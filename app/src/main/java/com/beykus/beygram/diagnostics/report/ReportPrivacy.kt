package com.beykus.beygram.diagnostics.report

/**
 * Редакция строк, попадающих в диагностический отчёт.
 *
 * Серверный sanitizer (`app/redaction.py`) — вторая линия, а не замена:
 * клиент не должен класть секреты и адреса в события вообще. Здесь —
 * последний барьер на устройстве для message/attrs перед сериализацией.
 *
 * Допустимы только маркеры замены вида `[скрыто: <класс>]`; всё, что похоже
 * на URL, адрес, токен, номер или ключ, удаляется независимо от источника.
 */
object ReportPrivacy {

    private val botToken = Regex("""\b\d{6,12}:[A-Za-z0-9_-]{30,}(?![A-Za-z0-9_-])""")
    private val jwt = Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b""")
    private val longHex = Regex("""\b[0-9a-fA-F]{32,}\b""")
    private val longBase64Like = Regex("""\b[A-Za-z0-9+/=_-]{40,}\b""")
    private val url = Regex("""(?i)\b[a-z][a-z0-9+.-]{1,31}://[^\s<>"']+""")
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b""")
    // Кандидаты IPv6: скобочная форма или ≥3 двоеточий (времена вида 12:00:00 не трогаем).
    private val ipv6Candidate = Regex("""\[[0-9a-fA-F:]+]|(?:[0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{0,4}""")
    private val hostname = Regex("""\b(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,24}(?::\d{1,5})?\b""", RegexOption.IGNORE_CASE)
    private val phoneNumber = Regex("""\+\d[\d ().-]{6,}\d|\b\d{7,}\b""")
    private val keyedSecret = Regex(
        """(?i)\b(api[_-]?hash|api[_-]?id|token|password|passwd|secret|authorization|credential|key|cookie|session|phone|nonce|login|user(?:name)?|host|server|endpoint|proxy|subscribe(?:_url)?|url|link)(\s*[=:]\s*)(\[[^\]\n]*\]|\S+)"""
    )
    private val subscriptionScheme = Regex("""(?i)\b(?:vmess|vless|trojan|ss|ssr|hysteria2?|tuic|socks5?|https?)://[^\s<>"']+""")

    /** Возвращает строку, безопасную для отправки: секреты и адреса заменены маркерами. */
    fun redact(text: String): String {
        var result = text
        result = result.replace(botToken, "[скрыто: токен]")
        result = result.replace(jwt, "[скрыто: токен]")
        result = result.replace(subscriptionScheme, "[скрыто: ссылка]")
        result = result.replace(url) { match ->
            // Схему оставляем, адрес скрываем: класс ссылки полезен, содержимое — нет.
            "${match.value.substringBefore("://")}://[скрыто: ссылка]"
        }
        result = result.replace(ipv4, "[скрыто: адрес]")
        result = result.replace(ipv6Candidate) { match ->
            val value = match.value
            val isBracketed = value.startsWith("[")
            val colonCount = value.count { it == ':' }
            if (isBracketed || colonCount >= 3) "[скрыто: адрес]" else value
        }
        result = result.replace(hostname, "[скрыто: хост]")
        result = result.replace(phoneNumber, "[скрыто: номер]")
        result = result.replace(longHex, "[скрыто: ключ]")
        result = result.replace(longBase64Like, "[скрыто: ключ]")
        result = result.replace(keyedSecret) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}[скрыто]"
        }
        return result
    }

    /**
     * Доверенные числовые метрики: значения этих ключей сохраняются как есть, если это чистое число.
     * Список закрытый и проверяется **только по ключу структурированного атрибута**, а не по подстроке в тексте:
     * свободный текст всегда проходит полную редакцию через [redact], поэтому длинный номер в сообщении остаётся
     * скрытым, даже если рядом стоит похожее имя поля.
     */
    private val numericDiagnosticKeys = setOf(
        "attempt", "bytes_read", "count", "download_ms", "download_offset", "duration_ms", "duration_s",
        "duration_sec", "elapsed_ms", "file_size_bytes", "first_read_ms", "first_ready_ms", "frames",
        "heap_used_kb_at_start", "heap_used_kb_end", "kbps", "link_ms", "moov_bytes", "offsets_ms",
        "playing_ms", "prefix_bytes", "rate", "rebuffer_count", "rebuffer_total_ms", "seek_count",
        "size_bytes", "size_kb", "total_bytes", "total_ms", "tries", "underruns", "unlock_ms", "waited_s",
    )
    private val pureNumber = Regex("-?[0-9]{1,18}")

    /**
     * Подготовка значения структурированного атрибута, где ключ известен.
     *
     * Для доверенного числового ключа чистое число сохраняется: это метрика (размер, смещение, счётчик, тайминг), а не
     * персональные данные. Во всех остальных случаях применяется обычная строгая редакция [sanitize], поэтому
     * произвольный текст, ссылки, адреса и номера телефонов скрываются как раньше.
     */
    fun sanitizeAttribute(key: String, value: String, maxChars: Int): String {
        val cleaned = stripControls(value).trim()
        if (key.lowercase() in numericDiagnosticKeys && pureNumber.matches(cleaned)) {
            return if (cleaned.length <= maxChars) cleaned else cleaned.substring(0, maxChars)
        }
        return sanitize(value, maxChars)
    }

    /** Убирает управляющие символы, кроме \n и \t, как ожидает сервер (`_strip_controls`). */
    fun stripControls(value: String): String =
        buildString(value.length) {
            for (ch in value) {
                if (ch == '\n' || ch == '\t' || ch.code >= 32) append(ch)
            }
        }

    /** Полная подготовка строки: контроль символов, редакция, ограничение длины по кодовым точкам. */
    fun sanitize(value: String, maxChars: Int): String {
        val cleaned = redact(stripControls(value)).trim('\n', '\r', ' ', '\t')
        if (cleaned.length <= maxChars) return cleaned
        var end = maxChars
        while (end > 0 && Character.isHighSurrogate(cleaned[end - 1])) end--
        return cleaned.substring(0, end)
    }
}
