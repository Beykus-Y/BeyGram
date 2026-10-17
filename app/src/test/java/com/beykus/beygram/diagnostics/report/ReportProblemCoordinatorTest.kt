package com.beykus.beygram.diagnostics.report

import com.beykus.beygram.domain.repository.ReportBotChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalCoroutinesApi::class)
class ReportProblemCoordinatorTest {

    private companion object {
        val botUserId = 8841599020L
        val reportId = "123e4567-e89b-42d3-a456-426614174000"
    }

    /** Управляемый фейк канала бота. */
    private class FakeBotChannel : ReportBotChannel {
        var openResult: Result<Long> = Result.success(botUserId)
        var sendResult: Result<Unit> = Result.success(Unit)
        var waitResult: Result<BotConfirmation> = Result.success(BotConfirmation("0123456789abcdef", 555L))
        var suspendWait: CompletableDeferred<Unit>? = null

        val sentNonces = mutableListOf<String>()
        var waits = 0
        var cancelled = 0

        override suspend fun openBotChat(expectedBotUserId: Long, username: String): Result<Long> {
            if (expectedBotUserId != botUserId) return Result.failure(IllegalStateException("wrong pinned id"))
            return openResult
        }

        override suspend fun sendReportCommand(botChatId: Long, nonce: String): Result<Unit> {
            sentNonces.add(nonce)
            return sendResult
        }

        override suspend fun awaitBotConfirmation(botChatId: Long, timeoutMillis: Long): Result<BotConfirmation> {
            waits++
            suspendWait?.let { deferred ->
                deferred.await()
                return waitResult
            }
            return waitResult
        }

        override fun cancelBotWait() {
            cancelled++
            suspendWait?.cancel()
        }
    }

    /** Управляемый фейк транспорта API со сценарием ответов. */
    private class FakeTransport : DiagnosticsReportTransport {
        val postResults = ArrayDeque<ReportApiOutcome<AcceptedReport>>()
        val statusResults = ArrayDeque<ReportApiOutcome<ReportStatusSnapshot>>()
        val posts = mutableListOf<Pair<String, ByteArray>>() // nonce to body
        val statusTokens = mutableListOf<String>()

        override fun postReport(config: DiagnosticsApiConfig, bodyBytes: ByteArray, nonce: String): ReportApiOutcome<AcceptedReport> {
            posts.add(nonce to bodyBytes)
            return postResults.removeFirstOrNull()
                ?: ReportApiOutcome.Ok(AcceptedReport(reportId, "queued", 1.0, "token", false, ""))
        }

        override fun getStatus(config: DiagnosticsApiConfig, reportId: String, statusToken: String): ReportApiOutcome<ReportStatusSnapshot> {
            statusTokens.add(statusToken)
            return statusResults.removeFirstOrNull()
                ?: ReportApiOutcome.Ok(
                    // Без сценария сервер держит отчёт в очереди: delivered приходит только явно.
                    ReportStatusSnapshot(reportId, "queued", 1.0, 1.0, null, 0, "в очереди")
                )
        }

        fun bodiesEqual(): Boolean =
            posts.size >= 2 && posts[0].second.contentEquals(posts[1].second)

        fun bodyText(post: Int = posts.lastIndex): String =
            String(posts[post].second, StandardCharsets.UTF_8)
    }

    private fun accepted(status: String = "queued") = ReportApiOutcome.Ok(
        AcceptedReport(reportId, status, 1.0, "token-1", idempotentReplay = false, detail = "")
    )

    private fun TestScope.newCoordinator(bot: FakeBotChannel, api: FakeTransport): DiagnosticsReportCoordinator =
        DiagnosticsReportCoordinator(
            scope = backgroundScope,
            config = DiagnosticsApiConfig("https://reports.example.com", "BeyGram_Bot", botUserId),
            botChannel = bot,
            apiClient = api,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            eventsProvider = { emptyList() },
            overflowedProvider = { false },
            metaProvider = { ReportClientMeta(appVersion = "1.0-test", platform = "android") }
        )

