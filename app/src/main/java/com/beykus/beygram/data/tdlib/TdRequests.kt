package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.diagnostics.ConnectionFailure
import com.beykus.beygram.diagnostics.recordConnectionFailure
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume

internal class TdRequestError(val code: Int, message: String) : IllegalStateException(message)

private const val DEFAULT_REQUEST_TIMEOUT_MS = 30_000L

internal class TdRequests(private val client: () -> Client) {
    suspend operator fun <T : TdApi.Object> invoke(function: TdApi.Function<T>, timeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MS): T = withTimeout(timeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            client().send(function, Client.ResultHandler { result ->
                if (continuation.isActive) {
                    if (result is TdApi.Error) continuation.resumeWith(Result.failure(TdRequestError(result.code, result.message)))
                    else { @Suppress("UNCHECKED_CAST") continuation.resume(result as T) }
                }
            })
        }
    }
}

private val MESSAGE_TIMEOUT = UiText.Res(R.string.err_tdlib_message_timeout)
private val REQUEST_TIMEOUT = UiText.Res(R.string.err_tdlib_request_timeout)

/** Запросы к сообщениям: здесь 404 и MESSAGE_ID_INVALID значат, что сообщение удалено или недоступно. */
internal suspend fun <T> conversationResult(timeoutMessage: UiText = MESSAGE_TIMEOUT, block: suspend () -> T): Result<T> =
    telegramResult(timeoutMessage, block) { error ->
        when {
            error.message?.contains("MESSAGE_NOT_MODIFIED") == true -> UiText.Res(R.string.err_tdlib_message_not_modified)
            error.message?.contains("MESSAGE_EDIT_TIME_EXPIRED") == true -> UiText.Res(R.string.err_tdlib_message_edit_expired)
            error.message?.contains("MESSAGE_ID_INVALID") == true || (error as? TdRequestError)?.code == 404 -> UiText.Res(R.string.err_tdlib_message_unavailable)
            error.message?.contains("MESSAGE_DELETE_FORBIDDEN") == true -> UiText.Res(R.string.err_tdlib_message_delete_forbidden)
            else -> requestErrorText(error)
        }
    }

/** Контакты, звонки и чаты: сообщений здесь нет, поэтому 404 не значит удалённое сообщение. */
internal suspend fun <T> requestResult(block: suspend () -> T): Result<T> = telegramResult(REQUEST_TIMEOUT, block, ::requestErrorText)

private suspend fun <T> telegramResult(timeoutMessage: UiText, block: suspend () -> T, errorText: (Exception) -> UiText): Result<T> = try { Result.success(block()) }
catch (timeout: TimeoutCancellationException) {
    recordConnectionFailure(ConnectionFailure.Timeout)
    Result.failure(UserMessageException(timeoutMessage, timeout))
}
catch (cancelled: CancellationException) { throw cancelled }
catch (error: UserMessageException) { Result.failure(error) }
catch (error: Exception) {
    if (error.isFloodWait()) recordConnectionFailure(ConnectionFailure.FloodWait)
    Result.failure(UserMessageException(errorText(error), error))
}

private fun Exception.isFloodWait(): Boolean = message?.contains("FLOOD_WAIT") == true || (this as? TdRequestError)?.code == 429

private fun requestErrorText(error: Exception): UiText = when {
    error.isFloodWait() -> UiText.Res(R.string.err_tdlib_flood_wait)
    else -> UiText.Res(R.string.err_tdlib_action_failed)
}
