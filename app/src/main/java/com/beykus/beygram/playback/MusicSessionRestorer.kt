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
    val verified = resolveMusicOrigins(repository, saved.accountId, saved.origins)
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

/**
 * Resolves Telegram origins against the account's *current* TDLib state, with at most six lookups in flight.
 * The result keeps the input positions: a null slot means the message is gone, protected, secret, not audio, or not sent.
 * Callers decide what a gap means; nothing is dropped silently here. An origin that belongs to another account is never
 * looked up in this repository.
 */
suspend fun resolveMusicOrigins(
    repository: TelegramRepository,
    accountId: String,
    origins: List<PlaybackOrigin?>
): List<PlaybackItem?> {
    val semaphore = Semaphore(6)
    return coroutineScope {
        origins.map { origin ->
            async {
                semaphore.withPermit { resolveMusicOrigin(repository, accountId, origin) }
            }
        }.awaitAll()
    }
}

private suspend fun resolveMusicOrigin(
    repository: TelegramRepository,
    accountId: String,
    origin: PlaybackOrigin?
): PlaybackItem? {
    if (origin == null || origin.accountId != accountId) return null
    val chat = repository.observeChat(origin.chatId).value
        ?: run {
            repository.loadChat(origin.chatId)
            repository.observeChat(origin.chatId).value
        }
    if (chat == null || chat.isSecret || chat.hasProtectedContent) return null
    // Only the message is needed. A context page here cost three TDLib requests and 40 mapped messages per track,
    // which held a whole playlist start for seconds.
    val message = repository.singleMessage(origin.chatId, origin.messageId).getOrNull()
        ?.takeIf { it.chatId == origin.chatId && it.id == origin.messageId }
        ?: return null
    val media = message.media ?: return null
    if (media.kind != MediaKind.Audio || media.isSecret || media.isSecretChat ||
        message.isViewOnce || message.sendState != MessageSendState.Sent
    ) return null
    val file = repository.observeFile(media.fileId).value
    return media.playbackItem(
        file.localPath.orEmpty(),
        media.fileName.ifBlank { "Audio" },
        origin,
        accountId = accountId,
        sizeBytes = file.size
    )
}
