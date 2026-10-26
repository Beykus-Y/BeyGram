package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.PaidReactionUncertainException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sends Stars reactions. One send is three TDLib steps: the pending reaction is added and committed at once, so no reaction waits
 * to be charged later, and a failed step removes the pending reaction again. One send runs at a time, and a send is never retried,
 * because a repeated commit could spend the Stars twice.
 */
internal class TdPaidReactions(private val request: suspend (TdApi.Function<TdApi.Ok>) -> TdApi.Ok) {
    private val sendInFlight = AtomicBoolean(false)

    /** Sends [starCount] Stars to one message. The caller has already confirmed the amount with the user. */
    suspend fun send(chatId: Long, messageId: Long, starCount: Long, isAnonymous: Boolean): Result<Unit> {
        if (starCount <= 0L) return Result.failure(UserMessageException(UiText.Res(R.string.err_paid_reaction_amount)))
        if (!sendInFlight.compareAndSet(false, true)) {
            return Result.failure(UserMessageException(UiText.Res(R.string.err_paid_reaction_busy)))
        }
        var committing = false
        return try {
            sendPendingPaidReaction(
                add = { request(TdApi.AddPendingPaidMessageReaction(chatId, messageId, starCount, paidReactionType(isAnonymous))) },
                commit = {
                    committing = true
                    request(TdApi.CommitPendingPaidMessageReactions(chatId, messageId))
                },
                rollback = { request(TdApi.RemovePendingPaidMessageReactions(chatId, messageId)) }
            )
            Result.success(Unit)
        } catch (timeout: TimeoutCancellationException) {
            Result.failure(paidReactionFailure(timeout, committing))
        } catch (cancelled: CancellationException) {
            // The commit may already be with TDLib, which still charges the Stars when nobody waits for the answer.
            // So a cancelled wait after the commit was sent is reported as uncertain; before it, nothing can be spent.
            if (!committing) throw cancelled
            Result.failure(paidReactionFailure(cancelled, committing))
        } catch (error: Exception) {
            Result.failure(paidReactionFailure(error, committing))
        } finally {
            sendInFlight.set(false)
        }
    }
}

/**
 * Runs [add] and then [commit]. When either one fails, [rollback] removes the pending reaction, even if the coroutine has been
 * cancelled, and the original failure is rethrown with a failed rollback attached as suppressed.
 */
internal suspend fun sendPendingPaidReaction(
    add: suspend () -> Unit,
    commit: suspend () -> Unit,
    rollback: suspend () -> Unit
) {
    try {
        add()
        commit()
    } catch (failure: Throwable) {
        withContext(NonCancellable) {
            runCatching { rollback() }.onFailure { failure.addSuppressed(it) }
        }
        throw failure
    }
}

/**
 * A failure before the commit was sent, or a refusal by TDLib, is definite: no Stars were spent. Any other failure after the commit
 * was sent may still have spent them, so it is reported as uncertain.
 */
private fun paidReactionFailure(error: Exception, committing: Boolean): UserMessageException = when {
    committing && error !is TdRequestError -> PaidReactionUncertainException(UiText.Res(R.string.err_paid_reaction_uncertain), error)
    else -> UserMessageException(paidReactionErrorText(error), error)
}

/** Clear text for a refused Stars reaction; each TDLib reason has its own explanation and an unknown one stays generic. */
internal fun paidReactionErrorText(error: Exception): UiText {
    val message = error.message.orEmpty()
    return when {
        error is TimeoutCancellationException -> UiText.Res(R.string.err_monetization_timeout)
        message.contains("BALANCE_TOO_LOW") -> UiText.Res(R.string.err_paid_reaction_balance_low)
        (error is TdRequestError && error.code == 429) || message.contains("FLOOD_WAIT") -> UiText.Res(R.string.err_tdlib_flood_wait)
        (error is TdRequestError && error.code == 404) || message.contains("MESSAGE_ID_INVALID") ->
            UiText.Res(R.string.err_tdlib_message_unavailable)
        message.contains("REACTION_INVALID") || message.contains("CHAT_WRITE_FORBIDDEN") || message.contains("CHAT_FORBIDDEN") ->
            UiText.Res(R.string.err_paid_reaction_unavailable)
        else -> UiText.Res(R.string.err_paid_reaction_failed)
    }
}

/** The reaction is shown with the sender's name unless the caller asks for anonymity. */
internal fun paidReactionType(isAnonymous: Boolean): TdApi.PaidReactionType =
    if (isAnonymous) TdApi.PaidReactionTypeAnonymous() else TdApi.PaidReactionTypeRegular()
