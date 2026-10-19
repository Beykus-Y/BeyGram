package com.beykus.beygram.share

/**
 * Содержимое, которое другое приложение передало через «Поделиться». Пока оно не отправлено, живёт только в памяти активности.
 * [fileUnsupported] означает, что пришёл файл: его пока не импортируем, поэтому текст не отправляем.
 */
data class IncomingShare(val requestId: Long, val text: String, val fileUnsupported: Boolean = false)

object IncomingShareParser {
    /** Предел текста одного сообщения Telegram в символах Unicode (кодовых точках). */
    const val MAX_TEXT_LENGTH = 4096

    private const val ACTION_SEND = "android.intent.action.SEND"
    private const val MIME_TEXT_PLAIN = "text/plain"

    /**
     * Разбирает входящий `ACTION_SEND` с типом text/plain. Файл в `EXTRA_STREAM` даёт [IncomingShare.fileUnsupported];
     * пустой запрос без текста и файла возвращает null. Пробелы по краям убираем, переносы внутри сохраняем.
     */
    fun from(action: String?, mimeType: String?, text: CharSequence?, hasStream: Boolean, requestId: Long): IncomingShare? {
        if (action != ACTION_SEND || mimeType?.startsWith(MIME_TEXT_PLAIN) != true) return null
        if (hasStream) return IncomingShare(requestId, text = "", fileUnsupported = true)
        val value = text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return IncomingShare(requestId, value)
    }
}
