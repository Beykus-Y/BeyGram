package com.beykus.beygram.domain.repository

import com.beykus.beygram.domain.model.*
import kotlinx.coroutines.flow.StateFlow

/** Conversation operations; UI never sends native TDLib requests or chooses protocol flags. */
interface ConversationRepository {
    val messageLimits: StateFlow<MessageLimits>
    fun observeChatHistory(chatId: Long): StateFlow<ChatHistory>
    fun openChat(chatId: Long)
    fun closeChat(chatId: Long)
    fun loadOlderMessages(chatId: Long)
    suspend fun sendMessage(chatId: Long, text: String, replyToMessageId: Long? = null): Result<Unit>
    suspend fun sendMedia(chatId: Long, media: OutgoingMedia, caption: String, replyToMessageId: Long? = null): Result<Unit>
    suspend fun messageCapabilities(chatId: Long, messageId: Long): Result<MessageCapabilities>
    suspend fun editMessage(chatId: Long, messageId: Long, text: String): Result<Unit>
    suspend fun deleteMessage(chatId: Long, messageId: Long, scope: DeleteMessageScope): Result<Unit>
    suspend fun messageContext(chatId: Long, messageId: Long): Result<MessageContext>
    fun messagesVisible(chatId: Long, messageIds: List<Long>)
    fun setAppForeground(foreground: Boolean)
    fun refreshInteractionPolicy()
    fun openMessageContent(chatId: Long, messageId: Long)
}
