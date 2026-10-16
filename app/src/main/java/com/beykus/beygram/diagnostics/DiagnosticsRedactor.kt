package com.beykus.beygram.diagnostics

/**
 * Последняя линия защиты перед записью отчёта: убирает токены ботов, api_hash, пароли,
 * коды и номера телефонов, даже если они каким-то образом попали в текст.
 */
internal object DiagnosticsRedactor {
    private val botToken = Regex("""\b\d{6,12}:[A-Za-z0-9_-]{30,}\b""")
    private val keyedSecret = Regex("""(?i)\b(api[_-]?hash|token|password|passwd|secret|code|phone)(\s*[=:]\s*)\S+""")
    // Номер с плюсом, либо любая длинная цифровая строка. Даты и время вида 2026-10-16 не затрагиваются.
    private val phoneNumber = Regex("""\+\d[\d\s().-]{6,}\d|\b\d{7,}\b""")

    fun redact(text: String): String = text
        .replace(botToken, "[токен скрыт]")
        .replace(keyedSecret) { "${it.groupValues[1]}${it.groupValues[2]}[скрыто]" }
        .replace(phoneNumber, "[номер скрыт]")
}
