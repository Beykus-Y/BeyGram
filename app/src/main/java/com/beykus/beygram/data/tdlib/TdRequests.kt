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
        awaitResponse(function)
    }

    /**
     * Waits for TDLib without a local timeout. Account deletion must not be abandoned
     * while the server may already have committed it; a late successful response still
     * has to trigger local session cleanup.
     */
    internal suspend fun <T : TdApi.Object> awaitResponse(function: TdApi.Function<T>): T =
        suspendCancellableCoroutine { continuation ->
            client().send(function, Client.ResultHandler { result ->
                if (continuation.isActive) {
                    if (result is TdApi.Error) continuation.resumeWith(Result.failure(TdRequestError(result.code, result.message)))
                    else { @Suppress("UNCHECKED_CAST") continuation.resume(result as T) }
                }
            })
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
            error.message?.contains("PREMIUM_ACCOUNT_REQUIRED") == true -> UiText.Res(R.string.err_premium_required)
            error.message?.contains("STICKER") == true -> UiText.Res(R.string.err_sticker_send_failed)
            error.message?.contains("SCHEDULE_DATE") == true -> UiText.Res(R.string.err_tdlib_schedule_date)
            error.message?.contains("SCHEDULE_TOO_MUCH") == true -> UiText.Res(R.string.err_tdlib_schedule_too_much)
            error.message?.contains("SCHEDULE_") == true -> UiText.Res(R.string.err_tdlib_schedule_failed)
            error.message?.contains("SCHEDUL", ignoreCase = true) == true -> UiText.Res(R.string.err_tdlib_schedule_failed)
            else -> requestErrorText(error)
        }
    }

/** Контакты, звонки и чаты: сообщений здесь нет, поэтому 404 не значит удалённое сообщение. */
internal suspend fun <T> requestResult(block: suspend () -> T): Result<T> = telegramResult(REQUEST_TIMEOUT, block, ::requestErrorText)

/**
 * Администрирование группы: отказ прав, лимит запросов и конфликты участника должны объясняться
 * по-разному, чтобы администратор понимал, что можно исправить, а что нет.
 */
internal suspend fun <T> groupResult(block: suspend () -> T): Result<T> =
    telegramResult(UiText.Res(R.string.err_group_action_failed), block, ::groupErrorText)

private fun groupErrorText(error: Exception): UiText {
    val message = error.message.orEmpty()
    return when {
        error.isFloodWait() -> UiText.Res(R.string.err_tdlib_flood_wait)
        message.contains("USERNAME_OCCUPIED") -> UiText.Res(R.string.err_username_taken)
        message.contains("USERNAME_INVALID") -> UiText.Res(R.string.err_username_invalid)
        message.contains("USERNAME_PURCHASE_AVAILABLE") -> UiText.Res(R.string.err_username_purchase)
        message.contains("CHAT_ADMIN_REQUIRED") || message.contains("CHAT_NOT_ENOUGH_RIGHTS") ||
            message.contains("CHAT_WRITE_FORBIDDEN") || message.contains("CHAT_FORBIDDEN") ||
            message.contains("CHANNEL_PRIVATE") || message.contains("CHAT_MEMBER_REQUIRED") ||
            message.contains("CHAT_OWNER") || message.contains("administrator rights", ignoreCase = true) ||
            message.contains("Not enough rights", ignoreCase = true) ->
            UiText.Res(R.string.err_group_rights_missing)
        message.contains("USER_ALREADY_PARTICIPANT") || message.contains("already a participant", ignoreCase = true) ->
            UiText.Res(R.string.err_group_member_already)
        message.contains("USER_NOT_PARTICIPANT") || message.contains("not a participant", ignoreCase = true) ->
            UiText.Res(R.string.err_group_member_not_found)
        message.contains("PRIVACY", ignoreCase = true) -> UiText.Res(R.string.err_group_add_privacy)
        message.contains("INVITE_LINK") -> UiText.Res(R.string.err_group_invite_invalid)
        message.contains("CHAT_TITLE_EMPTY") -> UiText.Res(R.string.err_group_title_required)
        message.contains("PHOTO", ignoreCase = true) -> UiText.Res(R.string.err_group_photo_failed)
        else -> UiText.Res(R.string.err_group_action_failed)
    }
}

/** Translation and recognition have their own friendly errors; a secret-chat refusal is never a generic failure. */
internal suspend fun <T> translationResult(block: suspend () -> T): Result<T> =
    telegramResult(UiText.Res(R.string.err_translation_failed), block) { error ->
        when {
            error.isSecretChatError() -> UiText.Res(R.string.err_translation_secret_chat)
            error.isFloodWait() -> UiText.Res(R.string.err_tdlib_flood_wait)
            else -> UiText.Res(R.string.err_translation_failed)
        }
    }

internal suspend fun <T> speechRecognitionResult(block: suspend () -> T): Result<T> =
    telegramResult(UiText.Res(R.string.err_transcription_failed), block) { error ->
        when {
            error.isSecretChatError() -> UiText.Res(R.string.err_transcription_secret_chat)
            error.isFloodWait() -> UiText.Res(R.string.err_tdlib_flood_wait)
            else -> UiText.Res(R.string.err_transcription_failed)
        }
    }

private fun Exception.isSecretChatError(): Boolean =
    message?.contains("SECRET", ignoreCase = true) == true || message?.contains("CHAT_TYPE_INVALID") == true

/** Локальные операции с файлами: таймаут значит медленный диск, а не сеть, поэтому в диагностику соединения он не попадает. */
internal suspend fun <T> storageResult(block: suspend () -> T): Result<T> = try { Result.success(block()) }
catch (timeout: TimeoutCancellationException) { Result.failure(timeout) }
catch (cancelled: CancellationException) { throw cancelled }
catch (error: Exception) { Result.failure(error) }

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
    error.message?.contains("USERNAME_NOT_OCCUPIED") == true ||
        error.message?.contains("username not found", ignoreCase = true) == true ->
        UiText.Res(R.string.err_username_not_found)
    else -> UiText.Res(R.string.err_tdlib_action_failed)
}
