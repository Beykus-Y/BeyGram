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
    /** Replaces the loaded history with messages around [messageId]; newer messages load on [loadLatestMessages]. False when a newer jump or latest load superseded it. */
    suspend fun loadMessagesAround(chatId: Long, messageId: Long): Result<Boolean>
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
    /** Emoji the current user can add to the message, in Telegram's order. Empty where reactions are not allowed. */
    suspend fun availableReactions(chatId: Long, messageId: Long): Result<List<String>>
    /** Adds [emoji], or removes it when the user has already chosen it. Returns the message's reactions after the change. */
    suspend fun toggleReaction(chatId: Long, messageId: Long, emoji: String): Result<List<MessageReaction>>
    suspend fun messageContext(chatId: Long, messageId: Long): Result<MessageContext>
    /** Messages in one chat whose text matches [query]. Pass 0 for the first page, then the previous page's nextFromMessageId. */
    suspend fun searchMessages(chatId: Long, query: String, fromMessageId: Long): Result<MessageSearchPage>
    /** Messages with an attachment of [kind] for the chat profile tabs, newest first. Same paging as [searchMessages]. */
    suspend fun sharedMessages(chatId: Long, kind: SharedMediaKind, fromMessageId: Long): Result<MessageSearchPage>
    fun messagesVisible(chatId: Long, messageIds: List<Long>)
    fun setAppForeground(foreground: Boolean)
    fun refreshInteractionPolicy()
    fun openMessageContent(chatId: Long, messageId: Long)
    /** Names of the people typing in the chat right now; empty when nobody is. A blank name means the sender's name is unknown. */
    fun observeTyping(chatId: Long): StateFlow<List<String>>
    /**
     * Shows [activity] to the chat's other members until [endChatActivity] or another activity replaces it.
     * Telegram drops an activity after a few seconds, so it is sent again while it lasts. Typing also stops on its own
     * a few seconds after the last call, so typing calls come from each keystroke.
     */
    fun startChatActivity(chatId: Long, activity: ChatActivity)
    /** Ends [activity] if it is the one being shown. */
    fun endChatActivity(chatId: Long, activity: ChatActivity)
}
