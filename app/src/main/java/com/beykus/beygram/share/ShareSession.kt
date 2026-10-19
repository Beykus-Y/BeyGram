package com.beykus.beygram.share

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.beykus.beygram.R
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Один запрос «Поделиться» с файлами. Копирование и отправка идут в общем scope приложения, а не в композиции,
 * поэтому поворот экрана не прерывает их и не даёт отправить второй раз.
 */
internal class ShareSession(val requestId: Long, val uris: List<Uri>, initialDraft: String) {
    var draft by mutableStateOf(initialDraft)
    var staged by mutableStateOf<List<OutgoingMedia>>(emptyList())
    var staging by mutableStateOf(uris.isNotEmpty())
    var sending by mutableStateOf(false)
    /** Всё отправлено: экран закрывается и открывает чат. */
    var sent by mutableStateOf(false)
    var sentChatId by mutableStateOf(0L)
    var error by mutableStateOf<UiText?>(null)
    private var started = false
    private var released = false
    private var importer: OutgoingMediaImporter? = null

    /** Копирует файлы в приватную папку один раз; вызов после поворота ничего не запускает повторно. */
    fun startStaging(context: Context, scope: CoroutineScope) {
        if (started || uris.isEmpty()) return
        started = true
        val staging = OutgoingMediaImporter(context)
        importer = staging
        scope.launch { stage(staging, context, scope) }
    }

    private suspend fun stage(importer: OutgoingMediaImporter, context: Context, scope: CoroutineScope) {
        if (uris.size > IncomingShareParser.MAX_FILES) {
            error = UiText.Res(R.string.composer_too_many_attachments)
            staging = false
            return
        }
        val items = mutableListOf<OutgoingMedia>()
        try {
            for (uri in uris) {
                // Фото и видео уходят как медиа, остальное как документ.
                val asDocument = withContext(Dispatchers.IO) { !isPhotoOrVideo(context, uri) }
                items += importer.prepare(uri, asDocument)
            }
            staged = items
            // Экран уже закрыли, пока копировали: копии никто не заберёт.
            if (released) discard(importer, scope, items)
        } catch (failure: Exception) {
            discard(importer, scope, items)
            error = UiText.Res(R.string.composer_prepare_failed)
        } finally {
            staging = false
        }
    }

    /**
     * Отправляет подготовленные файлы или текст. [topicId] — тема группы с темами, 0 для остальных чатов.
     * Отправка идёт в [scope], переживает поворот экрана и не запускается дважды.
     */
    fun send(
        chatId: Long,
        topicId: Long,
        scope: CoroutineScope,
        sendText: suspend (chatId: Long, topicId: Long, text: String) -> Result<Unit>,
        sendMedia: suspend (chatId: Long, topicId: Long, media: List<OutgoingMedia>, caption: String) -> Result<Unit>
    ) {
        if (sending) return
        sending = true
        error = null
        scope.launch {
            try {
                // Все файлы убраны пользователем — отправляем только текст.
                val result = if (staged.isNotEmpty()) sendStaged(chatId, topicId, sendMedia) else sendText(chatId, topicId, draft.trim())
                result
                    .onSuccess { sentChatId = chatId; sent = true }
                    .onFailure {
                        error = (it as? UserMessageException)?.text ?: UiText.Res(R.string.share_send_failed)
                        // Экран уже закрыт: повторить отправку будет некому, копии не должны остаться в папке.
                        val leftover = staged
                        if (released) importer?.let { staging -> scope.launch { leftover.forEach { staging.discard(it) } } }
                    }
            } finally {
                sending = false
            }
        }
    }

    // Файлы уходят по одному, а группы из 2–10 фото и видео — альбомом. Подпись уходит с первой отправленной частью.
    private suspend fun sendStaged(
        chatId: Long,
        topicId: Long,
        sendMedia: suspend (chatId: Long, topicId: Long, media: List<OutgoingMedia>, caption: String) -> Result<Unit>
    ): Result<Unit> {
        val album = staged.size in 2..IncomingShareParser.MAX_FILES && staged.all { it.kind == MediaKind.Photo || it.kind == MediaKind.Video }
        val batches = if (album) listOf(staged) else staged.map { listOf(it) }
        for (batch in batches) {
            val result = sendMedia(chatId, topicId, batch, draft.trim())
            if (result.isFailure) return result
            staged = staged.filterNot { item -> batch.any { it.path == item.path } }
            draft = ""
        }
        return Result.success(Unit)
    }

    fun removeStaged(item: OutgoingMedia, importer: OutgoingMediaImporter, scope: CoroutineScope) {
        staged = staged.filterNot { it.path == item.path }
        scope.launch { importer.discard(item) }
    }

    /** Экран закрыт без отправки: непосланные копии удаляем, отправку, которая идёт, не трогаем. */
    fun release(context: Context, scope: CoroutineScope) {
        released = true
        if (sending) return
        discard(OutgoingMediaImporter(context), scope, staged)
        staged = emptyList()
    }

    private fun discard(importer: OutgoingMediaImporter, scope: CoroutineScope, items: List<OutgoingMedia>) {
        scope.launch { items.forEach { importer.discard(it) } }
    }

    private companion object {
        fun isPhotoOrVideo(context: Context, uri: Uri): Boolean {
            val mime = context.contentResolver.getType(uri).orEmpty()
            return mime.startsWith("image/") || mime.startsWith("video/")
        }
    }
}

/** Единственный активный запрос «Поделиться»: держит его состояние между поворотами экрана. */
internal object ShareSessions {
    private var current: ShareSession? = null

    fun of(context: Context, requestId: Long, uris: List<Uri>, text: String): ShareSession {
        current?.takeIf { it.requestId == requestId }?.let { return it }
        // Пришёл новый запрос: копии старого, если он не отправлен, больше не нужны.
        current?.release(context.applicationContext, scopeOf(context))
        return ShareSession(requestId, uris, text).also { current = it }
    }

    fun release(context: Context, requestId: Long) {
        val session = current?.takeIf { it.requestId == requestId } ?: return
        current = null
        session.release(context.applicationContext, scopeOf(context))
    }

    private fun scopeOf(context: Context) = (context.applicationContext as com.beykus.beygram.BeyGramApplication).applicationScope
}
