package com.beykus.beygram.diagnostics.report

import com.beykus.beygram.diagnostics.StructuredEventLog

/**
 * Готовит снимок диагностики к отправке: превращает структурированные события
 * в безопасные [DiagnosticEvent], генерирует понятную сводку и укладывает
 * payload в байтовый бюджет сервера (256 КиБ; клиентский headroom — 240 КиБ).
 *
 * Приоритет обрезания: сначала события (старые), затем сводка.
 * Описание никогда не обрезается молча — оно проходит отдельную валидацию.
 */
object ReportSnapshot {

    const val MAX_EVENT_MESSAGE_CHARS = 500
    const val MAX_EVENT_ATTR_VALUE_CHARS = 200
    const val MAX_EVENT_ATTRS = 24
    const val MAX_EVENT_CODE_CHARS = 64
    const val MAX_SUMMARY_CHARS = BugReportPayload.MAX_SUMMARY_CHARS

    /** Локальный байтовый бюджет тела: ниже серверного лимита 262144 байта. */
    const val MAX_BODY_BYTES = 240 * 1024

    /** Безопасные события из буфера процесса, прошедшие редакцию и лимиты схемы. */
    fun collectEvents(log: StructuredEventLog = StructuredEventLog.shared): List<DiagnosticEvent> =
        log.snapshot().map { entry ->
            DiagnosticEvent(
                seq = entry.seq,
                atSeconds = entry.atMillis / 1000.0,
                kind = entry.kind,
                severity = entry.severity,
                code = ReportPrivacy.sanitize(entry.code, MAX_EVENT_CODE_CHARS).ifEmpty { null },
                message = ReportPrivacy.sanitize(entry.message, MAX_EVENT_MESSAGE_CHARS),
                attrs = sanitizeAttrs(entry.attrs)
            )
        }

    private fun sanitizeAttrs(attrs: Map<String, String>): Map<String, String> =
        attrs.entries.take(MAX_EVENT_ATTRS)
            .map { (key, value) ->
                ReportPrivacy.sanitize(key, MAX_EVENT_CODE_CHARS) to
                    ReportPrivacy.sanitize(value, MAX_EVENT_ATTR_VALUE_CHARS)
            }
            .filter { (key, _) -> key.isNotEmpty() }
            .toMap()

    /**
     * Сводка для человека: компактный текст того же снимка, ≤ 8000 символов.
     * Строится из уже безопасных событий, отдельная редакция не требуется,
     * но применяется на всякий случай.
     */
    fun buildSummary(events: List<DiagnosticEvent>): String {
        val lines = events.takeLast(120).map { event ->
            buildString {
                append("- ")
                append(event.atSeconds?.let { formatMoment(it) } ?: "—")
                append(' ')
                append(event.kind.wire)
                if (event.code != null) append('/').append(event.code)
                append(' ')
                if (event.severity != Severity.INFO) append('(').append(event.severity.wire).append(") ")
                if (event.message.isNotEmpty()) append(event.message)
                if (event.attrs.isNotEmpty()) {
                    append(" [")
                    append(
                        event.attrs.entries.joinToString(", ") { (key, value) -> "$key=$value" }
                    )
                    append(']')
                }
            }
        }
        return ReportPrivacy.sanitize(lines.joinToString("\n"), MAX_SUMMARY_CHARS)
    }

    private fun formatMoment(atSeconds: Double): String {
        val seconds = atSeconds.toLong()
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, secs)
    }

    /**
     * Проверяет, что payload помещается в бюджет, при необходимости урезает
     * события (старые первыми) и сводку. Возвращает null, если даже минимальный
     * отчёт не помещается — практически невозможно (описание ≤ 2000 символов).
     */
    fun fitToBudget(payload: BugReportPayload, maxBytes: Int = MAX_BODY_BYTES): BugReportPayload? {
        if (encodedSize(payload) <= maxBytes) return payload
        var events = payload.events
        var summary = payload.summary
        while (events.isNotEmpty() && encodedSize(payload.copy(events = events, summary = summary)) > maxBytes) {
            events = events.drop(maxOf(1, events.size / 4))
        }
        if (encodedSize(payload.copy(events = events, summary = summary)) <= maxBytes) {
            return payload.copy(events = events, summary = summary)
        }
        // Событий больше нет — сокращаем сводку ступенями.
        if (summary != null) {
            for (factor in listOf(2, 4, 8)) {
                val shortened = summary.take(summary.length / factor)
                if (encodedSize(payload.copy(events = events, summary = shortened)) <= maxBytes) {
                    return payload.copy(events = events, summary = shortened)
                }
            }
        }
        if (encodedSize(payload.copy(events = events, summary = null)) <= maxBytes) {
            return payload.copy(events = events, summary = null)
        }
        return null
    }

    /** Размер сериализованного тела в байтах UTF-8 — та величина, которую меряет сервер. */
    fun encodedSize(payload: BugReportPayload): Int = ReportJson.encode(payload).toByteArray(Charsets.UTF_8).size
}
