package com.beykus.beygram.ui.screens.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.ui.screens.chat.ForwardChatDialog
import com.beykus.beygram.ui.screens.chat.ForwardTarget
import java.io.File

/**
 * A share picked from the player. The track, the timecode and the chat source are fixed when the button is tapped, so
 * a track change in the player neither drops the picker nor sends a different track.
 */
internal data class PendingTrackShare(val item: PlaybackItem, val caption: String, val source: PlaybackRepositorySource)

/** A playback position as the caption of a shared track: m:ss, or h:mm:ss from one hour on. */
internal fun shareTimecode(positionMs: Long): String {
    val total = positionMs.coerceAtLeast(0L) / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/**
 * Only a complete local music file is sent. A track from a secret chat, a view-once message or a chat that protects
 * its content from copying is never offered. [originUnprotected] is true only when the track's chat is known not to
 * protect its content, so a chat that cannot be checked here does not get a share either.
 */
internal fun canShareTrack(item: PlaybackItem, originUnprotected: Boolean): Boolean =
    item.isMusic && item.path.isNotBlank() && item.growing == null &&
        !item.secret && !item.isSecretChat && !item.viewOnce && originUnprotected

/**
 * The track as it is on disk now. A growing source that has finished downloading is replaced by its completed file, so
 * an Ogg/Opus track that started before its download ended can still be shared. Anything still downloading is returned
 * unchanged and fails [canShareTrack].
 */
internal fun completedShareItem(item: PlaybackItem): PlaybackItem {
    val growing = item.growing ?: return item
    if (!growing.isComplete()) return item
    val completed = growing.completedPath()?.takeIf { it.isNotBlank() } ?: return item
    return item.copy(path = completed, growing = null)
}

/** Chats that may receive a shared track: writable, and neither secret nor protecting its content. */
internal fun shareTargetChats(chats: List<ChatPreview>): List<ChatPreview> =
    chats.filter { it.canPost && !it.isSecret && !it.hasProtectedContent }.distinctBy { it.id }

/** The chat a track came from as it is now; null when the track is not from a chat or that chat is not loaded. */
internal fun originChatNow(repository: TelegramRepository, item: PlaybackItem): ChatPreview? =
    item.origin?.let { repository.observeChat(it.chatId).value }

/** The track as an ordinary audio message, sent from the local file the player already plays. */
internal fun trackOutgoingMedia(item: PlaybackItem): OutgoingMedia {
    val file = File(item.path)
    return OutgoingMedia(
        path = item.path,
        fileName = file.name,
        mimeType = item.mimeType.ifBlank { "audio/mpeg" },
        kind = MediaKind.Audio,
        durationSeconds = item.durationSeconds,
        size = item.sizeBytes.takeIf { it > 0L } ?: file.length(),
        title = item.trackTitle,
        performer = item.performer
    )
}

/**
 * Picks chats of [repository], the account the player's chat source belongs to, and sends the track as an audio file
 * with [caption], the timecode. The forward picker runs in copy mode, so the recipient gets a new message rather than a
 * forward. A chat that refuses the file stays selected for a retry.
 *
 * Protection and the chat list can change while the picker is open, so the track and every chosen chat are checked
 * again right before the upload. A chosen chat that no longer qualifies stays selected and counts as failed.
 */
@Composable
internal fun ShareTrackDialog(repository: TelegramRepository, item: PlaybackItem, caption: String, onClose: () -> Unit) {
    val chats by repository.chats.collectAsStateWithLifecycle()
    val archivedChats by repository.archivedChats.collectAsStateWithLifecycle()
    val failedText = stringResource(R.string.player_share_failed)
    var sending by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    ForwardChatDialog(
        chats = shareTargetChats(chats + archivedChats),
        busy = sending,
        error = failure,
        observeFile = repository::observeFile,
        downloadFile = repository::downloadFile,
        asCopy = true,
        allowComment = false,
        loadTopics = { chatId -> repository.forumTopics(chatId) },
        onSend = { targets, _ ->
            sending = true
            failure = null
            val shareItem = completedShareItem(item)
            val trackAllowed = canShareTrack(
                shareItem,
                originUnprotected = item.origin == null || originChatNow(repository, item)?.hasProtectedContent == false
            )
            val allowedChats = shareTargetChats(repository.chats.value + repository.archivedChats.value)
                .map { it.id }
                .toSet()
            val media = if (trackAllowed) trackOutgoingMedia(shareItem) else null
            val failed = mutableListOf<ForwardTarget>()
            for (target in targets) {
                val sent = media != null && target.chatId in allowedChats &&
                    repository.sendMedia(target.chatId, media, caption, forumTopicId = target.topicId).isSuccess
                if (!sent) failed += target
            }
            sending = false
            if (failed.isNotEmpty()) failure = failedText
            failed
        },
        onClose = onClose
    )
}
