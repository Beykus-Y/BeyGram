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
