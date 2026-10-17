package com.beykus.beygram.diagnostics

import com.beykus.beygram.diagnostics.report.DiagnosticEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity

/**
 * Структурированный журнал безопасных событий диагностики — источник данных
 * для отчёта о проблеме. Это не вторая система сбора: те же события, что идут
 * в текстовый [ConnectionEventLog], дублируются здесь в машиночитаемой форме
 * с конечным набором кодов и атрибутов. Авторы событий обязаны передавать
 * только нечувствительные значения (состояния, счётчики, коды ошибок).
 *
 * Хранится только в памяти процесса, кольцевой буфер как у текстового журнала.
 */
class StructuredEventLog(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    data class Entry(
        val seq: Long,
        val atMillis: Long,
        val kind: EventKind,
        val severity: Severity,
        val code: String,
        val message: String,
        val attrs: Map<String, String>,
    ) {
        fun toDiagnosticEvent(): DiagnosticEvent = DiagnosticEvent(
            seq = seq,
            atSeconds = atMillis / 1000.0,
            kind = kind,
            severity = severity,
            code = code,
            message = message,
            attrs = attrs
        )
    }

    private val events = ArrayDeque<Entry>(capacity)
    private var nextSeq = 0L
    private var overflowed = false

    /**
     * Записывает событие. Вызывается из боевых участков кода с фиксированными
     * кодами; свободных строк из логов и пользовательских данных здесь быть не должно.
     */
    @Synchronized
    fun record(
        kind: EventKind,
        severity: Severity = Severity.INFO,
        code: String,
        message: String = "",
        attrs: Map<String, String> = emptyMap()
    ) {
        if (events.size == capacity) {
            events.removeFirst()
            overflowed = true
        }
        events.addLast(
            Entry(
                seq = nextSeq++,
                atMillis = clock(),
                kind = kind,
                severity = severity,
                code = code,
                message = message,
                attrs = attrs
            )
        )
    }

    /** Самые старые события первыми. */
    @Synchronized
    fun snapshot(): List<Entry> = events.toList()

    /** Буфер переполнялся с запуска процесса: часть событий утеряна. */
    @Synchronized
    fun hasOverflowed(): Boolean = overflowed

    /** Сколько событий записано с запуска процесса, включая уже вытесненные из буфера. */
    @Synchronized
    fun totalRecorded(): Long = nextSeq

    companion object {
        const val DEFAULT_CAPACITY = 200

        /** Единый буфер процесса для отчёта о проблеме. */
        val shared = StructuredEventLog()
    }
}
