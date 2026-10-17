package com.beykus.beygram.diagnostics.report

/**
 * Кодирование payload и разбор ответов API без внешних зависимостей.
 *
 * Кодирование детерминировано: один и тот же payload всегда даёт одни и те же
 * байты — это условие идемпотентного повтора на сервере (отпечаток содержимого).
 * Парсер рассчитан на маленькие ответы фиксированной схемы и возвращает null
 * вместо исключения на неожиданную форму.
 */
object ReportJson {

    // --- Кодирование ---------------------------------------------------------

    fun encode(payload: BugReportPayload): String = buildString {
        append('{')
        append("\"schema_version\":").append(BugReportPayload.SCHEMA_VERSION)
        append(",\"report_id\":").append(quote(payload.reportId))
        append(",\"description\":").append(quote(payload.description))
        payload.summary?.let { append(",\"summary\":").append(quote(it)) }
        payload.clientMeta?.let { meta ->
            append(",\"client_meta\":{")
            var first = true
            fun field(name: String, value: String?) {
                if (value == null) return
                if (!first) append(',')
                first = false
                append("\"$name\":").append(quote(value))
            }
            fun field(name: String, value: Int?) {
                if (value == null) return
                if (!first) append(',')
                first = false
                append("\"$name\":").append(value)
            }
            fun field(name: String, value: Boolean?) {
                if (value == null) return
                if (!first) append(',')
                first = false
                append("\"$name\":").append(value)
            }
            field("app_version", meta.appVersion)
            field("app_version_code", meta.appVersionCode)
            field("platform", meta.platform)
            field("os_version", meta.osVersion)
            field("device_model", meta.deviceModel)
            field("locale", meta.locale)
            field("vpn_active", meta.vpnActive)
            field("proxy_enabled", meta.proxyEnabled)
            field("tdlib_version", meta.tdlibVersion)
            field("event_count_total", meta.eventCountTotal)
            field("buffer_overflowed", meta.bufferOverflowed)
            append('}')
        }
        if (payload.events.isNotEmpty()) {
            append(",\"events\":[")
            payload.events.forEachIndexed { index, event ->
                if (index > 0) append(',')
                append('{')
                var first = true
                fun field(name: String, value: String, isNumber: Boolean = false) {
                    if (!first) append(',')
                    first = false
                    append("\"$name\":")
                    if (isNumber) append(value) else append(quote(value))
                }
                event.seq?.let { field("seq", it.toString(), isNumber = true) }
                event.atSeconds?.let { field("at", formatNumber(it), isNumber = true) }
                field("kind", event.kind.wire)
                field("severity", event.severity.wire)
                event.code?.let { field("code", it) }
                field("message", event.message)
                if (event.attrs.isNotEmpty()) {
                    append(",\"attrs\":{")
                    event.attrs.entries.forEachIndexed { attrIndex, (key, value) ->
                        if (attrIndex > 0) append(',')
                        append(quote(key)).append(':').append(quote(value))
                    }
                    append('}')
                }
                append('}')
            }
            append(']')
        }
        payload.createdAtSeconds?.let { append(",\"created_at\":").append(formatNumber(it)) }
        append('}')
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }

    /** Стандартное JSON-экранирование строки, включая управляющие символы. */
    fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else ->
                    if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

