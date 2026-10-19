package com.beykus.beygram.share

/** Текст, который другое приложение передало через «Поделиться». Пока он не отправлен, живёт только в памяти активности. */
data class IncomingShare(val requestId: Long, val text: String)

object IncomingShareParser {
    /** Предел текста одного сообщения Telegram. Длиннее не отправляем, а предлагаем сократить. */
    const val MAX_TEXT_LENGTH = 4096

    private const val ACTION_SEND = "android.intent.action.SEND"
    private const val MIME_TEXT_PLAIN = "text/plain"

    /** Текст из входящего `ACTION_SEND` или null, если это не непустой текст. Пробелы по краям убираем, переносы внутри сохраняем. */
    fun textOf(action: String?, mimeType: String?, text: CharSequence?): String? {
        if (action != ACTION_SEND || mimeType?.startsWith(MIME_TEXT_PLAIN) != true) return null
        return text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
