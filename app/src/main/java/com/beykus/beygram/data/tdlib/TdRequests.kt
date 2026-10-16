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

internal suspend fun <T> conversationResult(block: suspend () -> T): Result<T> = try { Result.success(block()) }
catch (_: TimeoutCancellationException) { Result.failure(IllegalStateException("Сервер не ответил вовремя. Проверьте состояние сообщения перед повторной попыткой.")) }
catch (cancelled: CancellationException) { throw cancelled }
catch (error: Exception) {
    val message = when {
        error.message?.contains("MESSAGE_NOT_MODIFIED") == true -> "Сообщение не изменилось"
        error.message?.contains("MESSAGE_EDIT_TIME_EXPIRED") == true -> "Время редактирования истекло"
        error.message?.contains("MESSAGE_ID_INVALID") == true || (error as? TdRequestError)?.code == 404 -> "Сообщение удалено или недоступно"
        error.message?.contains("MESSAGE_DELETE_FORBIDDEN") == true -> "Удаление этого сообщения недоступно"
        error.message?.contains("FLOOD_WAIT") == true || (error as? TdRequestError)?.code == 429 -> "Слишком много запросов. Попробуйте позже"
        else -> error.message ?: "Не удалось выполнить действие"
    }
    Result.failure(IllegalStateException(message))
}
