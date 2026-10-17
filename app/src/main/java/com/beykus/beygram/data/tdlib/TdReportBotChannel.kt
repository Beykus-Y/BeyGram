package com.beykus.beygram.data.tdlib

import com.beykus.beygram.diagnostics.report.BotConfirmation
import com.beykus.beygram.diagnostics.report.BotReply
import com.beykus.beygram.diagnostics.report.BotWaitOutcome
import com.beykus.beygram.diagnostics.report.ReportBotException
import com.beykus.beygram.diagnostics.report.ReportBotFailure
import com.beykus.beygram.diagnostics.report.BotReplyCorrelator
import com.beykus.beygram.domain.repository.ReportBotChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * TDLib-реализация подтверждения через служебного бота.
 *
 * Обновления TDLib приходят из [TdLibTelegramRepository.handleUpdate] — канал
 * только «подсматривает» релевантные события и никогда не фильтрует обычные
 * сообщения чатов. Подтверждением считает исключительно результат
 * [BotReplyCorrelator]: sender/chat/reply-chain/формат.
 *
 * Каждая команда `/report` открывает свежую сессию корреляции: подтверждение
 * из предыдущей попытки не может подтвердить новый nonce.
 */
internal class TdReportBotChannel(
    private val client: () -> Client,
    private val pinnedBotUserId: Long
) : ReportBotChannel {

    private val request = TdRequests(client)

    private class Session(
        val chatId: Long,
        val correlator: BotReplyCorrelator,
        val wait: CompletableDeferred<BotWaitOutcome>
    )

    @Volatile private var session: Session? = null

    /** Как [requestResult], но сохраняет [ReportBotException] нетронутой для координатора. */
    private suspend fun <T> botResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (timeout: TimeoutCancellationException) {
        Result.failure(ReportBotException(ReportBotFailure.SEND_FAILED, cause = timeout))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: ReportBotException) {
        Result.failure(failure)
    } catch (error: Exception) {
        Result.failure(ReportBotException(ReportBotFailure.SEND_FAILED, error.message.orEmpty(), error))
    }

    override suspend fun openBotChat(expectedBotUserId: Long, username: String): Result<Long> =
        botResult {
            val cleaned = username.trim().trimStart('@')
            val chat = request(TdApi.SearchPublicChat(cleaned))
            val type = chat.type as? TdApi.ChatTypePrivate
                ?: throw ReportBotException(ReportBotFailure.BOT_UNRESOLVED, "chat is not private")
            if (expectedBotUserId > 0 && type.userId != expectedBotUserId) {
                throw ReportBotException(
                    ReportBotFailure.CONFIG_MISMATCH,
                    "resolved ${type.userId} != configured $expectedBotUserId"
                )
            }
            val user = runCatching { request(TdApi.GetUser(type.userId)) }.getOrNull()
            if (user != null) {
                if (user.type !is TdApi.UserTypeBot) {
                    throw ReportBotException(ReportBotFailure.NOT_A_BOT, "resolved user is not a bot")
                }
                val usernames = buildList {
                    user.usernames?.activeUsernames?.let(::addAll)
                    user.usernames?.editableUsername?.let(::add)
                }
                if (usernames.none { it.equals(cleaned, ignoreCase = true) }) {
                    throw ReportBotException(ReportBotFailure.CONFIG_MISMATCH, "username mismatch")
                }
            }
            chat.id
        }

    override suspend fun sendReportCommand(botChatId: Long, nonce: String): Result<Unit> =
        botResult {
            // Новый nonce — новая сессия корреляции: старые ответы бота не считаются.
            // Сессия создаётся до отправки: обновление об отправке может прийти раньше, чем вернётся SendMessage.
            val started = Session(
                botChatId,
                BotReplyCorrelator(pinnedBotUserId, botChatId),
                CompletableDeferred()
            )
            session = started
            try {
                val content = TdApi.InputMessageText().apply {
                    text = TdApi.FormattedText("/report $nonce", arrayOf())
                }
                val sent = request(TdApi.SendMessage().apply {
                    chatId = botChatId
                    inputMessageContent = content
                })
                started.correlator.trackSent(sent.id)
            } catch (failure: Throwable) {
                closeSession(started)
                throw failure
            }
            Unit
        }

    override suspend fun awaitBotConfirmation(botChatId: Long, timeoutMillis: Long): Result<BotConfirmation> {
        val active = session?.takeIf { it.chatId == botChatId }
        if (active == null || active.wait.isCompleted) {
            return Result.failure(ReportBotException(ReportBotFailure.SEND_FAILED, "no active confirmation session"))
        }
        try {
            when (val outcome = withTimeout(timeoutMillis) { active.wait.await() }) {
                is BotWaitOutcome.Confirmed ->
                    return Result.success(BotConfirmation(outcome.correlationId, outcome.botReplyMessageId))
                is BotWaitOutcome.Refused ->
                    return Result.failure(ReportBotException(outcome.reply.toFailure(), "bot refusal"))
            }
        } catch (timeout: TimeoutCancellationException) {
            return Result.failure(ReportBotException(ReportBotFailure.WAIT_TIMEOUT, cause = timeout))
        } catch (cancelled: CancellationException) {
            // Отмена корутины — ни успех, ни таймаут: пробрасываем дальше.
            throw cancelled
        } catch (failure: ReportBotException) {
            return Result.failure(failure)
        } finally {
            closeSession(active)
        }
    }

    override fun cancelBotWait() {
        val active = session ?: return
        closeSession(active)
    }

    // ---- Точки входа из потока обновлений TDLib -------------------------------

    fun onNewMessage(message: TdApi.Message) {
        val active = session ?: return
        if (message.chatId != active.chatId) return
        when (val verdict = active.correlator.onIncoming(message)) {
            is BotReplyCorrelator.Verdict.Confirmed ->
                active.wait.complete(BotWaitOutcome.Confirmed(verdict.correlationId, verdict.botReplyMessageId))
            is BotReplyCorrelator.Verdict.Refusal ->
                active.wait.complete(BotWaitOutcome.Refused(verdict.reply))
            null -> Unit
        }
    }

    fun onSendSucceeded(update: TdApi.UpdateMessageSendSucceeded) {
        val active = session ?: return
        if (update.message.chatId != active.chatId) return
        active.correlator.onSendSucceeded(update.oldMessageId, update.message.id)
    }

    fun onSendFailed(update: TdApi.UpdateMessageSendFailed) {
        val active = session ?: return
        if (update.message.chatId != active.chatId) return
        // Ожидание завершаем только за нашу команду, а не за любую неудачную отправку в этом чате.
        if (!active.correlator.owns(update.oldMessageId)) return
        active.correlator.onSendFailed(update.oldMessageId)
        active.wait.completeExceptionally(
            ReportBotException(ReportBotFailure.SEND_FAILED_PERMANENT, "code=${update.error.code}")
        )
    }

    private fun BotReply.toFailure(): ReportBotFailure = when (this) {
        BotReply.NotAllowed -> ReportBotFailure.REFUSED_NOT_ALLOWED
        BotReply.RateLimited -> ReportBotFailure.REFUSED_RATE_LIMITED
        BotReply.Disabled -> ReportBotFailure.REFUSED_DISABLED
        BotReply.BadNonce -> ReportBotFailure.REFUSED_BAD_NONCE
        BotReply.NeedNonce -> ReportBotFailure.REFUSED_PROTOCOL
        is BotReply.Confirmed -> ReportBotFailure.REFUSED_PROTOCOL
        BotReply.Unrelated -> ReportBotFailure.REFUSED_PROTOCOL
    }

    private fun closeSession(active: Session) {
        if (session === active) session = null
        // Повторное завершение уже завершённого deferred — без действия.
        active.wait.completeExceptionally(ReportBotException(ReportBotFailure.WAIT_TIMEOUT, "session closed"))
    }
}