    /** Читаемый отступ для локального превью; не участвует в отправке. */
    fun prettyPrint(text: String): String {
        val builder = StringBuilder(text.length + 64)
        var indent = 0
        var inString = false
        var escaped = false
        for (ch in text) {
            when {
                inString -> {
                    builder.append(ch)
                    when {
                        escaped -> escaped = false
                        ch == '\\' -> escaped = true
                        ch == '"' -> inString = false
                    }
                }
                ch == '"' -> {
                    builder.append(ch)
                    inString = true
                }
                ch == '{' || ch == '[' -> {
                    builder.append(ch).append('\n')
                    indent++
                    builder.append(INDENT.repeat(indent))
                }
                ch == '}' || ch == ']' -> {
                    builder.append('\n')
                    indent--
                    builder.append(INDENT.repeat(indent)).append(ch)
                }
                ch == ',' -> builder.append(ch).append('\n').append(INDENT.repeat(indent))
                ch == ':' -> builder.append(": ")
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    private val INDENT = "  "

    // --- Разбор --------------------------------------------------------------

    fun parse(text: String): Any? = Parser(text).let { parser ->
        val value = parser.parseValue()
        value
    }

    private class Parser(private val text: String) {
        private var position = 0

        fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (position >= text.length) return null
            return when (text[position]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            position++ // {
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (position < text.length && text[position] == '}') {
                position++
                return result
            }
            while (position < text.length) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                position++ // :
                result[key] = parseValue()
                skipWhitespace()
                when (text.getOrNull(position)) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return result
                    }
                    else -> return result
                }
            }
            return result
        }

        private fun parseArray(): List<Any?> {
            position++ // [
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (position < text.length && text[position] == ']') {
                position++
                return result
            }
            while (position < text.length) {
                result.add(parseValue())
                skipWhitespace()
                when (text.getOrNull(position)) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return result
                    }
                    else -> return result
                }
            }
            return result
        }

        private fun parseString(): String {
            position++ // "
            val builder = StringBuilder()
            while (position < text.length) {
                val ch = text[position]
                when {
                    ch == '"' -> {
                        position++
                        return builder.toString()
                    }
                    ch == '\\' -> {
                        position++
                        when (val escape = text.getOrNull(position)) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'u' -> {
                                val hex = text.substring(position + 1, position + 5)
                                builder.append(hex.toInt(16).toChar())
                                position += 4
                            }
                            else -> builder.append(escape)
                        }
                        position++
                    }
                    else -> {
                        builder.append(ch)
                        position++
                    }
                }
            }
            return builder.toString()
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            if (text.regionMatches(position, literal, 0, literal.length)) {
                position += literal.length
                return value
            }
            position = text.length
            return null
        }

        private fun parseNumber(): Any? {
            val start = position
            while (position < text.length && text[position] in "0123456789+-.eE") position++
            val raw = text.substring(start, position)
            if (raw.isEmpty()) return null
            return raw.toLongOrNull() ?: raw.toDoubleOrNull()
        }
    }

    private fun Any?.asMap(): Map<*, *>? = this as? Map<*, *>
    private fun Map<*, *>.string(key: String): String? = this[key] as? String
    private fun Map<*, *>.number(key: String): Double? = when (val value = this[key]) {
        is Long -> value.toDouble()
        is Double -> value
        is Int -> value.toDouble()
        else -> null
    }
    private fun Map<*, *>.boolean(key: String): Boolean? = this[key] as? Boolean

    /** Ответ 202 на POST; null при неожиданной форме — вызывающая сторона решает, как считать такой ответ. */
    fun parseAccepted(json: Any?): AcceptedReport? {
        val map = json.asMap() ?: return null
        val reportId = map.string("report_id") ?: return null
        val status = map.string("status") ?: return null
        val acceptedAt = map.number("accepted_at") ?: return null
        val token = map.string("status_token") ?: return null
        return AcceptedReport(
            reportId = reportId,
            status = status,
            acceptedAtSeconds = acceptedAt,
            statusToken = token,
            idempotentReplay = map.boolean("idempotent_replay") == true,
            detail = map.string("detail").orEmpty()
        )
    }

    fun parseStatus(json: Any?): ReportStatusSnapshot? {
        val map = json.asMap() ?: return null
        val reportId = map.string("report_id") ?: return null
        val status = map.string("status") ?: return null
        val acceptedAt = map.number("accepted_at") ?: return null
        val updatedAt = map.number("updated_at") ?: return null
        return ReportStatusSnapshot(
            reportId = reportId,
            status = status,
            acceptedAtSeconds = acceptedAt,
            updatedAtSeconds = updatedAt,
            deliveredAtSeconds = map.number("delivered_at"),
            attempts = when (val attempts = map["attempts"]) {
                is Long -> attempts.toInt()
                is Double -> attempts.toInt()
                else -> 0
            },
            detail = map.string("detail").orEmpty()
        )
    }

    /** Код ошибки из конверта `{"error": {"code": ...}}`; null, если это не ошибка сервера. */
    fun parseErrorCode(json: Any?): String? = (json.asMap()?.get("error") as? Map<*, *>)?.string("code")

    /** Текст ошибки из конверта — только для показа пользователю, не для решений. */
    fun parseErrorMessage(json: Any?): String? =
        (json.asMap()?.get("error") as? Map<*, *>)?.string("message")
}
