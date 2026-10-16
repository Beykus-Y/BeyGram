package com.beykus.beygram.diagnostics

/**
 * Последняя линия защиты перед записью отчёта: убирает токены ботов, api_hash, пароли,
 * коды и номера телефонов, даже если они каким-то образом попали в текст.
 */
internal object DiagnosticsRedactor {
    private val botToken = Regex("""\b\d{6,12}:[A-Za-z0-9_-]{30,}(?![A-Za-z0-9_-])""")
    // Номер с плюсом (пробелы, скобки и дефисы внутри допускаются, переводы строк нет),
    // либо любая длинная цифровая строка. Даты и время вида 2026-10-16 не затрагиваются.
    private val phoneNumber = Regex("""\+\d[\d ().-]{6,}\d|\b\d{7,}\b""")
    // Значение может уже быть заменено на плейсхолдер из phoneNumber, поэтому берём и его целиком.
    private val keyedSecret = Regex("""(?i)\b(api[_-]?hash|token|password|passwd|secret|code|phone)(\s*[=:]\s*)(\[[^\]\n]*\]|\S+)""")

    fun redact(text: String): String = text
        .replace(botToken, "[токен скрыт]")
        .replace(phoneNumber, "[номер скрыт]")
        .replace(keyedSecret) { "${it.groupValues[1]}${it.groupValues[2]}[скрыто]" }
}
