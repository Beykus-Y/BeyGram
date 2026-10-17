package com.beykus.beygram.diagnostics

import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity

/**
 * Пишет событие работы приложения сразу в текстовый журнал («Посмотреть» и «Поделиться логами») и в
 * структурированный буфер отчёта о проблеме. Передавайте только состояния, коды ошибок, длительности и размеры:
 * тексты сообщений, имена файлов, пути и номера сюда не попадают.
 */
fun recordAppEvent(
    kind: EventKind,
    code: String,
    text: String,
    severity: Severity = Severity.INFO,
    attrs: Map<String, String> = emptyMap()
) {
    ConnectionEventLog.shared.record(text)
    StructuredEventLog.shared.record(kind = kind, severity = severity, code = code, message = text, attrs = attrs)
}
