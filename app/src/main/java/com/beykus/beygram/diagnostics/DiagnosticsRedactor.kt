package com.beykus.beygram.diagnostics

/**
 * Убирает из текста диагностики только секреты: bot-токены, api_hash, номера телефонов, коды и пароли.
 * Строки и числа, которые не являются персональными данными, остаются как есть.
 */
internal object DiagnosticsRedactor {
    private val botToken = Regex("""\b\d{6,12}:[A-Za-z0-9_-]{30,}(?![A-Za-z0-9_-])""")
    // Номера телефонов (международный, с пробелами и дефисами внутри), либо длинные цифровые
    // последовательности без «+», которые могут оказаться номером. Длинные числа redactятся,
    // только если не являются значением типизированного диагностического атрибута.
    private val phoneNumber = Regex("""\+\d[\d ().-]{6,}\d|\b\d{7,}\b""")
    // Значения этих ключей — счётчики, размеры, смещения и тайминги; они не персональные данные.
    // Список намеренно закрытый: новый числовой атрибут добавляется сюда осознанно, вместе с тестом.
    private val numericDiagnosticKeys = setOf(
        "attempt",
        "bytes_read",
        "count",
        "download_ms",
        "download_offset",
        "duration_ms",
        "duration_s",
        "duration_sec",
        "elapsed_ms",
        "file_size_bytes",
        "first_read_ms",
        "first_ready_ms",
        "frames",
        "heap_used_kb_at_start",
        "heap_used_kb_end",
        "kbps",
        "link_ms",
        "moov_bytes",
        "offsets_ms",
        "playing_ms",
        "prefix_bytes",
        "rate",
        "rebuffer_count",
        "rebuffer_total_ms",
        "seek_count",
        "size_bytes",
        "size_kb",
        "total_bytes",
        "total_ms",
        "tries",
        "underruns",
        "unlock_ms",
        "waited_s",
    )
    private val keyedSecret = Regex("""(?i)\b(api[_-]?hash|token|password|passwd|secret|code|phone)(\s*[=:]\s*)(\[[^\]\n]*\]|\S+)""")
    private val pureNumber = Regex("""-?\d{1,18}""")

    fun redact(text: String): String {
        // Numbers first: a phone number with spaces must be removed as a whole, before the keyed-value rule sees
        // only its first token. Typed numeric diagnostics are the single exception and stay untouched.
        // A bot token contains digits that would otherwise be taken for a phone number, so it is removed first.
        val withoutTokens = text.replace(botToken, "[скрыт токен]")
        val withoutNumbers = withoutTokens.replace(phoneNumber) { match ->
            if (isTypedDiagnosticNumber(withoutTokens, match)) match.value else "[номер скрыт]"
        }
        return withoutNumbers
            .replace(keyedSecret) { "${it.groupValues[1]}${it.groupValues[2]}[скрыто]" }
    }

    /**
     * True when the number is the value of a known numeric diagnostic attribute (`key=1234567`, `key: 1234567`).
     * Only in that case the digits are kept; any other long number stays redacted, so the global phone protection is
     * not weakened.
     */
    private fun isTypedDiagnosticNumber(text: String, match: MatchResult): Boolean {
        if (!pureNumber.matches(match.value)) return false
        var index = match.range.first - 1
        while (index >= 0 && (text[index] == ' ' || text[index] == '\t')) index--
        if (index < 0 || (text[index] != '=' && text[index] != ':')) return false
        index--
        while (index >= 0 && (text[index] == ' ' || text[index] == '\t')) index--
        val keyEnd = index + 1
        while (index >= 0 && (text[index].isLetterOrDigit() || text[index] == '_')) index--
        val key = text.substring(index + 1, keyEnd).lowercase()
        return key in numericDiagnosticKeys
    }
}