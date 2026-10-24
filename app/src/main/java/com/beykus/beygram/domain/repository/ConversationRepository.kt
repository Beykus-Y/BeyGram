package com.beykus.beygram.domain.repository

import com.beykus.beygram.domain.model.*
import kotlinx.coroutines.flow.StateFlow

/** Conversation operations; UI never sends native TDLib requests or chooses protocol flags. */
interface ConversationRepository {
    fun observePinnedMessagesVersion(chatId: Long): StateFlow<Long>
    suspend fun pinnedMessages(chatId: Long, fromMessageId: Long = 0L, forumTopicId: Long = 0L): Result<PinnedMessagePage>
    /** Pins or unpins a message in its parent chat; forum messages keep their topic. */
    suspend fun setMessagePinned(chatId: Long, messageId: Long, pinned: Boolean): Result<Unit>
    fun observeBotKeyboard(chatId: Long): StateFlow<BotKeyboard?>
    suspend fun dismissBotKeyboard(chatId: Long, messageId: Long): Result<Unit>
    suspend fun botCallback(chatId: Long, messageId: Long, data: List<Byte>): Result<BotCallbackAnswer>
    val messageLimits: StateFlow<MessageLimits>
    /** Local-first persistent composer drafts, synchronized with TDLib when connected. */
    fun observeMessageDraft(chatId: Long): StateFlow<MessageDraft>
    fun observeForumTopicDraft(chatId: Long, forumTopicId: Long): StateFlow<MessageDraft>
    fun updateForumTopicDraft(chatId: Long, forumTopicId: Long, transform: (MessageDraft) -> MessageDraft)
    /** Atomic updates prevent text/formatting/reply callbacks from overwriting one another. */
    fun updateMessageDraft(chatId: Long, transform: (MessageDraft) -> MessageDraft)
    fun observeChatHistory(chatId: Long): StateFlow<ChatHistory>
    fun observeForumTopicHistory(chatId: Long, forumTopicId: Long): StateFlow<ChatHistory>
    fun loadForumTopicHistory(chatId: Long, forumTopicId: Long)
    fun loadOlderForumTopicMessages(chatId: Long, forumTopicId: Long)
    suspend fun latestForumTopicPreview(chatId: Long, forumTopicId: Long): Result<String?>
    suspend fun loadForumTopicMessagesAround(chatId: Long, forumTopicId: Long, messageId: Long): Result<Boolean>
    /** The linked discussion supergroup of a channel, or zero when the channel has none. */
    suspend fun linkedDiscussionChatId(chatId: Long): Result<Long>
    /**
     * Opens a channel post's comment thread. TDLib resolves the linked discussion chat and thread id; a post with
     * comments disabled or hidden fails with a user-facing explanation instead of an empty thread.
     */
    suspend fun loadMessageThread(chatId: Long, messageId: Long): Result<MessageThread>
    /** One older page of comments, newest first in TDLib and mapped oldest first for the thread list. */
    suspend fun loadMessageThreadComments(discussionChatId: Long, threadId: Long, fromMessageId: Long = 0L): Result<MessageThreadPage>
    /** Sends into the discussion chat's thread, not into the channel; the reply keeps the target comment's thread. */
    suspend fun sendThreadReply(
        discussionChatId: Long,
        threadId: Long,
        text: String,
        entities: List<MessageTextEntity> = emptyList(),
        replyToMessageId: Long? = null
    ): Result<ChatMessage>
    /** Permanent deletions observed in a chat; history windows alone cannot distinguish deleted vs unloaded rows. */
    fun observeDeletedMessages(chatId: Long): StateFlow<Set<Long>>
    fun openChat(chatId: Long)
    fun closeChat(chatId: Long)
    fun loadOlderMessages(chatId: Long)
    /** Replaces the loaded history with messages around [messageId]; newer messages load on [loadLatestMessages]. False when a newer jump or latest load superseded it. */
    suspend fun loadMessagesAround(chatId: Long, messageId: Long): Result<Boolean>
    /** One page of messages newer than [afterMessageId], oldest first; used to walk toward the first unread target. */
    suspend fun newerMessages(chatId: Long, forumTopicId: Long, afterMessageId: Long): Result<List<ChatMessage>>
    fun loadLatestMessages(chatId: Long)
    /** [clearDraft] false оставляет черновик чата нетронутым: нужно для текста из других приложений. */
    suspend fun sendMessage(chatId: Long, text: String, replyToMessageId: Long? = null, entities: List<MessageTextEntity> = emptyList(), forumTopicId: Long = 0L, clearDraft: Boolean = true, options: MessageSendOptions = MessageSendOptions()): Result<Unit>
    suspend fun isForumChat(chatId: Long): Result<Boolean>
    /** Темы группы с темами; null, если чат не группа с темами. Берётся первая страница, до 100 тем. */
    suspend fun forumTopics(chatId: Long, onPage: (List<ForumTopic>) -> Unit = {}): Result<List<ForumTopic>?>
    suspend fun canCreateForumTopic(chatId: Long): Result<Boolean>
    suspend fun createForumTopic(chatId: Long, name: String): Result<Long>
    suspend fun renameForumTopic(chatId: Long, forumTopicId: Long, name: String): Result<Unit>
    suspend fun setForumTopicClosed(chatId: Long, forumTopicId: Long, closed: Boolean): Result<Unit>
    suspend fun setForumTopicPinned(chatId: Long, forumTopicId: Long, pinned: Boolean): Result<Unit>
    suspend fun deleteForumTopic(chatId: Long, forumTopicId: Long): Result<Unit>
    suspend fun sendMedia(chatId: Long, media: OutgoingMedia, caption: String, replyToMessageId: Long? = null, entities: List<MessageTextEntity> = emptyList(), forumTopicId: Long = 0L, options: MessageSendOptions = MessageSendOptions()): Result<Unit>
    suspend fun sendMediaAlbum(chatId: Long, media: List<OutgoingMedia>, caption: String, replyToMessageId: Long? = null, entities: List<MessageTextEntity> = emptyList(), forumTopicId: Long = 0L, options: MessageSendOptions = MessageSendOptions()): Result<Unit>
    /**
     * Sends a sticker as a native InputMessageSticker, not as a document. [sticker] carries the TDLib file id,
     * dimensions and emoji; its bytes are never downloaded by the picker. Reply/topic/scheduling apply as usual.
     */
    suspend fun sendSticker(chatId: Long, sticker: OutgoingMedia, replyToMessageId: Long? = null, forumTopicId: Long = 0L, options: MessageSendOptions = MessageSendOptions()): Result<Unit>
    /** The account's recent sticker list; the first page is bounded by TDLib. */
    suspend fun recentStickers(): Result<List<StickerItem>>
    /** Installed regular sticker sets; opening one fetches that set only when the user asks for it. */
    suspend fun installedStickerSets(): Result<List<StickerSetSummary>>
    suspend fun stickerSet(setId: Long): Result<StickerSetContent>
    /** Sticker text search across sets. Pass 0 first, then [StickerSearchPage.nextOffset]. */
    suspend fun searchStickers(query: String, offset: Int = 0): Result<StickerSearchPage>
    /** GIFs explicitly saved by the user in this Telegram account. */
    suspend fun savedAnimations(): Result<List<AnimationItem>>
    /** GIFs recently seen in this account's chats, newest first. An empty [offset] starts the list. */
    suspend fun recentAnimations(offset: String = ""): Result<AnimationPage>
    /** GIFs found in this account's message history. [offset] comes from the previous page's nextOffset. */
    suspend fun searchAnimations(query: String, offset: String = ""): Result<AnimationPage>
    /** Whether the current account may send premium stickers. */
    suspend fun isPremiumAccount(): Result<Boolean>
    /** Scheduled queue straight from TDLib; no local scheduled-message cache is created. */
    suspend fun scheduledMessages(chatId: Long): Result<List<ChatMessage>>
    /** Cancels one queued message in TDLib. */
    suspend fun deleteScheduledMessage(chatId: Long, messageId: Long): Result<Unit>
    /** Moves a queued message to [atSeconds]; albums are moved together by TDLib. */
    suspend fun rescheduleMessage(chatId: Long, messageId: Long, atSeconds: Long): Result<Unit>
    /** Delivers a queued message immediately through TDLib. */
    suspend fun sendScheduledNow(chatId: Long, messageId: Long): Result<Unit>
    /** Retries failed outgoing messages regardless of their content type. */
    suspend fun retryMessages(chatId: Long, messageIds: List<Long>): Result<Unit>
    suspend fun messageCapabilities(chatId: Long, messageId: Long): Result<MessageCapabilities>
    /** Per-chat notification preferences from TDLib; no local copy is created. */
    suspend fun chatNotificationPreferences(chatId: Long): Result<ChatNotificationPreferences>
    /** Updates only sound/preview/mentions; mute and unrelated TDLib settings are preserved. */
    suspend fun setChatNotificationPreferences(chatId: Long, preferences: ChatNotificationPreferences): Result<Unit>
    /** Notification sounds already saved in this Telegram account. */
    suspend fun savedNotificationSounds(): Result<List<NotificationSoundOption>>
    /** Translates a message with TDLib, preserving its formatting entities. Secret chats are refused locally. */
    suspend fun translateMessage(chatId: Long, messageId: Long, toLanguageCode: String): Result<MessageTranslation>
    /** Starts server-side speech recognition; the result arrives through UpdateMessageContent. Never automatic. */
    suspend fun recognizeSpeech(chatId: Long, messageId: Long): Result<Unit>
    /** Rates the last recognition result as good or bad. Telegram uses it to improve recognition quality. */
    suspend fun rateSpeechRecognition(chatId: Long, messageId: Long, isGood: Boolean): Result<Unit>
    /** Place among the chat's attachments of the same kind; TDLib counts can be approximate in large chats. */
    suspend fun mediaPosition(chatId: Long, messageId: Long, kind: MediaKind): Result<MediaPosition>
    suspend fun editMessage(chatId: Long, messageId: Long, text: String, entities: List<MessageTextEntity> = emptyList()): Result<Unit>
    suspend fun deleteMessage(chatId: Long, messageId: Long, scope: DeleteMessageScope): Result<Unit>
    /** Marks a chat unread without changing its read watermark; the chat list shows an unread marker. */
    suspend fun markChatUnread(chatId: Long): Result<Unit>
    /**
     * Forwards a message. With [asCopy] the original sender is hidden, like Telegram's "Forward without author";
     * TDLib refuses either form when the message can't be forwarded.
     */
    suspend fun forwardMessage(fromChatId: Long, messageId: Long, toChatId: Long, asCopy: Boolean = false): Result<Unit>
    /** A t.me link to [messageId], or a Telegram error when the message has no shareable link. */
    suspend fun messageLink(chatId: Long, messageId: Long, inMessageThread: Boolean = false): Result<String>
    /**
     * Reactions the current user can add to the message, in Telegram's order. Empty where reactions are not allowed. Custom
     * emoji are listed only for users with Telegram Premium.
     */
    suspend fun availableReactions(chatId: Long, messageId: Long): Result<List<ReactionOption>>
    /**
     * Adds [reaction], or removes it when the user has already chosen it. [addToRecent] is set only for a reaction picked from the
     * extended menu, as Telegram requires. Returns the message's reactions after the change.
     */
    suspend fun toggleReaction(chatId: Long, messageId: Long, reaction: ReactionOption, addToRecent: Boolean): Result<List<MessageReaction>>
    /** The custom emoji sticker behind [customEmojiId]. TDLib is asked on first use; the value stays null until it answers. */
    fun observeCustomEmoji(customEmojiId: Long): StateFlow<CustomEmoji?>
    suspend fun messageContext(chatId: Long, messageId: Long): Result<MessageContext>
    /**
     * Messages in one chat whose text matches [query] and whose sender/attachment type match [filter]. Pass 0 for the
     * first page, then the previous page's nextFromMessageId. [filter]'s date range is applied to each returned page
     * without changing that cursor, so a page can be empty while [MessageSearchPage.nextFromMessageId] still allows more.
     */
    suspend fun searchMessages(chatId: Long, query: String, fromMessageId: Long, filter: ChatSearchFilter = ChatSearchFilter()): Result<MessageSearchPage>
    /** Same as [searchMessages], scoped to one forum topic. */
    suspend fun searchForumTopicMessages(chatId: Long, forumTopicId: Long, query: String, fromMessageId: Long, filter: ChatSearchFilter = ChatSearchFilter()): Result<MessageSearchPage>
    /**
     * Messages matching [query] in all chats of the account, limited by [filter]. Pass "" for the first page, then the
     * previous page's nextOffset. The offset is only valid with the same filter it came from.
     */
    suspend fun searchGlobalMessages(query: String, offset: String, filter: GlobalSearchFilter): Result<GlobalMessagePage>
    /**
     * Local and server chat-title search. TDLib has no offset here, so [limit] grows for the next page
     * and callers merge rows by chat id. Only chats already known to TDLib are returned.
     */
    suspend fun searchGlobalChats(query: String, limit: Int): Result<GlobalSearchPage<ChatPreview>>
    /** Address-book contacts matching [query], with TDLib's total count for the "show more" limit. */
    suspend fun searchGlobalContacts(query: String, limit: Int): Result<GlobalSearchPage<ContactPreview>>
    /** Public chats matching [query]. Results are not joined until the user taps one. */
    suspend fun searchGlobalPublicChats(query: String): Result<GlobalSearchPage<ChatPreview>>
    /**
     * Resolves a public search result only after the user taps it. Channels open in preview; public
     * groups join when needed. A join request that needs approval is returned, never sent silently
     * while the query is being typed.
     */
    suspend fun openPublicChat(chatId: Long): Result<PublicChatOpenResult>
    /**
     * Messages with an attachment of [kind] for the chat profile tabs and the chat media viewer, newest first. Same paging as
     * [searchMessages]. With [newer], the page holds messages newer than [fromMessageId] instead.
     */
    suspend fun sharedMessages(chatId: Long, kind: SharedMediaKind, fromMessageId: Long, newer: Boolean = false): Result<MessageSearchPage>
    fun messagesVisible(chatId: Long, messageIds: List<Long>)
    /**
     * Streams the history of one chat into [target] (a SAF document), newest to oldest in bounded pages.
     * Secret chats and chats with protected content are refused before anything is written. Messages TDLib
     * marks as not saveable are skipped. The target is discarded on cancellation or any error.
     */
    suspend fun exportChat(
        chatId: Long,
        target: ChatExportTarget,
        request: ChatExportRequest,
        onProgress: (ChatExportProgress) -> Unit = {}
    ): Result<ChatExportSummary>
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
