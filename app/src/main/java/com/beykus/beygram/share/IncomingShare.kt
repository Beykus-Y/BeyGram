package com.beykus.beygram.share

import android.net.Uri

/**
 * Содержимое, которое другое приложение передало через «Поделиться». Пока оно не отправлено, живёт только в памяти активности.
 * [uris] — файлы из `EXTRA_STREAM`/`ClipData`; их копируем в приватную папку только при показе экрана выбора.
 */
data class IncomingShare(val requestId: Long, val text: String, val uris: List<Uri> = emptyList())

object IncomingShareParser {
    /** Предел текста одного сообщения Telegram в символах Unicode (кодовых точках). */
    const val MAX_TEXT_LENGTH = 4096

    /** Больше файлов за раз не принимаем: как и у вложений в чате, это предел одного альбома. */
    const val MAX_FILES = 10

    private const val ACTION_SEND = "android.intent.action.SEND"
    private const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
    private const val MIME_TEXT_PLAIN = "text/plain"

    /**
     * Разбирает входящий `SEND` или `SEND_MULTIPLE`. Если есть файлы, возвращает их вместе с подписью-текстом.
     * Чистый текст принимается только с типом text/plain. Пустой запрос возвращает null.
     */
    fun from(action: String?, mimeType: String?, text: CharSequence?, uris: List<Uri>, requestId: Long): IncomingShare? {
        if (action != ACTION_SEND && action != ACTION_SEND_MULTIPLE) return null
        val value = text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        if (uris.isNotEmpty()) return IncomingShare(requestId, value.orEmpty(), uris)
        if (mimeType?.startsWith(MIME_TEXT_PLAIN) != true || value == null) return null
        return IncomingShare(requestId, value)
    }
}
