package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.data.export.CHAT_EXPORT_MAX_SINGLE_DOWNLOAD_BYTES
import com.beykus.beygram.data.export.CHAT_EXPORT_MAX_TOTAL_DOWNLOAD_BYTES
import com.beykus.beygram.data.export.ChatExportChat
import com.beykus.beygram.data.export.ChatExportMedia
import com.beykus.beygram.data.export.ChatExportMediaProvider
import com.beykus.beygram.data.export.ChatExportMessage
import com.beykus.beygram.data.export.ChatExportPage
import com.beykus.beygram.data.export.ChatExportPageSource
import com.beykus.beygram.data.export.ChatExportStreamer
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.ChatExportProgress
import com.beykus.beygram.domain.model.ChatExportRequest
import com.beykus.beygram.domain.model.ChatExportSummary
import com.beykus.beygram.domain.model.ChatExportTarget
import com.beykus.beygram.domain.model.MediaKind
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

/**
 * TDLib-backed chat export. The chat type and protected-content flag are checked before the target is
 * opened, and only this repository's client is used, so an export can never read another account's history.
 */
internal class TdChatExporter(
    private val requests: TdRequests,
    private val map: (TdApi.Message) -> ChatExportMessage
) {
    suspend fun export(
        chatId: Long,
        target: ChatExportTarget,
        request: ChatExportRequest,
        onProgress: (ChatExportProgress) -> Unit
    ): Result<ChatExportSummary> = withContext(Dispatchers.IO) {
        requestResult {
            var streamerStarted = false
            try {
                if (chatId == 0L) throw UserMessageException(UiText.Res(R.string.err_chat_export_failed))
                if (!request.hasValidDateRange) throw UserMessageException(UiText.Res(R.string.err_chat_export_date_range))
                val chat = requests(TdApi.GetChat(chatId))
                if (chat.type is TdApi.ChatTypeSecret) {
                    throw UserMessageException(UiText.Res(R.string.err_chat_export_secret_chat))
                }
                if (chat.hasProtectedContent) {
                    throw UserMessageException(UiText.Res(R.string.err_chat_export_protected_chat))
                }
                val totalMessages = if (request.hasDateRange) {
                    null
                } else {
                    try {
                        requests(TdApi.GetChatMessageCount(chatId, null, null, false)).count.takeIf { it > 0 }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }
                streamerStarted = true
                ChatExportStreamer(
                    target = target,
                    source = TdChatExportPageSource(requests, chatId, map),
                    mediaProvider = TdChatExportMediaProvider(requests)
                ).export(
                    chat = ChatExportChat(chat.id, chat.title.ifBlank { "Chat" }, chat.exportKind()),
                    request = request,
                    onProgress = onProgress,
                    totalMessages = totalMessages
                )
            } catch (cancelled: CancellationException) {
                if (!streamerStarted) withContext(NonCancellable) { runCatching { target.discard() } }
                throw cancelled
            } catch (error: Exception) {
                // A refusal before the streamer takes ownership must not leave an empty document from CreateDocument.
                // Once the streamer is running it performs its own NonCancellable cleanup.
                if (!streamerStarted) withContext(NonCancellable) { runCatching { target.discard() } }
                throw error
            }
        }
    }
}

private fun TdApi.Chat.exportKind(): String = when (val chatType = type) {
    is TdApi.ChatTypePrivate -> "private"
    is TdApi.ChatTypeSecret -> "secret"
    is TdApi.ChatTypeBasicGroup -> "group"
    is TdApi.ChatTypeSupergroup -> if (chatType.isChannel) "channel" else "group"
    else -> "unknown"
}

/** Pages newest to oldest with the bounded GetChatHistory window; the page's last id is the next cursor. */
internal class TdChatExportPageSource(
    private val requests: TdRequests,
    private val chatId: Long,
    private val map: (TdApi.Message) -> ChatExportMessage
) : ChatExportPageSource {
    override suspend fun page(beforeMessageId: Long, limit: Int): ChatExportPage {
        val result = requests(TdApi.GetChatHistory(chatId, beforeMessageId, 0, limit, false))
        val raw = result.messages.orEmpty().filterNotNull()
            .filter { beforeMessageId == 0L || it.id != beforeMessageId }
            .sortedByDescending { it.id }
        return ChatExportPage(
            messages = raw.map { map(it) },
            nextBeforeMessageId = raw.lastOrNull()?.id ?: 0L,
            hasMore = raw.isNotEmpty()
        )
    }
}

/** Opens already downloaded files and downloads small missing ones within per-file and total limits. */
internal class TdChatExportMediaProvider(
    private val requests: TdRequests
) : ChatExportMediaProvider {
    private var downloadedBytes = 0L

    override suspend fun open(media: ChatExportMedia): InputStream? {
        val file = requests(TdApi.GetFile(media.fileId), DOWNLOAD_REQUEST_TIMEOUT_MS)
        file.local?.takeIf { it.isDownloadingCompleted }?.path?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { FileInputStream(path) }.getOrNull()?.let { return it }
        }

        val local = file.local ?: return null
        if (!local.canBeDownloaded && !local.isDownloadingActive) return null
        val size = maxOf(file.size, file.expectedSize, media.size)
        if (size <= 0L ||
            size > CHAT_EXPORT_MAX_SINGLE_DOWNLOAD_BYTES ||
            downloadedBytes + size > CHAT_EXPORT_MAX_TOTAL_DOWNLOAD_BYTES
        ) return null

        // Reserve the budget before starting the transfer: a timed-out download must not let later files
        // silently exceed the documented total. The counter intentionally stays charged on failure.
        downloadedBytes += size
        val startedDownload = !local.isDownloadingActive
        var completed = false
        try {
            if (startedDownload) {
                requests(TdApi.DownloadFile(file.id, 16, 0, 0, false), DOWNLOAD_REQUEST_TIMEOUT_MS)
            }
            val deadline = System.currentTimeMillis() + MEDIA_DOWNLOAD_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                delay(250)
                currentCoroutineContext().ensureActive()
                val current = requests(TdApi.GetFile(file.id), DOWNLOAD_REQUEST_TIMEOUT_MS)
                val currentLocal = current.local ?: return null
                if (currentLocal.isDownloadingCompleted) {
                    val path = currentLocal.path
                    if (!path.isNullOrBlank()) {
                        val stream = runCatching { FileInputStream(path) }.getOrNull()
                        if (stream != null) {
                            completed = true
                            return stream
                        }
                    }
                    return null
                }
                if (!currentLocal.isDownloadingActive && !currentLocal.canBeDownloaded) return null
            }
            return null
        } finally {
            if (!completed && startedDownload) {
                runCatching {
                    requests(TdApi.CancelDownloadFile(file.id, true), DOWNLOAD_REQUEST_TIMEOUT_MS)
                }
            }
        }
    }

    private companion object {
        const val DOWNLOAD_REQUEST_TIMEOUT_MS = 10_000L
        const val MEDIA_DOWNLOAD_TIMEOUT_MS = 90_000L
    }
}

