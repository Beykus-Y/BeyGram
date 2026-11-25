package com.beykus.beygram.diagnostics

/**
 * Убирает из **свободного текста** диагностики секреты: bot-токены, api_hash, номера телефонов, коды и пароли.
 *
 * Здесь нет списка «доверенных» числовых атрибутов намеренно: в произвольной строке невозможно доказать, что
 * `bytes_read=` относится к метрике, а не к тексту сообщения. Числовые метрики сохраняются на границе
 * структурированных данных — `ReportPrivacy.sanitizeAttribute(key, value)`, где ключ известен и проверяется отдельно.
 */
internal object DiagnosticsRedactor {
    private val botToken = Regex("""\b\d{6,12}:[A-Za-z0-9_-]{30,}(?![A-Za-z0-9_-])""")
    // Номера телефонов (международный, с пробелами и дефисами внутри), либо длинные цифровые
    // последовательности без «+», которые могут оказаться номером.
    private val phoneNumber = Regex("""\+\d[\d ().-]{6,}\d|\b\d{7,}\b""")
    private val keyedSecret = Regex("""(?i)\b(api[_-]?hash|token|password|passwd|secret|code|phone)(\s*[=:]\s*)(\[[^\]\n]*\]|\S+)""")

    fun redact(text: String): String {
        // A bot token contains digits that would otherwise be taken for a phone number, so it is removed first.
        val withoutTokens = text.replace(botToken, "[скрыт токен]")
        val withoutNumbers = withoutTokens.replace(phoneNumber, "[номер скрыт]")
        return withoutNumbers.replace(keyedSecret) { "${it.groupValues[1]}${it.groupValues[2]}[скрыто]" }
    }
}