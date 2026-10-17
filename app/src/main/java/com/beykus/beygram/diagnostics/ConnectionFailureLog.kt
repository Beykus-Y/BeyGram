package com.beykus.beygram.diagnostics

import java.util.ArrayDeque

/** Ошибки подключения, которые показывает экран диагностики. Текст выбирает экран по виду ошибки. */
enum class ConnectionFailure { Timeout, Proxy, FloodWait }

data class ConnectionFailureEntry(val atMillis: Long, val failure: ConnectionFailure)

/**
 * Кольцевой буфер последних ошибок подключения. Хранится только в памяти процесса.
 * Сюда попадают только связанные с подключением ошибки: таймаут, прокси и FLOOD_WAIT.
 */
class ConnectionFailureLog(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val entries = ArrayDeque<ConnectionFailureEntry>(capacity)

    @Synchronized
    fun record(failure: ConnectionFailure) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(ConnectionFailureEntry(clock(), failure))
    }

    /** Самые новые ошибки первыми. */
    @Synchronized
    fun snapshot(): List<ConnectionFailureEntry> = entries.reversed()

    companion object {
        const val DEFAULT_CAPACITY = 20

        val shared = ConnectionFailureLog()
    }
}

/** Пишет ошибку на экран диагностики и в отчёт «Поделиться». */
fun recordConnectionFailure(failure: ConnectionFailure) {
    ConnectionFailureLog.shared.record(failure)
    ConnectionEventLog.shared.record("Ошибка подключения: ${failure.name}")
}
