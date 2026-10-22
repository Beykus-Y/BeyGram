package com.beykus.beygram.share

import android.net.Uri

/**
 * Содержимое, которое другое приложение передало через «Поделиться». Пока оно не отправлено, живёт только в памяти активности.
 * [uris] — файлы из `EXTRA_STREAM`/`ClipData`; их копируем в приватную папку только при показе экрана выбора.
 */
data class IncomingShare(
    val requestId: Long, val text: String, val uris: List<Uri> = emptyList(),
    /** Suggested recipient from Android Direct Share, never a sending authorization. */
    val targetChatId: Long = 0L
)

/** Файлы из «Поделиться», которые приняты, и число отброшенных адресов. */
data class ShareUriCheck(val accepted: List<Uri>, val rejected: Int)

object IncomingShareParser {
    /** Предел текста одного сообщения Telegram в символах Unicode (кодовых точках). */
    const val MAX_TEXT_LENGTH = 4096

    /** Больше файлов за раз не принимаем: как и у вложений в чате, это предел одного альбома. */
    const val MAX_FILES = 10

    private const val ACTION_SEND = "android.intent.action.SEND"
    private const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
    private const val MIME_TEXT_PLAIN = "text/plain"
    private const val SCHEME_CONTENT = "content"

    /** FileProvider Prod и Dev: их файлы BeyGram отдаёт только себе, другим приложениям через «Поделиться» не отдаём. */
    private val BEYGRAM_FILE_AUTHORITIES = setOf("com.beykus.beygram.files", "com.beykus.beygram.dev.files")

    /**
     * Принимаем только `content://` от чужих приложений. `file://` открывает любой путь с правами BeyGram,
     * включая базу TDLib; свой FileProvider отдаёт скачанные медиа и диагностику.
     * Адрес с `userId@` или портом отбрасываем: ContentResolver отрезает `userId@` и откроет наш FileProvider.
     */
    fun isForeignContent(scheme: String?, authority: String?, ownPackage: String): Boolean {
        if (scheme?.lowercase() != SCHEME_CONTENT || authority.isNullOrEmpty()) return false
        if ('@' in authority || ':' in authority) return false
        val name = authority.lowercase()
        return name !in BEYGRAM_FILE_AUTHORITIES && !name.startsWith(ownPackage.lowercase())
    }

    fun checkUris(uris: List<Uri>, ownPackage: String): ShareUriCheck {
        val accepted = uris.filter { isForeignContent(it.scheme, it.authority, ownPackage) }
        return ShareUriCheck(accepted, uris.size - accepted.size)
    }

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

    /**
     * Все файлы запроса: из `EXTRA_STREAM` и из `ClipData`, без повторов. Часть приложений кладёт файлы только в `ClipData`.
     * Для других действий файлов нет.
     */
    fun <T> collectStreams(action: String?, streams: List<T>, clipItems: List<T>): List<T> =
        if (action != ACTION_SEND && action != ACTION_SEND_MULTIPLE) emptyList() else (streams + clipItems).distinct()
}
