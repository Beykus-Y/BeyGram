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

/** Chats that may receive a shared track: writable, and neither secret nor protecting its content. */
internal fun shareTargetChats(chats: List<ChatPreview>): List<ChatPreview> =
    chats.filter { it.canPost && !it.isSecret && !it.hasProtectedContent }.distinctBy { it.id }

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
            val media = trackOutgoingMedia(item)
            val failed = mutableListOf<ForwardTarget>()
            for (target in targets) {
                if (repository.sendMedia(target.chatId, media, caption, forumTopicId = target.topicId).isFailure) {
                    failed += target
                }
            }
            sending = false
            if (failed.isNotEmpty()) failure = failedText
            failed
        },
        onClose = onClose
    )
}