    private fun DiagnosticsReportCoordinator.fillDescription(text: String = "не работает соединение") {
        onDescriptionChange(text)
    }

    private fun httpError(status: Int, code: String, retryAfter: Int? = null) =
        ReportApiOutcome.HttpError(status, code, "сервер", retryAfter)

    /** Продвигает виртуальное время потока: таймеры подтверждения и опрос статуса. */
    private fun TestScope.pump() {
        testScheduler.advanceTimeBy(600_000)
        testScheduler.runCurrent()
    }

    @Test
    fun `nothing is sent without an explicit submit`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport()
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription()
        pump()
        assertTrue(coordinator.state.value is ReportFlowState.Draft)
        assertEquals(0, bot.sentNonces.size)
        assertEquals(0, api.posts.size)
    }

    @Test
    fun `happy path reaches delivered only through the status get`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport().apply {
            postResults.add(accepted("queued"))
            statusResults.add(
                ReportApiOutcome.Ok(ReportStatusSnapshot(reportId, "queued", 1.0, 1.0, null, 0, "в очереди"))
            )
            statusResults.add(
                ReportApiOutcome.Ok(ReportStatusSnapshot(reportId, "delivered", 1.0, 2.0, 3.0, 1, "доставлено"))
            )
        }
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription()
        coordinator.submit()
        pump()
        val finalState = coordinator.state.value
        assertTrue("ожидали Delivered, было ${finalState::class.simpleName}", finalState is ReportFlowState.Delivered)
        assertEquals(reportId, (finalState as ReportFlowState.Delivered).reportId)
        // Подтверждение: одна команда боту, один POST.
        assertEquals(1, bot.sentNonces.size)
        assertEquals(1, api.posts.size)
        assertEquals(bot.sentNonces.single(), api.posts.single().first)
        // Тело POST без nonce и telegram_user_id.
        val body = api.bodyText()
        assertFalse(body.contains("nonce"))
        assertFalse(body.contains("telegram_user_id"))
        // Статус запрошен с токеном из 202: два опроса (queued → delivered).
        assertEquals(listOf("token-1", "token-1"), api.statusTokens)
    }

    @Test
    fun `double submit does not create a second nonce or post`() = runTest {
        val bot = FakeBotChannel().apply { suspendWait = CompletableDeferred() }
        val api = FakeTransport().apply { postResults.add(accepted()) }
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription()
        coordinator.submit()
        coordinator.submit()
        coordinator.submit()
        pump()
        // Поток завис на ожидании подтверждения: команда одна, POST ещё не начинался.
        assertTrue(coordinator.state.value is ReportFlowState.AwaitingBotConfirmation)
        assertEquals(1, bot.sentNonces.size)
        assertEquals(0, api.posts.size)
    }

    @Test
    fun `not allowed refusal fails without a post and retry re-confirms with a new nonce`() = runTest {
        val bot = FakeBotChannel().apply {
            waitResult = Result.failure(
                com.beykus.beygram.diagnostics.report.ReportBotException(
                    com.beykus.beygram.diagnostics.report.ReportBotFailure.REFUSED_NOT_ALLOWED
                )
            )
        }
        val api = FakeTransport().apply { postResults.add(accepted()) }
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription()
        coordinator.submit()
        pump()
        val failed = coordinator.state.value as ReportFlowState.Failed
        assertEquals(ReportFailureReason.NOT_ALLOWED, failed.reason)
        assertEquals(0, api.posts.size)

        bot.waitResult = Result.success(BotConfirmation("fedcba9876543210", 556L))
        coordinator.retry()
        pump()
        assertTrue(coordinator.state.value is ReportFlowState.Queued)
        // Подтверждение выполнено дважды с разными nonce.
        assertEquals(2, bot.sentNonces.size)
        assertNotEquals(bot.sentNonces[0], bot.sentNonces[1])
        assertEquals(1, api.posts.size)
    }

    @Test
    fun `confirmation timeout retries with a new nonce and the same report id`() = runTest {
        val bot = FakeBotChannel().apply {
            waitResult = Result.failure(
                ReportBotException(ReportBotFailure.WAIT_TIMEOUT)
            )
        }
        val api = FakeTransport().apply { postResults.add(accepted()) }
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription()
        coordinator.submit()
        pump()
        assertEquals(ReportFailureReason.CONFIRMATION_TIMEOUT, (coordinator.state.value as ReportFlowState.Failed).reason)

        bot.waitResult = Result.success(BotConfirmation("0123456789abcdef", 1L))
        coordinator.retry()
        pump()
        assertTrue(coordinator.state.value is ReportFlowState.Queued)
        assertNotEquals(bot.sentNonces[0], bot.sentNonces[1])
        // POST-а до подтверждения не было: report_id не виден в API — проверяем отсутствие второго POST-а.
        assertEquals(1, api.posts.size)
    }

    @Test
    fun `transient 503 retries the same bytes with the same nonce`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport().apply {
            postResults.add(httpError(503, "temporarily_unavailable"))
            postResults.add(accepted())
        }
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription()
        coordinator.submit()
        pump()
        assertTrue(coordinator.state.value is ReportFlowState.Queued)
        assertEquals(2, api.posts.size)
        assertEquals(api.posts[0].first, api.posts[1].first)
        assertTrue(api.bodiesEqual())
    }

    @Test
    fun `422 is not retried and requires a new report`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport().apply { postResults.add(httpError(422, "payload_invalid")) }
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription()
        coordinator.submit()
        pump()
        val failed = coordinator.state.value as ReportFlowState.Failed
        assertEquals(ReportFailureReason.INVALID_PAYLOAD, failed.reason)
        assertFalse(failed.canRetrySameReport)
        assertEquals(1, api.posts.size)
        coordinator.retry()
        pump()
        assertEquals("повтор для 422 запрещён", 1, api.posts.size)
    }

    @Test
    fun `401 after confirmation leads to nonce expired failure`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport().apply { postResults.add(httpError(401, "invalid_token")) }
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription()
        coordinator.submit()
        pump()
        assertEquals(ReportFailureReason.NONCE_EXPIRED, (coordinator.state.value as ReportFlowState.Failed).reason)
    }

    @Test
    fun `429 surfaces retry-after to the ui`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport().apply { postResults.add(httpError(429, "rate_limited", retryAfter = 77)) }
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription()
        coordinator.submit()
        pump()
        val failed = coordinator.state.value as ReportFlowState.Failed
        assertEquals(ReportFailureReason.RATE_LIMITED, failed.reason)
        assertEquals(77, failed.retryAfterSeconds)
        assertTrue(failed.canRetrySameReport)
    }

    @Test
    fun `cancel during bot wait returns to draft without posting`() = runTest {
        val bot = FakeBotChannel().apply { suspendWait = CompletableDeferred() }
        val api = FakeTransport()
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription()
        coordinator.submit()
        pump()
        assertEquals(1, bot.sentNonces.size)
        coordinator.cancel()
        pump()
        assertTrue(coordinator.state.value is ReportFlowState.Draft)
        assertEquals(0, api.posts.size)
    }

    @Test
    fun `editing description after failure starts a new report`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport().apply { postResults.add(httpError(409, "report_conflict")) }
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription("первая проблема")
        coordinator.submit()
        pump()
        assertEquals(ReportFailureReason.CONFLICT, (coordinator.state.value as ReportFlowState.Failed).reason)

        // Пользователь возвращается к форме («Составить новый отчёт»), правит описание и отправляет заново.
        coordinator.startNewReport()
        assertTrue(coordinator.state.value is ReportFlowState.Draft)
        coordinator.onDescriptionChange("вторая проблема")
        api.postResults.add(accepted())
        coordinator.submit()
        pump()
        assertTrue(coordinator.state.value is ReportFlowState.Queued)
        // Два разных report_id: конфликт не повторяется.
        val firstBody = api.bodyText(0)
        val secondBody = api.bodyText(1)
        val firstId = Regex(""""report_id":"([^"]+)"""").find(firstBody)!!.groupValues[1]
        val secondId = Regex(""""report_id":"([^"]+)"""").find(secondBody)!!.groupValues[1]
        assertNotEquals(firstId, secondId)
    }

    @Test
    fun `empty description is blocked and flagged`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport()
        val coordinator = newCoordinator(bot, api)
        coordinator.onDescriptionChange("   ")
        val draft = coordinator.state.value as ReportFlowState.Draft
        assertFalse(draft.canSubmit)
        coordinator.submit()
        pump()
        assertEquals(0, api.posts.size)
    }

    @Test
    fun `description longer than the limit is truncated`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport()
        val coordinator = newCoordinator(bot, api)
        coordinator.onDescriptionChange("а".repeat(2500))
        val draft = coordinator.state.value as ReportFlowState.Draft
        assertEquals(BugReportPayload.MAX_DESCRIPTION_CHARS, draft.description.length)
    }

    @Test
    fun `disabling diagnostics removes events and summary from the payload`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport().apply { postResults.add(accepted()) }
        val coordinator = newCoordinator(
            bot, api
        )
        coordinator.fillDescription()
        coordinator.onIncludeDiagnosticsChange(false)
        coordinator.submit()
        pump()
        val body = api.bodyText()
        assertFalse(body.contains("\"events\""))
        assertFalse(body.contains("\"summary\""))
    }

    @Test
    fun `queued state does not become delivered without a status get`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport().apply {
            postResults.add(accepted("queued"))
            // Статус всегда отвечает "queued" — вечная очередь.
            statusResults.addLast(
                ReportApiOutcome.Ok(ReportStatusSnapshot(reportId, "queued", 1.0, 1.0, null, 0, "в очереди"))
            )
        }
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription()
        coordinator.submit()
        pump()
        val state = coordinator.state.value
        assertTrue(state is ReportFlowState.Queued)
        // Опрос ограничен интервалами 5,10,20,30 с — четыре запроса, потом стоп.
        assertEquals(4, api.statusTokens.size)
        // Ручное обновление продолжает проверку.
        api.statusResults.addLast(
            ReportApiOutcome.Ok(ReportStatusSnapshot(reportId, "delivered", 1.0, 2.0, 3.0, 2, ""))
        )
        coordinator.pollOnce()
        pump()
        assertTrue(coordinator.state.value is ReportFlowState.Delivered)
    }

    @Test
    fun `failed delivery status becomes delivery failed`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport().apply {
            postResults.add(accepted("queued"))
            statusResults.addLast(
                ReportApiOutcome.Ok(ReportStatusSnapshot(reportId, "failed", 1.0, 2.0, null, 6, "не доставлено"))
            )
        }
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription()
        coordinator.submit()
        pump()
        val state = coordinator.state.value
        assertTrue(state is ReportFlowState.DeliveryFailed)
        assertEquals(6, (state as ReportFlowState.DeliveryFailed).attempts)
    }

    @Test
    fun `unconfirmed payload snapshot equals preview bytes`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport().apply { postResults.add(accepted()) }
        val coordinator = newCoordinator(bot, api)
        coordinator.fillDescription("превью-проверка")
        val preview = coordinator.previewText()
        coordinator.submit()
        pump()
        assertNotNull(preview)
        // Превью и отправленные байты содержат одно и то же описание.
        assertTrue(api.bodyText().contains("превью-проверка"))
        assertTrue(preview!!.contains("превью-проверка"))
    }

    @Test
    fun `unconfigured endpoint blocks submit but not the draft`() = runTest {
        val bot = FakeBotChannel()
        val api = FakeTransport()
        val coordinator = DiagnosticsReportCoordinator(
            scope = backgroundScope,
            config = DiagnosticsApiConfig("", "BeyGram_Bot", botUserId),
            botChannel = bot,
            apiClient = api,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            eventsProvider = { emptyList() },
            overflowedProvider = { false },
            metaProvider = { ReportClientMeta() }
        )
        coordinator.fillDescription()
        val draft = coordinator.state.value as ReportFlowState.Draft
        assertFalse(draft.configReady)
        assertFalse(draft.canSubmit)
        coordinator.submit()
        pump()
        assertEquals(0, api.posts.size)
    }
}
