package com.beykus.beygram.ui.screens.music

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.playback.PlaybackOrigin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Cover file ids of library tracks, by message. A row that comes back on screen does not look its message up again.
 * [NO_COVER] marks a message that has no cover or could not be read.
 */
private object TrackCoverIds {
    const val NO_COVER = -1
    private val ids = LruCache<String, Int>(512)

    fun key(origin: PlaybackOrigin): String = "${origin.accountId}:${origin.chatId}:${origin.messageId}"

    fun get(origin: PlaybackOrigin): Int? = ids.get(key(origin))

    fun put(origin: PlaybackOrigin, fileId: Int) {
        ids.put(key(origin), fileId)
    }
}

/**
 * The local cover picture of a Telegram library track, or null while there is none. The cover is the small album
 * picture Telegram keeps next to the audio; it is downloaded when a row asks for it and then shown from disk. Tracks
 * of secret or protected chats and of another account never get a cover here.
 */
@Composable
internal fun rememberTelegramTrackCover(
    telegram: TelegramRepository,
    accountId: String,
    origin: PlaybackOrigin?
): String? {
    val path by produceState<String?>(null, telegram, accountId, origin) {
        value = null
        if (origin == null || origin.accountId != accountId) return@produceState
        val fileId = TrackCoverIds.get(origin) ?: lookUpCoverId(telegram, origin).also { TrackCoverIds.put(origin, it) }
        if (fileId == TrackCoverIds.NO_COVER) return@produceState
        val file = telegram.observeFile(fileId)
        file.value.localPath?.takeIf { it.isNotBlank() }?.let {
            value = it
            return@produceState
        }
        telegram.downloadFile(fileId)
        value = file.first { !it.localPath.isNullOrBlank() }.localPath
    }
    return path
}

private suspend fun lookUpCoverId(telegram: TelegramRepository, origin: PlaybackOrigin): Int = try {
    val chat = telegram.observeChat(origin.chatId).value
        ?: run {
            telegram.loadChat(origin.chatId)
            telegram.observeChat(origin.chatId).value
        }
    val message = if (chat == null || chat.isSecret || chat.hasProtectedContent) {
        null
    } else {
        telegram.singleMessage(origin.chatId, origin.messageId).getOrNull()
            ?.takeIf { it.chatId == origin.chatId && it.id == origin.messageId }
    }
    val media = message?.media
    val thumb = media?.thumbnailFileId
    if (media == null || media.kind != MediaKind.Audio || media.isSecret || thumb == null || thumb == media.fileId) {
        TrackCoverIds.NO_COVER
    } else {
        thumb
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    TrackCoverIds.NO_COVER
}