/** The one content file that belongs to a message; link previews and thumbnails are not exported. */
internal fun TdApi.MessageContent.toChatExportMedia(): ChatExportMedia? = when (this) {
    is TdApi.MessagePhoto -> {
        val largest = photo.sizes.orEmpty()
            .filter { it.photo != null }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: return null
        val file = largest.photo ?: return null
        ChatExportMedia(
            kind = MediaKind.Photo,
            fileId = file.id,
            fileName = "",
            mimeType = "image/jpeg",
            size = file.mediaSize(),
            width = largest.width,
            height = largest.height
        )
    }
    is TdApi.MessageVideo -> video.video?.let { file ->
        ChatExportMedia(
            kind = MediaKind.Video,
            fileId = file.id,
            fileName = video.fileName.orEmpty(),
            mimeType = video.mimeType.orEmpty().ifBlank { "video/mp4" },
            size = file.mediaSize(),
            durationSeconds = video.duration,
            width = video.width,
            height = video.height
        )
    }
    is TdApi.MessageAnimation -> animation.animation?.let { file ->
        ChatExportMedia(
            kind = MediaKind.Animation,
            fileId = file.id,
            fileName = animation.fileName.orEmpty(),
            mimeType = animation.mimeType.orEmpty().ifBlank { "video/mp4" },
            size = file.mediaSize(),
            durationSeconds = animation.duration,
            width = animation.width,
            height = animation.height
        )
    }
    is TdApi.MessageAudio -> audio.audio?.let { file ->
        ChatExportMedia(
            kind = MediaKind.Audio,
            fileId = file.id,
            fileName = audio.fileName.orEmpty().ifBlank { audio.title.orEmpty() },
            mimeType = audio.mimeType.orEmpty().ifBlank { "audio/mpeg" },
            size = file.mediaSize(),
            durationSeconds = audio.duration
        )
    }
    is TdApi.MessageDocument -> document.document?.let { file ->
        ChatExportMedia(
            kind = MediaKind.Document,
            fileId = file.id,
            fileName = document.fileName.orEmpty(),
            mimeType = document.mimeType.orEmpty().ifBlank { "application/octet-stream" },
            size = file.mediaSize()
        )
    }
    is TdApi.MessageVoiceNote -> voiceNote.voice?.let { file ->
        ChatExportMedia(
            kind = MediaKind.Voice,
            fileId = file.id,
            fileName = "",
            mimeType = voiceNote.mimeType.orEmpty().ifBlank { "audio/ogg" },
            size = file.mediaSize(),
            durationSeconds = voiceNote.duration
        )
    }
    is TdApi.MessageVideoNote -> videoNote.video?.let { file ->
        ChatExportMedia(
            kind = MediaKind.VideoNote,
            fileId = file.id,
            fileName = "",
            mimeType = "video/mp4",
            size = file.mediaSize(),
            durationSeconds = videoNote.duration,
            width = videoNote.length,
            height = videoNote.length
        )
    }
    is TdApi.MessageSticker -> sticker.sticker?.let { file ->
        ChatExportMedia(
            kind = MediaKind.Sticker,
            fileId = file.id,
            fileName = "",
            mimeType = stickerMimeType(sticker.format),
            size = file.mediaSize(),
            width = sticker.width,
            height = sticker.height
        )
    }
    else -> null
}

private fun TdApi.File.mediaSize(): Long = maxOf(size, expectedSize)
