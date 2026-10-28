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
    /** Commands advertised by the private chat's bot; null for a non-bot chat or a bot without commands. */
    suspend fun botCommandMenu(chatId: Long): Result<BotCommandMenu?>
    /**
     * Resolves an inline bot by username and asks TDLib for one results page. Pass "" first, then the previous
     * [InlineBotResults.nextOffset]. No message is sent by this call. A null page means [username] is not an inline
     * bot (unknown, a user, a channel or a bot without inline mode), which is not an error.
     */
    suspend fun inlineBotQuery(chatId: Long, username: String, query: String, offset: String = ""): Result<InlineBotResults?>
    /**
     * Music from the optional music inline bot; null when the bot does not resolve. The caller's own music search is separate.
     * [contextChatId] is the chat a result will be sent to; 0 asks in Saved Messages, which is enough for playback.
     */
    suspend fun musicBotSearch(query: String, offset: String = "", contextChatId: Long = 0L): Result<InlineBotResults?>
    /** Downloads one music bot result for the shared player and removes the temporary message it was sent as. */
    suspend fun musicBotTrack(queryId: Long, resultId: String): Result<MusicBotTrack?>
    /** Sends one inline result with the composer's reply, forum topic and delivery options. */
    suspend fun sendInlineBotResult(
        chatId: Long,
        queryId: Long,
        resultId: String,
        replyToMessageId: Long? = null,
        forumTopicId: Long = 0L,
        options: MessageSendOptions = MessageSendOptions()
    ): Result<Unit>
    /** Opens a Mini App through TDLib; the returned URL and origin policy are authoritative. */
    suspend fun openBotWebApp(
        chatId: Long,
        botUserId: Long,
        url: String,
        source: BotWebAppSource,
        forumTopicId: Long = 0L,
        replyToMessageId: Long? = null,
        theme: BotWebAppTheme,
        buttonText: String = ""
    ): Result<BotWebAppLaunch>

    /**
     * Sends data produced by a Mini App to its bot with SendWebAppData. [buttonText] is the text of the button that
     * opened the Web App. The payload is validated for size and JSON before reaching TDLib; data is never logged.
     */
    suspend fun sendBotWebAppData(botUserId: Long, buttonText: String, data: String): Result<Unit>
    /** Informs TDLib that the Mini App screen is gone. Safe to call once per launch; never blocks disposal. */
    fun closeBotWebApp(launchId: Long)
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
    /** Poll, location, contact and dice are sent as TDLib content, never as formatted text. */
    suspend fun sendPoll(chatId: Long, poll: OutgoingPoll, replyToMessageId: Long? = null, forumTopicId: Long = 0L, options: MessageSendOptions = MessageSendOptions()): Result<Unit>
    suspend fun sendLocation(chatId: Long, point: GeoPoint, live: Boolean = false, replyToMessageId: Long? = null, forumTopicId: Long = 0L, options: MessageSendOptions = MessageSendOptions()): Result<Unit>
    suspend fun sendContactCard(chatId: Long, userId: Long, replyToMessageId: Long? = null, forumTopicId: Long = 0L, options: MessageSendOptions = MessageSendOptions()): Result<Unit>
    suspend fun sendDice(chatId: Long, emoji: String, replyToMessageId: Long? = null, forumTopicId: Long = 0L, options: MessageSendOptions = MessageSendOptions()): Result<Unit>
    /** Chosen option positions are 0-based, as TDLib expects. */
    suspend fun votePoll(chatId: Long, messageId: Long, optionIndices: List<Int>): Result<Unit>
    suspend fun stopPoll(chatId: Long, messageId: Long): Result<Unit>
    /** Voters of one option in a non-anonymous poll; [offset] is the number of already loaded voters. */
    suspend fun pollVoters(chatId: Long, messageId: Long, optionIndex: Int, offset: Int, limit: Int = 50): Result<PollVotersPage>
    /** Appends an option when TDLib's MessagePoll.canAddOption allows it. */
    suspend fun addPollOption(chatId: Long, messageId: Long, text: String): Result<Unit>
    /** Ends an outgoing live-location share by clearing EditMessageLiveLocation.location. */
    suspend fun stopLiveLocation(chatId: Long, messageId: Long): Result<Unit>
    /** The account's Telegram address book, for the compose-contact picker. */
    suspend fun contacts(): Result<List<ContactPreview>>
    /**
     * Sends a sticker as a native InputMessageSticker, not as a document. [sticker] carries the TDLib file id,
     * dimensions and emoji; its bytes are never downloaded by the picker. Reply/topic/scheduling apply as usual.
     */
    suspend fun sendSticker(chatId: Long, sticker: OutgoingMedia, replyToMessageId: Long? = null, forumTopicId: Long = 0L, options: MessageSendOptions = MessageSendOptions()): Result<Unit>
    /** The account's recent sticker list; the first page is bounded by TDLib. */
    suspend fun recentStickers(): Result<List<StickerItem>>
    /** Stickers explicitly marked as favorites by this account. */
    suspend fun favoriteStickers(): Result<List<StickerItem>>
    /** Adds or removes [sticker] from the account's favorite stickers; the picker updates only after TDLib confirms. */
    suspend fun setStickerFavorite(sticker: StickerItem, favorite: Boolean): Result<Unit>
    /** Installed regular sticker sets; opening one fetches that set only when the user asks for it. */
    suspend fun installedStickerSets(): Result<List<StickerSetSummary>>
    suspend fun stickerSet(setId: Long): Result<StickerSetContent>
    /**
     * Adds [setId] to the account's installed sets or removes it. Archiving is not exposed here: opening a sticker
     * from a chat only needs the installed state.
     */
    suspend fun changeStickerSet(setId: Long, installed: Boolean): Result<Unit>
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
    /**
     * Secret-chat lifecycle and TTL by chat id. The map contains only chats whose details were requested or updated;
     * a secret chat is device-specific, so this state is never persisted locally.
     */
    val secretChats: StateFlow<Map<Long, SecretChatDetails>>
    /** Asks TDLib for SecretChat state and the current Chat.messageAutoDeleteTime; safe to call while opening. */
    fun refreshSecretChat(chatId: Long)
    /**
     * Changes the chat-wide self-destruct timer for messages sent from now on (SetChatMessageAutoDeleteTime).
     * Already delivered messages keep the TTL TDLib assigned them; this is not per-message view-once.
     */
    suspend fun setSecretChatTtl(chatId: Long, seconds: Int): Result<Unit>
    /** CloseSecretChat; TDLib closes the session. History already delivered stays on the devices, so callers must confirm. */
    suspend fun closeSecretChat(chatId: Long): Result<Unit>
    /** Notification sounds already saved in this Telegram account. */
    suspend fun savedNotificationSounds(): Result<List<NotificationSoundOption>>
    /** Translates a message with TDLib, preserving its formatting entities. Secret chats are refused locally. */
    suspend fun translateMessage(chatId: Long, messageId: Long, toLanguageCode: String): Result<MessageTranslation>
    /** Starts server-side speech recognition; the result arrives through UpdateMessageContent. Never automatic. */
    suspend fun recognizeSpeech(chatId: Long, messageId: Long): Result<Unit>
    /** Rates the last recognition result as good or bad. Telegram uses it to improve recognition quality. */
    suspend fun rateSpeechRecognition(chatId: Long, messageId: Long, isGood: Boolean): Result<Unit>
    /**
     * Telegram's free recognition quota for this account. Null means TDLib has not reported it yet; an
     * exhausted quota is represented by [SpeechRecognitionTrial.leftCount] equal to zero.
     */
    val speechRecognitionTrial: StateFlow<SpeechRecognitionTrial?>
    /** Place among the chat's attachments of the same kind; TDLib counts can be approximate in large chats. */
    suspend fun mediaPosition(chatId: Long, messageId: Long, kind: MediaKind): Result<MediaPosition>
    suspend fun editMessage(chatId: Long, messageId: Long, text: String, entities: List<MessageTextEntity> = emptyList()): Result<Unit>
    suspend fun deleteMessage(chatId: Long, messageId: Long, scope: DeleteMessageScope): Result<Unit>
    /** Deletes a selection with one DeleteMessages request, after checking the chosen scope for every message. */
    suspend fun deleteMessages(chatId: Long, messageIds: List<Long>, scope: DeleteMessageScope): Result<Unit>
    /** Marks a chat unread without changing its read watermark; the chat list shows an unread marker. */
    suspend fun markChatUnread(chatId: Long): Result<Unit>
    /** Sends a complaint about messages to Telegram with the chosen reason. Nothing is sent before the user picks one. */
    suspend fun reportMessages(chatId: Long, messageIds: List<Long>, reason: MessageReportReason): Result<Unit>
    /**
     * Forwards a message. With [asCopy] the original sender is hidden, like Telegram's "Forward without author";
     * TDLib refuses either form when the message can't be forwarded. [messageThreadId] targets a forum topic.
     */
    suspend fun forwardMessage(
        fromChatId: Long,
        messageId: Long,
        toChatId: Long,
        asCopy: Boolean = false,
        messageThreadId: Long? = null,
        options: MessageSendOptions = MessageSendOptions()
    ): Result<Unit>
    /**
     * Forwards a whole selection with one ForwardMessages request (at most [MAX_MESSAGE_BATCH_SIZE] messages).
     * [messageIds] must keep album members adjacent so TDLib does not split the album. Each message is checked for
     * forward rights before anything is sent; a TDLib partial result leaves failed IDs for the caller to keep selected.
     */
    suspend fun forwardMessages(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        asCopy: Boolean = false,
        messageThreadId: Long? = null,
        options: MessageSendOptions = MessageSendOptions()
    ): Result<ForwardedMessages>
    /** A t.me link to [messageId], or a Telegram error when the message has no shareable link. */
    suspend fun messageLink(chatId: Long, messageId: Long, inMessageThread: Boolean = false): Result<String>
    /**
     * Reactions the current user can add to the message, in Telegram's order. Empty where reactions are not allowed. Custom
     * emoji are listed only for users with Telegram Premium. A Stars reaction is listed only where Telegram allows one here.
     */
    suspend fun availableReactions(chatId: Long, messageId: Long): Result<List<ReactionOption>>
    /**
     * Adds [reaction], or removes it when the user has already chosen it. [addToRecent] is set only for a reaction picked from the
     * extended menu, as Telegram requires. Returns the message's reactions after the change.
     */
    suspend fun toggleReaction(chatId: Long, messageId: Long, reaction: ReactionOption, addToRecent: Boolean): Result<List<MessageReaction>>
    /**
     * Sends one Stars reaction of [starCount] after the user confirmed the amount. Never retried. A
     * [PaidReactionUncertainException] means Telegram may already have taken the Stars, so the user must check the message first.
     */
    suspend fun addPaidReaction(chatId: Long, messageId: Long, starCount: Long, isAnonymous: Boolean = false): Result<Unit> =
        Result.failure(UnsupportedOperationException())
    /** The Stars balance the paid reaction dialog spends from; read the same way as the Stars screen. */
    suspend fun starBalance(): Result<StarBalance> = Result.failure(UnsupportedOperationException())
    /** The custom emoji sticker behind [customEmojiId]. TDLib is asked on first use; the value stays null until it answers. */
    fun observeCustomEmoji(customEmojiId: Long): StateFlow<CustomEmoji?>
    /** The effect behind [effectId]. Only fresh-message overlays call this; the value stays null until TDLib answers. */
    fun observeMessageEffect(effectId: Long): StateFlow<MessageEffect?>
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
    /** Whether this account's stealth settings let [effect] reach Telegram for [chatId]. Accounts without stealth settings allow everything. */
    fun interactionAllowed(effect: InteractionEffect, chatId: Long?): Boolean = true
    /** Whether a message send must be confirmed first: this account hides its online status and warns before risky actions. */
    fun stealthWarnsBeforeSending(): Boolean = false
    fun openMessageContent(chatId: Long, messageId: Long)
    /** Opens view-once content the user explicitly agreed to open although media-open marks are hidden. */
    fun openMessageContentWithConsent(chatId: Long, messageId: Long) = openMessageContent(chatId, messageId)
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
    /** Reads what paying for a paid media or invoice message would cost. Reading never spends anything. */
    suspend fun paidContentPlan(chatId: Long, messageId: Long): PaidContentPlan = PaidContentPlan.Unavailable
    /**
     * Pays a paid message with Telegram Stars for exactly [confirmedStarCount], the price the user confirmed.
     * Never retried automatically: a timeout or an unknown failure is reported as Uncertain.
     */
    suspend fun payPaidContentWithStars(chatId: Long, messageId: Long, confirmedStarCount: Long): PaidContentPaymentOutcome =
        PaidContentPaymentOutcome.NotPayable
}
