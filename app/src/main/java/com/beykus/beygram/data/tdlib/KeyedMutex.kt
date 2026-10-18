package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Отдельный замок на каждый ключ: операции с одним ключом идут по очереди, с разными выполняются параллельно.
 * Замок убирается, как только его никто не держит и не ждёт, поэтому таблица не растёт с числом сообщений,
 * которых касались раньше.
 */
internal class KeyedMutex<K : Any> {
    private class Slot {
        val mutex = Mutex()
        var users = 0
    }

    private val slots = HashMap<K, Slot>()

    suspend fun <T> withLock(key: K, action: suspend () -> T): T {
        val slot = synchronized(slots) { slots.getOrPut(key) { Slot() }.also { it.users++ } }
        try {
            return slot.mutex.withLock { action() }
        } finally {
            synchronized(slots) {
                slot.users--
                if (slot.users == 0) slots.remove(key)
            }
        }
    }

    /** Сколько ключей сейчас заняты или ждут. Нужно тестам, чтобы проверить очистку таблицы. */
    internal val activeKeys: Int get() = synchronized(slots) { slots.size }
}
