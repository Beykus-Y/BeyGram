package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Снимает реакции [displaced], затем добавляет новую через [add]. Если любой запрос не прошёл или корутину отменили,
 * уже снятые реакции возвращаются через [restore] в обратном порядке. Возврат идёт даже при отмене: сервер уже принял
 * снятие. Ошибку возврата прикрепляем к исходной как подавленную, чтобы она не терялась.
 */
internal suspend fun <T> replaceReactions(
    displaced: List<T>,
    add: suspend () -> Unit,
    remove: suspend (T) -> Unit,
    restore: suspend (T) -> Unit,
) {
    val removed = mutableListOf<T>()
    try {
        displaced.forEach { reaction ->
            remove(reaction)
            removed.add(reaction)
        }
        add()
    } catch (failure: Throwable) {
        withContext(NonCancellable) {
            removed.asReversed().forEach { reaction ->
                runCatching { restore(reaction) }.onFailure { failure.addSuppressed(it) }
            }
        }
        throw failure
    }
}
