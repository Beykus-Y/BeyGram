package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.playbackItem

/** Converts an actual TDLib message into an account-scoped, lazy music occurrence. */
internal fun chatMusicItem(
    message: ChatMessage,
    accountId: String,
    file: MediaFileState,
    fallbackTitle: String
): PlaybackItem? {
    val media = message.media ?: return null
    if (message.sendState != MessageSendState.Sent || message.isViewOnce ||
        media.kind != MediaKind.Audio || media.isSecret || media.isSecretChat
    ) return null
    return media.playbackItem(
        file.localPath.orEmpty(),
        fallbackTitle,
        PlaybackOrigin(message.chatId, message.id, accountId),
        accountId = accountId,
        sizeBytes = file.size
    )
}

/** The newest message is first, so Next traverses older songs in the same chat. */
internal fun orderedChatMusic(
    messages: List<ChatMessage>,
    accountId: String,
    file: (Int) -> MediaFileState,
    fallbackTitle: String
): List<PlaybackItem> = messages.sortedByDescending { it.id }
    .mapNotNull { message ->
        val fileId = message.media?.takeIf { it.kind == MediaKind.Audio }?.fileId ?: return@mapNotNull null
        chatMusicItem(message, accountId, file(fileId), fallbackTitle)
    }
    .distinctBy { it.key }
