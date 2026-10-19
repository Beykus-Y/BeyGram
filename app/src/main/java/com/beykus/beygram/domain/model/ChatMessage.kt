package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText

data class ChatMessage(
    val id: Long,
    val chatId: Long,
    val text: String,
    val time: String,
    val isOutgoing: Boolean,
    val dateSeconds: Long = 0L,
    val sendState: MessageSendState = MessageSendState.Sent,
    val media: MessageMedia? = null,
    /** Telegram's mediaAlbumId; zero means this message is not part of an album. */
    val mediaAlbumId: Long = 0L,
    val senderName: String = "",
    val reply: MessageReply? = null,
    val entities: List<MessageTextEntity> = emptyList(),
    val inlineKeyboard: List<List<BotButton>> = emptyList(),
    val isEdited: Boolean = false,
    val reactions: List<MessageReaction> = emptyList()
)

/**
 * An emoji reaction under a message. [isChosen] marks the reaction the current user added. A custom emoji reaction has a
 * [customEmojiId] and no emoji text; its sticker is looked up by that id.
 */
data class MessageReaction(val emoji: String, val count: Int, val isChosen: Boolean = false, val customEmojiId: Long = 0L) {
    val option: ReactionOption get() = ReactionOption(emoji, customEmojiId)
}

/** A reaction the user can add: a standard [emoji], or the custom emoji with [customEmojiId], which has no emoji text. */
data class ReactionOption(val emoji: String, val customEmojiId: Long = 0L)

/** A custom emoji sticker: its file, the sticker's [mimeType] as for other stickers, and the standard [emoji] it stands for. */
data class CustomEmoji(val customEmojiId: Long, val fileId: Int, val emoji: String, val mimeType: String)

/** The message that carries an album's reactions: the first one that has any, otherwise the latest. A single message is its own. */
fun reactionMessage(album: List<ChatMessage>): ChatMessage {
    val chronological = album.sortedWith(compareBy<ChatMessage> { it.dateSeconds }.thenBy { it.id })
    return chronological.firstOrNull { it.reactions.isNotEmpty() } ?: chronological.last()
}

enum class MessageSendState { Sending, Sent, Failed }

data class ChatHistory(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val hasOlderMessages: Boolean = true,
    /** True while the messages are a window around a jumped-to message, so newer messages are not loaded yet. */
    val hasNewerMessages: Boolean = false,
    val error: UiText? = null,
    val lastReadInboxMessageId: Long = 0L,
    val lastReadOutboxMessageId: Long = 0L
)

data class MessageReply(val chatId: Long, val messageId: Long, val text: UiText, val senderName: String = "", val isUnavailable: Boolean = false)
data class MessageCapabilities(val canReply: Boolean = false, val canCopy: Boolean = false, val canEdit: Boolean = false, val canDeleteForSelf: Boolean = false, val canDeleteForAll: Boolean = false, val canForward: Boolean = false, val canSave: Boolean = false)
enum class DeleteMessageScope { ForSelf, ForEveryone }
data class MessageContext(val chatId: Long, val title: String, val targetId: Long, val messages: List<ChatMessage>)
data class MessageSearchPage(val messages: List<ChatMessage>, val nextFromMessageId: Long)

/** Страница общего поиска по всем чатам аккаунта. [nextOffset] пустой, когда страницы кончились. */
data class GlobalMessagePage(val messages: List<ChatMessage>, val nextOffset: String)
data class MessageLimits(val text: Int = 4096, val caption: Int = 1024)

data class PinnedMessagePage(val messages: List<ChatMessage>, val totalCount: Int, val nextFromMessageId: Long)
