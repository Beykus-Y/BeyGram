package com.beykus.beygram.domain.model

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
    val isEdited: Boolean = false
)

enum class MessageSendState { Sending, Sent, Failed }

data class ChatHistory(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val hasOlderMessages: Boolean = true,
    val error: String? = null,
    val lastReadInboxMessageId: Long = 0L,
    val lastReadOutboxMessageId: Long = 0L
)

data class MessageReply(val chatId: Long, val messageId: Long, val text: String = "Сообщение", val senderName: String = "", val isUnavailable: Boolean = false)
data class MessageCapabilities(val canReply: Boolean = false, val canCopy: Boolean = false, val canEdit: Boolean = false, val canDeleteForSelf: Boolean = false, val canDeleteForAll: Boolean = false, val canForward: Boolean = false, val canSave: Boolean = false)
enum class DeleteMessageScope { ForSelf, ForEveryone }
data class MessageContext(val chatId: Long, val title: String, val targetId: Long, val messages: List<ChatMessage>)
data class MessageSearchPage(val messages: List<ChatMessage>, val nextFromMessageId: Long)
data class MessageLimits(val text: Int = 4096, val caption: Int = 1024)
