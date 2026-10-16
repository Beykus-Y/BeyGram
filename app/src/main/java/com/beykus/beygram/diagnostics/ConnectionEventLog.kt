package com.beykus.beygram.diagnostics

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale

/**
 * Кольцевой буфер недавних событий соединения, который попадает в отчёт «Поделиться логами».
 *
 * Хранится только в памяти процесса. Сюда пишите названия состояний и тип сети, но не
 * номера телефонов, коды, пароли, токены, адреса прокси или содержимое сообщений.
 */
class ConnectionEventLog(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val events = ArrayDeque<String>(capacity)

    @Synchronized
    fun record(event: String) {
        if (events.size == capacity) events.removeFirst()
        events.addLast("${TIME_FORMAT.format(Instant.ofEpochMilli(clock()))} $event")
    }

    /** Самые старые события первыми. */
    @Synchronized
    fun snapshot(): List<String> = events.toList()

    companion object {
        const val DEFAULT_CAPACITY = 200

        /** Единый буфер процесса; его читает и TDLib-репозиторий, и сетевой монитор. */
        val shared = ConnectionEventLog()

        private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
            .withZone(ZoneId.systemDefault())
    }
}
