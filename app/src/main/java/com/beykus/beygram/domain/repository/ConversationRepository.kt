package com.beykus.beygram.domain.repository

import com.beykus.beygram.domain.model.*
import kotlinx.coroutines.flow.StateFlow

/** Conversation operations; UI never sends native TDLib requests or chooses protocol flags. */
interface ConversationRepository {
    fun observePinnedMessagesVersion(chatId: Long): StateFlow<Long>
    suspend fun pinnedMessages(chatId: Long, fromMessageId: Long = 0L): Result<PinnedMessagePage>
    fun observeBotKeyboard(chatId: Long): StateFlow<BotKeyboard?>
    suspend fun dismissBotKeyboard(chatId: Long, messageId: Long): Result<Unit>
    suspend fun botCallback(chatId: Long, messageId: Long, data: List<Byte>): Result<BotCallbackAnswer>
    val messageLimits: StateFlow<MessageLimits>
    fun observeChatHistory(chatId: Long): StateFlow<ChatHistory>
    fun openChat(chatId: Long)
    fun closeChat(chatId: Long)
    fun loadOlderMessages(chatId: Long)
    /** Replaces the loaded history with messages around [messageId]; newer messages load on [loadLatestMessages]. */
    suspend fun loadMessagesAround(chatId: Long, messageId: Long): Result<Unit>
    fun loadLatestMessages(chatId: Long)
    suspend fun sendMessage(chatId: Long, text: String, replyToMessageId: Long? = null, entities: List<MessageTextEntity> = emptyList()): Result<Unit>
    suspend fun sendMedia(chatId: Long, media: OutgoingMedia, caption: String, replyToMessageId: Long? = null, entities: List<MessageTextEntity> = emptyList()): Result<Unit>
    suspend fun sendMediaAlbum(chatId: Long, media: List<OutgoingMedia>, caption: String, replyToMessageId: Long? = null, entities: List<MessageTextEntity> = emptyList()): Result<Unit>
    /** Retries failed outgoing messages regardless of their content type. */
    suspend fun retryMessages(chatId: Long, messageIds: List<Long>): Result<Unit>
    suspend fun messageCapabilities(chatId: Long, messageId: Long): Result<MessageCapabilities>
    /** Place among the chat's attachments of the same kind; TDLib counts can be approximate in large chats. */
    suspend fun mediaPosition(chatId: Long, messageId: Long, kind: MediaKind): Result<MediaPosition>
    suspend fun editMessage(chatId: Long, messageId: Long, text: String, entities: List<MessageTextEntity> = emptyList()): Result<Unit>
    suspend fun deleteMessage(chatId: Long, messageId: Long, scope: DeleteMessageScope): Result<Unit>
    /** Regular forward (the original sender stays visible). TDLib refuses it when the message can't be forwarded. */
    suspend fun forwardMessage(fromChatId: Long, messageId: Long, toChatId: Long): Result<Unit>
    suspend fun messageContext(chatId: Long, messageId: Long): Result<MessageContext>
    /** Messages in one chat whose text matches [query]. Pass 0 for the first page, then the previous page's nextFromMessageId. */
    suspend fun searchMessages(chatId: Long, query: String, fromMessageId: Long): Result<MessageSearchPage>
    fun messagesVisible(chatId: Long, messageIds: List<Long>)
    fun setAppForeground(foreground: Boolean)
    fun refreshInteractionPolicy()
    fun openMessageContent(chatId: Long, messageId: Long)
    /** Names of the people typing in the chat right now; empty when nobody is. A blank name means the sender's name is unknown. */
    fun observeTyping(chatId: Long): StateFlow<List<String>>
}
