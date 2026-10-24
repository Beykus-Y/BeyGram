package com.beykus.beygram.data.tdlib

/** Короче этого номер телефона быть не может, значит его ввели не полностью. */
internal const val MIN_CONTACT_PHONE_DIGITS = 7

/**
 * Форматирование контакта для отправки в чат. TDLib передаёт vCard как есть, поэтому строки
 * экранируются по RFC 6350 (TEXT): обратная косая черта, точка с запятой, запятая и переводы строк.
 * Телефон не форматируется для показа: Telegram ожидает международный номер с ведущим «+».
 */
internal object ContactCard {
    private const val CRLF = "\r\n"

    fun vCard(firstName: String, lastName: String, phone: String): String = buildString {
        append("BEGIN:VCARD").append(CRLF)
        append("VERSION:3.0").append(CRLF)
        append("N:").append(escape(lastName)).append(';').append(escape(firstName)).append(";;;").append(CRLF)
        append("FN:").append(escape(fullName(firstName, lastName).ifBlank { phone })).append(CRLF)
        if (phone.isNotBlank()) append("TEL;TYPE=CELL:").append(escape(phone)).append(CRLF)
        append("END:VCARD").append(CRLF)
    }

    /** Оставляет только цифры и один ведущий плюс: пробелы, скобки и дефисы в TDLib не нужны. */
    fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        return if (trimmed.startsWith('+')) "+$digits" else digits
    }

    /** TDLib разрешает пустой телефон у контакта, но непустой должен быть полным. */
    fun isValidPhone(phone: String, allowEmpty: Boolean): Boolean {
        val digits = phone.count { it.isDigit() }
        return when {
            digits == 0 -> allowEmpty
            else -> digits >= MIN_CONTACT_PHONE_DIGITS
        }
    }

    fun fullName(firstName: String, lastName: String): String =
        listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    private fun escape(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                '\r' -> append("\\n")
                else -> append(character)
            }
        }
    }
}
