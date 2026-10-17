package com.beykus.beygram.diagnostics.report

import com.beykus.beygram.domain.repository.ReportBotChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Причина неуспеха; локализованный текст выбирает UI-слой. */
enum class ReportFailureReason {
    TELEGRAM_UNAVAILABLE,
    BOT_UNAVAILABLE_OR_BLOCKED,
    NOT_ALLOWED,
    CONFIRMATION_TIMEOUT,
    CONFIRMATION_REJECTED,
    NONCE_EXPIRED,
    RATE_LIMITED,
    INVALID_PAYLOAD,
    CONFLICT,
    API_UNAVAILABLE,
    PAYLOAD_TOO_LARGE,
    FAILED_DELIVERY;

    /** Отказ, при котором повтор той же отправки бессмысленен: нужен новый отчёт. */
    val isTerminal: Boolean
        get() = this == INVALID_PAYLOAD || this == CONFLICT || this == PAYLOAD_TOO_LARGE || this == FAILED_DELIVERY
}

/**
 * Состояния потока отправки: draft → preparing → awaiting_telegram →
 * awaiting_bot_confirmation → uploading → queued → delivered; ошибки — [Failed].
 * `queued` ещё НЕ доставка: `delivered` ставится только по GET статуса.
 */
sealed interface ReportFlowState {

    /** Форма: редактирование допустимо, ничего не отправлено. */
    data class Draft(
        val description: String,
        val includeDiagnostics: Boolean,
        val descriptionError: Boolean,
        val eventCount: Int,
        val canSubmit: Boolean,
        val configReady: Boolean,
        val telegramReady: Boolean,
    ) : ReportFlowState

    data class Preparing(val includeDiagnostics: Boolean, val eventCount: Int) : ReportFlowState

    data class AwaitingTelegram(val botUsername: String) : ReportFlowState

    /** Ждём входящее подтверждение бота; можно прервать — POST ещё не начат. */
    data class AwaitingBotConfirmation(
        val botUsername: String,
        val elapsedSeconds: Long,
        val timeoutSeconds: Long
    ) : ReportFlowState

    data class Uploading(val reportId: String, val attempt: Int, val maxAttempts: Int) : ReportFlowState

    /** Ответ 202 означает «принято в очередь», не «доставлено». */
    data class Queued(
        val reportId: String,
        val attempts: Int,
        val acceptedAtSeconds: Double,
        val detail: String,
        val idempotentReplay: Boolean
    ) : ReportFlowState

    data class Delivered(val reportId: String, val attempts: Int) : ReportFlowState

    data class DeliveryFailed(val reportId: String, val attempts: Int, val detail: String) : ReportFlowState

    data class Failed(
        val reason: ReportFailureReason,
        val reportId: String?,
        val retryAfterSeconds: Int?,
        /** true — безопасен повтор тех же байтов; false — повтор требует нового подтверждения. */
        val canRetrySameReport: Boolean,
        val serverMessage: String? = null,
    ) : ReportFlowState
}

/**
 * Координатор отправки диагностического отчёта — единый активный поток на аккаунт.
 *
 * Гарантии:
 *  - один активный submit; повторный тап не создаёт второй nonce/POST;
 *  - report_id, nonce и байты payload фиксируются до первой попытки и не
 *    меняются при повторах (условие идемпотентности сервера);
 *  - превью «Просмотреть данные» показывает тот же снимок, что уйдёт в POST;
 *  - подтверждение бота обязательно до POST; чужие/старые ответы не пропускаются;
 *  - отмена корутины не выдаёт успех; состояние остаётся честным;
 *  - после смерти процесса ничего не отправляется само: черновик восстанавливается.
 *
 * Политика повторов:
 *  - временный сетевой сбой/5xx → те же байты, тот же nonce и report_id;
 *  - подтверждение не состояло (таймаут, отказ бота, 401) → новое подтверждение
 *    с НОВЫМ nonce при том же report_id: сервер ещё не связал nonce с отчётом,
 *    а дубль report_id на сервере исключён идемпотентной проверкой содержимого;
 *  - редактирование описания после неуспеха → полностью новый отчёт.
 */
