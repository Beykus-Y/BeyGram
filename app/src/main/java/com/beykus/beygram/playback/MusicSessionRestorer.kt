package com.beykus.beygram.playback

import android.content.Context
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.repository.TelegramRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Rehydrates each persisted origin from the *current* TDLib account, never from stale file paths or file IDs.
 * A missing/deleted/protected message is dropped. The original selection must still exist or nothing is restored.
 */
suspend fun restoreMusicSession(
    context: Context,
    repository: TelegramRepository,
    saved: SavedMusicSession
): Boolean {
    if (PlaybackEngine.state.value.item != null) return false
    val semaphore = Semaphore(3)
    val verified = coroutineScope {
        saved.origins.map { origin ->
            async {
                semaphore.withPermit {
                    val chat = repository.observeChat(origin.chatId).value
                        ?: run {
                            repository.loadChat(origin.chatId)
                            repository.observeChat(origin.chatId).value
                        }
                    if (chat == null || chat.isSecret || chat.hasProtectedContent) return@withPermit null
                    val message = repository.messageContext(origin.chatId, origin.messageId)
                        .getOrNull()?.messages?.firstOrNull { it.chatId == origin.chatId && it.id == origin.messageId }
                        ?: return@withPermit null
                    val media = message.media ?: return@withPermit null
                    if (media.kind != MediaKind.Audio || media.isSecret || media.isSecretChat ||
                        message.isViewOnce || message.sendState != MessageSendState.Sent
                    ) return@withPermit null
                    val file = repository.observeFile(media.fileId).value
                    media.playbackItem(
                        file.localPath.orEmpty(),
                        media.fileName.ifBlank { "Audio" },
                        origin,
                        accountId = saved.accountId,
                        sizeBytes = file.size
                    )
                }
            }
        }.awaitAll()
    }
    val selected = verified.getOrNull(saved.currentIndex) ?: return false
    val queue = verified.filterNotNull()
    val index = queue.indexOfFirst { it.key == selected.key }
    if (index < 0) return false
    PlaybackEngine.restoreQueue(
        context,
        queue,
        index,
        PlaybackQueueSource(
            saved.accountId, repository::observeFile, repository::downloadFile, repository::autoDownloadFile
        ),
        saved.positionMs,
        saved.shuffle,
        saved.repeat
    )
    return PlaybackEngine.state.value.item?.key == selected.key &&
        PlaybackEngine.state.value.playing == false && PlaybackEngine.state.value.intentPlaying == false
}
