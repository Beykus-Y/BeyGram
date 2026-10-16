package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume

internal class TdRequestError(val code: Int, message: String) : IllegalStateException(message)

internal class TdRequests(private val client: () -> Client) {
    suspend operator fun <T : TdApi.Object> invoke(function: TdApi.Function<T>): T = withTimeout(30_000) {
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

private const val MESSAGE_TIMEOUT = "Сервер не ответил вовремя. Проверьте состояние сообщения перед повторной попыткой."
private const val REQUEST_TIMEOUT = "Telegram не ответил вовремя. Попробуйте ещё раз."

/** Запросы к сообщениям: здесь 404 и MESSAGE_ID_INVALID значат, что сообщение удалено или недоступно. */
internal suspend fun <T> conversationResult(timeoutMessage: String = MESSAGE_TIMEOUT, block: suspend () -> T): Result<T> =
    telegramResult(timeoutMessage, block) { error ->
        when {
            error.message?.contains("MESSAGE_NOT_MODIFIED") == true -> "Сообщение не изменилось"
            error.message?.contains("MESSAGE_EDIT_TIME_EXPIRED") == true -> "Время редактирования истекло"
            error.message?.contains("MESSAGE_ID_INVALID") == true || (error as? TdRequestError)?.code == 404 -> "Сообщение удалено или недоступно"
            error.message?.contains("MESSAGE_DELETE_FORBIDDEN") == true -> "Удаление этого сообщения недоступно"
            else -> requestErrorText(error)
        }
    }

/** Контакты, звонки и чаты: сообщений здесь нет, поэтому 404 не значит удалённое сообщение. */
internal suspend fun <T> requestResult(block: suspend () -> T): Result<T> = telegramResult(REQUEST_TIMEOUT, block, ::requestErrorText)

private suspend fun <T> telegramResult(timeoutMessage: String, block: suspend () -> T, errorText: (Exception) -> String): Result<T> = try { Result.success(block()) }
catch (_: TimeoutCancellationException) { Result.failure(IllegalStateException(timeoutMessage)) }
catch (cancelled: CancellationException) { throw cancelled }
catch (error: Exception) { Result.failure(IllegalStateException(errorText(error))) }

private fun requestErrorText(error: Exception): String = when {
    error.message?.contains("FLOOD_WAIT") == true || (error as? TdRequestError)?.code == 429 -> "Слишком много запросов. Попробуйте позже"
    else -> error.message ?: "Не удалось выполнить действие"
}