class DiagnosticsReportCoordinator(
    private val scope: CoroutineScope,
    private val config: DiagnosticsApiConfig,
    private val botChannel: ReportBotChannel,
    private val apiClient: DiagnosticsReportTransport,
    private val draftStore: ReportDraftStore? = null,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
    private val eventsProvider: () -> List<DiagnosticEvent>,
    private val overflowedProvider: () -> Boolean,
    /** Всего событий с запуска процесса (включая вытесненные из буфера). */
    private val totalEventsProvider: () -> Long,
    private val metaProvider: suspend () -> ReportClientMeta,
    /** Авторизована ли TDLib-сессия; без неё сетевая отправка отключена. */
    private val telegramReadyProvider: () -> Boolean = { true },
) {

    private fun draftState(description: String? = null, include: Boolean? = null) = ReportFlowState.Draft(
        description = description ?: draftStore?.load().orEmpty(),
        includeDiagnostics = include ?: draftStore?.loadIncludeDiagnostics() ?: true,
        descriptionError = false,
        eventCount = 0,
        canSubmit = false,
        configReady = config.isConfigured,
        telegramReady = telegramReadyProvider()
    )

    private val _state = MutableStateFlow<ReportFlowState>(draftState())
    val state: StateFlow<ReportFlowState> = _state.asStateFlow()

    /** Зафиксированная отправка: те же payload/байты/report_id на всех повторах. */
    private class Submission(
        val payload: BugReportPayload,
        val bodyBytes: ByteArray,
        val reportId: String,
        var nonce: String,
    )

    @Volatile private var submission: Submission? = null
    @Volatile private var statusToken: String? = null
    private var activeJob: Job? = null

    // Последние введённые данные формы — для возврата к Draft после отмены.
    @Volatile private var lastDraftDescription: String = ""
    @Volatile private var lastDraftInclude: Boolean = true

    init {
        refreshDraftMeta()
    }

    private fun refreshDraftMeta() {
        val current = _state.value as? ReportFlowState.Draft ?: return
        lastDraftDescription = current.description
        lastDraftInclude = current.includeDiagnostics
        _state.value = current.copy(
            eventCount = eventsProvider().size,
            telegramReady = telegramReadyProvider(),
            canSubmit = config.isConfigured && telegramReadyProvider() &&
                current.description.isNotBlank() &&
                current.description.length <= BugReportPayload.MAX_DESCRIPTION_CHARS
        )
    }

    /** Вызывается при возврате на экран: обновить готовность и проверить статус. */
    fun onScreenResumed() {
        refreshDraftMeta()
        pollOnce()
    }

    fun onDescriptionChange(text: String) {
        val current = _state.value as? ReportFlowState.Draft ?: return
        // Изменение описания/состава делает зафиксированный payload недействительным:
        // следующая отправка станет новым отчётом с новым report_id и nonce.
        submission = null
        statusToken = null
        val limited = if (text.length > BugReportPayload.MAX_DESCRIPTION_CHARS) {
            text.take(BugReportPayload.MAX_DESCRIPTION_CHARS)
        } else {
            text
        }
        applyDraft(current.copy(description = limited, descriptionError = false))
    }

    fun onIncludeDiagnosticsChange(include: Boolean) {
        val current = _state.value as? ReportFlowState.Draft ?: return
        submission = null
        statusToken = null
        applyDraft(current.copy(includeDiagnostics = include))
    }

    private fun applyDraft(next: ReportFlowState.Draft) {
        val telegramReady = telegramReadyProvider()
        lastDraftDescription = next.description
        lastDraftInclude = next.includeDiagnostics
        _state.value = next.copy(
            eventCount = eventsProvider().size,
            telegramReady = telegramReady,
            canSubmit = config.isConfigured && telegramReady &&
                next.description.isNotBlank() &&
                next.description.length <= BugReportPayload.MAX_DESCRIPTION_CHARS
        )
        draftStore?.save(next.description, next.includeDiagnostics)
    }

    /**
     * Фиксирует снимок отчёта (если ещё не зафиксирован): description+summary+events+meta,
     * nonce и report_id. Используется и превью, и отправкой — поэтому превью
     * показывает ровно те байты, что уйдут в POST.
     */
    private suspend fun ensureSubmission(): Submission? {
        submission?.let { return it }
        val draft = _state.value as? ReportFlowState.Draft ?: return null
        val description = draft.description.trim()
        if (description.isEmpty() || description.length > BugReportPayload.MAX_DESCRIPTION_CHARS) {
            if (description.isEmpty()) _state.value = draft.copy(descriptionError = true)
            return null
        }
        val events = if (draft.includeDiagnostics) eventsProvider() else emptyList()
        val base = BugReportPayload(
            reportId = ReportNonce.newReportId(),
            description = description,
            summary = if (draft.includeDiagnostics) ReportSnapshot.buildSummary(events).ifEmpty { null } else null,
            clientMeta = null,
            events = events,
            createdAtSeconds = clock() / 1000.0
        )
        val fitted = ReportSnapshot.fitToBudget(base) ?: return null
        // Счётчики фиксируются вместе с payload: превью и POST видят одни и те же числа.
        val trimmed = fitted.events.size < events.size || fitted.summary != base.summary
        val meta = runCatching { metaProvider() }.getOrNull()?.let { client ->
            if (draft.includeDiagnostics) {
                client.copy(
                    eventCountTotal = totalEventsProvider().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                    bufferOverflowed = overflowedProvider() || trimmed
                )
            } else {
                client
            }
        }
        val payload = fitted.copy(clientMeta = meta)
        val bytes = ReportJson.encode(payload).toByteArray(Charsets.UTF_8)
        if (bytes.size > ReportSnapshot.MAX_BODY_BYTES) return null
        return Submission(payload, bytes, payload.reportId, ReportNonce.newNonce()).also { submission = it }
    }

    /** Полный текст данных отчёта для «Просмотреть данные» — ровно то, что уйдёт. */
    suspend fun previewText(): String? {
        val prepared = ensureSubmission() ?: return null
        return ReportJson.prettyPrint(ReportJson.encode(prepared.payload))
    }

    fun submit() {
        val current = _state.value
        if (current !is ReportFlowState.Draft) return // один активный submit
        if (!current.canSubmit) return
        if (activeJob?.isActive == true) return // повторный тап не создаёт второй nonce/POST
        // Снимок не сбрасываем: если пользователь открывал превью, уйдут именно его байты.
        // Сброс выполняется при правке описания/состава или через startNewReport().
        statusToken = null
        activeJob = scope.launch { runFullFlow() }
    }

    /**
     * Повтор после неуспеха. Те же байты/nonce — при временном сбое; новое
     * подтверждение с новым nonce (тот же report_id) — когда подтверждение
     * не состояло. Отправка начинается только явным действием пользователя.
     */
    fun retry() {
        val current = _state.value as? ReportFlowState.Failed ?: return
        if (activeJob?.isActive == true) return
        // Для финальных отказов сервера повтор бессмысленен или опасен.
        if (current.reason.isTerminal) return
        val prepared = submission ?: return
        activeJob = scope.launch {
            if (current.canRetrySameReport) {
                uploadWithRetries(prepared, prepared.nonce)
            } else {
                runFullFlow()
            }
        }
    }

    /** Прерывает операцию до отправки POST. После приёма сервером отзыв невозможен. */
    fun cancel() {
        when (_state.value) {
            is ReportFlowState.Preparing,
            is ReportFlowState.AwaitingTelegram,
            is ReportFlowState.AwaitingBotConfirmation -> {
                activeJob?.cancel()
                activeJob = null
                botChannel.cancelBotWait()
                // Честное состояние: ничего не отправлено, форма снова доступна.
                submission = null
                statusToken = null
                returnToDraft()
            }
            else -> Unit
        }
    }

    /** Ручное обновление статуса из состояния Queued или при возвращении на экран. */
    fun pollOnce() {
        val token = statusToken ?: return
        val prepared = submission ?: return
        if (_state.value !is ReportFlowState.Queued) return
        if (activeJob?.isActive == true) return
        activeJob = scope.launch { checkDeliveryStatus(prepared, token) }
    }

    /** Возврат к форме после неуспеха или доставки: следующий submit создаст новый отчёт. */
    fun startNewReport() {
        when (_state.value) {
            is ReportFlowState.Failed, is ReportFlowState.DeliveryFailed, is ReportFlowState.Delivered -> {
                submission = null
                statusToken = null
                activeJob = null
                returnToDraft()
            }
            else -> Unit
        }
    }

    private fun returnToDraft() {
        _state.value = ReportFlowState.Draft(
            description = lastDraftDescription,
            includeDiagnostics = lastDraftInclude,
            descriptionError = false,
            eventCount = eventsProvider().size,
            canSubmit = config.isConfigured && telegramReadyProvider() &&
                lastDraftDescription.isNotBlank() &&
                lastDraftDescription.length <= BugReportPayload.MAX_DESCRIPTION_CHARS,
            configReady = config.isConfigured,
            telegramReady = telegramReadyProvider()
        )
    }

    fun dispose() {
        activeJob?.cancel()
        activeJob = null
        botChannel.cancelBotWait()
    }

    // ---- Основной поток --------------------------------------------------------

    private suspend fun runFullFlow() {
        try {
            val prepared = ensureSubmission() ?: run {
                _state.value = ReportFlowState.Failed(
                    ReportFailureReason.PAYLOAD_TOO_LARGE, null, null, canRetrySameReport = false
                )
                return
            }
            val draft = _state.value as? ReportFlowState.Draft
            _state.value = ReportFlowState.Preparing(
                includeDiagnostics = draft?.includeDiagnostics ?: true,
                eventCount = prepared.payload.events.size
            )
            if (!confirmThroughBot(prepared)) return
            uploadWithRetries(prepared, prepared.nonce)
        } catch (cancelled: CancellationException) {
            // Отмена — не успех: состояние остаётся «не отправлено».
            throw cancelled
        } catch (unexpected: Exception) {
            _state.value = ReportFlowState.Failed(
                ReportFailureReason.API_UNAVAILABLE, submission?.reportId, null, canRetrySameReport = true
            )
        }
    }

    /** Возвращает true, когда подтверждение получено; иначе ставит [ReportFlowState.Failed]. */
    private suspend fun confirmThroughBot(prepared: Submission): Boolean {
        // Каждое подтверждение — с новым nonce: сервер выдаёт ISSUED на один раз.
        prepared.nonce = ReportNonce.newNonce()
        _state.value = ReportFlowState.AwaitingTelegram(config.botUsername)
        val chatId = botChannel.openBotChat(config.botUserId, config.botUsername).getOrElse { failure ->
            _state.value = ReportFlowState.Failed(botReason(failure), prepared.reportId, null, canRetrySameReport = false)
            return false
        }
        if (botChannel.sendReportCommand(chatId, prepared.nonce).isFailure) {
            _state.value = ReportFlowState.Failed(
                ReportFailureReason.TELEGRAM_UNAVAILABLE, prepared.reportId, null, canRetrySameReport = false
            )
            return false
        }
        // Фаза ожидания с видимым таймером; прервать можно до начала POST.
        val startedAt = clock()
        _state.value = ReportFlowState.AwaitingBotConfirmation(
            config.botUsername, 0, CONFIRMATION_WAIT_SECONDS
        )
        val waitTicker = scope.launch {
            while (clock() - startedAt < CONFIRMATION_WAIT_SECONDS * 1000) {
                delayFn(1000)
                val current = _state.value
                if (current is ReportFlowState.AwaitingBotConfirmation) {
                    _state.value = current.copy(elapsedSeconds = (clock() - startedAt) / 1000)
                } else {
                    return@launch
                }
            }
        }
        val confirmation = botChannel.awaitBotConfirmation(chatId, CONFIRMATION_WAIT_SECONDS * 1000)
        waitTicker.cancel()
        confirmation.getOrElse { failure ->
            val reason = when ((failure as? ReportBotException)?.failure) {
                ReportBotFailure.WAIT_TIMEOUT -> ReportFailureReason.CONFIRMATION_TIMEOUT
                ReportBotFailure.REFUSED_NOT_ALLOWED -> ReportFailureReason.NOT_ALLOWED
                ReportBotFailure.REFUSED_RATE_LIMITED -> ReportFailureReason.RATE_LIMITED
                ReportBotFailure.REFUSED_DISABLED -> ReportFailureReason.API_UNAVAILABLE
                ReportBotFailure.REFUSED_BAD_NONCE -> ReportFailureReason.NONCE_EXPIRED
                ReportBotFailure.REFUSED_PROTOCOL -> ReportFailureReason.CONFIRMATION_REJECTED
                else -> ReportFailureReason.TELEGRAM_UNAVAILABLE
            }
            _state.value = ReportFlowState.Failed(reason, prepared.reportId, null, canRetrySameReport = false)
            return false
        }
        return true
    }

    private suspend fun uploadWithRetries(prepared: Submission, nonce: String) {
        var attempt = 0
        var backoffMillis = UPLOAD_RETRY_BACKOFF_BASE_MS
        while (true) {
            attempt++
            _state.value = ReportFlowState.Uploading(prepared.reportId, attempt, UPLOAD_MAX_ATTEMPTS)
            val outcome = withContext(ioDispatcher) {
                apiClient.postReport(config, prepared.bodyBytes, nonce)
            }
            when (outcome) {
                is ReportApiOutcome.Ok -> {
                    statusToken = outcome.value.statusToken
                    when (outcome.value.status) {
                        STATUS_DELIVERED -> {
                            draftStore?.clear()
                            _state.value = ReportFlowState.Delivered(outcome.value.reportId, attempt)
                        }
                        STATUS_FAILED -> _state.value = ReportFlowState.DeliveryFailed(
                            outcome.value.reportId, 0, outcome.value.detail
                        )
                        else -> {
                            _state.value = ReportFlowState.Queued(
                                reportId = outcome.value.reportId,
                                attempts = 0,
                                acceptedAtSeconds = outcome.value.acceptedAtSeconds,
                                detail = outcome.value.detail,
                                idempotentReplay = outcome.value.idempotentReplay
                            )
                            startPolling(prepared)
                        }
                    }
                    return
                }
                is ReportApiOutcome.HttpError -> {
                    val retryable = outcome.isTransient
                    if (retryable && attempt < UPLOAD_MAX_ATTEMPTS) {
                        delayFn(waitForRetry(outcome, backoffMillis))
                        backoffMillis = (backoffMillis * 2).coerceAtMost(UPLOAD_RETRY_BACKOFF_MAX_MS)
                        continue
                    }
                    _state.value = ReportFlowState.Failed(
                        reason = mapHttpError(outcome),
                        reportId = prepared.reportId,
                        retryAfterSeconds = outcome.retryAfterSeconds,
                        canRetrySameReport = retryable || outcome.httpStatus == 429,
                        serverMessage = outcome.serverMessage
                    )
                    return
                }
                is ReportApiOutcome.Network -> {
                    if (outcome.isTransient && attempt < UPLOAD_MAX_ATTEMPTS) {
                        delayFn(backoffMillis)
                        backoffMillis = (backoffMillis * 2).coerceAtMost(UPLOAD_RETRY_BACKOFF_MAX_MS)
                        continue
                    }
                    _state.value = ReportFlowState.Failed(
                        ReportFailureReason.API_UNAVAILABLE, prepared.reportId, null,
                        canRetrySameReport = outcome.isTransient
                    )
                    return
                }
            }
        }
    }

    private fun waitForRetry(outcome: ReportApiOutcome.HttpError, fallbackMillis: Long): Long =
        outcome.retryAfterSeconds?.takeIf { it > 0 }?.let { it * 1000L } ?: fallbackMillis

    private fun mapHttpError(outcome: ReportApiOutcome.HttpError): ReportFailureReason = when (outcome.httpStatus) {
        401 -> ReportFailureReason.NONCE_EXPIRED
        403 -> ReportFailureReason.NOT_ALLOWED
        409 -> ReportFailureReason.CONFLICT
        413, 422 -> ReportFailureReason.INVALID_PAYLOAD
        429 -> ReportFailureReason.RATE_LIMITED
        else -> ReportFailureReason.API_UNAVAILABLE
    }

    // ---- Доставка --------------------------------------------------------------

    private fun startPolling(prepared: Submission) {
        val token = statusToken ?: return
        activeJob = scope.launch {
            for (intervalSeconds in POLL_INTERVALS_SECONDS) {
                delayFn(intervalSeconds * 1000L)
                if (!checkDeliveryStatus(prepared, token)) return@launch
            }
            // Дальше статус проверяется вручную или при возвращении на экран:
            // бессрочный фоновый опрос не ведётся (сервер может держать DROPPED как queued).
        }
    }

    /** false — статус финален или опрос прекращён. */
    private suspend fun checkDeliveryStatus(prepared: Submission, token: String): Boolean {
        val outcome = withContext(ioDispatcher) {
            apiClient.getStatus(config, prepared.reportId, token)
        }
        when (outcome) {
            is ReportApiOutcome.Ok -> when (outcome.value.status) {
                STATUS_DELIVERED -> {
                    draftStore?.clear()
                    _state.value = ReportFlowState.Delivered(outcome.value.reportId, outcome.value.attempts)
                    return false
                }
                STATUS_FAILED -> {
                    _state.value = ReportFlowState.DeliveryFailed(
                        outcome.value.reportId, outcome.value.attempts, outcome.value.detail
                    )
                    return false
                }
                else -> {
                    val current = _state.value
                    if (current is ReportFlowState.Queued) {
                        _state.value = current.copy(attempts = outcome.value.attempts, detail = outcome.value.detail)
                    }
                    return true
                }
            }
            is ReportApiOutcome.HttpError -> {
                // Токен статуса подписан сервером и не должен отклоняться; на сбой
                // опрос прекращается, состояние остаётся Queued с ручным обновлением.
                return false
            }
            is ReportApiOutcome.Network -> return outcome.isTransient
        }
    }

    private fun botReason(failure: Throwable): ReportFailureReason = when ((failure as? ReportBotException)?.failure) {
        ReportBotFailure.BOT_UNRESOLVED,
        ReportBotFailure.CONFIG_MISMATCH,
        ReportBotFailure.NOT_A_BOT -> ReportFailureReason.BOT_UNAVAILABLE_OR_BLOCKED
        else -> ReportFailureReason.TELEGRAM_UNAVAILABLE
    }

    companion object {
        const val CONFIRMATION_WAIT_SECONDS = 120L
        const val UPLOAD_MAX_ATTEMPTS = 3
        const val UPLOAD_RETRY_BACKOFF_BASE_MS = 2_000L
        const val UPLOAD_RETRY_BACKOFF_MAX_MS = 8_000L

        /** Умеренный опрос статуса; после последовательности — только вручную. */
        val POLL_INTERVALS_SECONDS = intArrayOf(5, 10, 20, 30)

        private const val STATUS_DELIVERED = "delivered"
        private const val STATUS_FAILED = "failed"
    }
}
