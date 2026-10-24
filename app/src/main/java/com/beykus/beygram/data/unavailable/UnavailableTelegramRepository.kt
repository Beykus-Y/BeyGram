package com.beykus.beygram.data.unavailable

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.ChatActivity
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMembersPage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.DeleteMessageScope
import com.beykus.beygram.domain.model.GlobalMessagePage
import com.beykus.beygram.domain.model.GlobalSearchFilter
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MediaPosition
import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.domain.model.MessageContext
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.MessageLimits
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MessageReaction
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.MessageSearchPage
import com.beykus.beygram.domain.model.NotificationPresentation
import com.beykus.beygram.domain.model.SharedMediaKind
import com.beykus.beygram.domain.model.PinnedMessagePage
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.BotKeyboard
import com.beykus.beygram.domain.model.BotCallbackAnswer
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.ProfileEdit
import com.beykus.beygram.domain.model.ProfileState
import com.beykus.beygram.domain.model.CachePolicy
import com.beykus.beygram.domain.model.SavedTelegramProxy
import com.beykus.beygram.domain.model.StorageCategory
import com.beykus.beygram.domain.model.StorageUsage
import com.beykus.beygram.domain.model.TelegramRoute
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.TelegramNotifications
import com.beykus.beygram.domain.model.TelegramProxyDraft
import com.beykus.beygram.diagnostics.report.BotConfirmation
import com.beykus.beygram.domain.repository.TelegramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Состояние без Telegram: нет параметров входа или нативной библиотеки. Чатов, контактов и сообщений нет,
 * действия возвращают ошибку, а экран входа объясняет причину. Демо-данных здесь нет.
 */
class UnavailableTelegramRepository : TelegramRepository {
    private val noChats = MutableStateFlow<List<ChatPreview>>(emptyList()).asStateFlow()
    private val noHistory = MutableStateFlow(ChatHistory()).asStateFlow()
    private val noProfile = MutableStateFlow(ProfileState()).asStateFlow()
    private val noTyping = MutableStateFlow<List<String>>(emptyList()).asStateFlow()

    override val authState: StateFlow<AuthState> = MutableStateFlow<AuthState>(AuthState.Idle).asStateFlow()
    override val chats: StateFlow<List<ChatPreview>> = noChats
    override val archivedChats: StateFlow<List<ChatPreview>> = noChats
    override val chatFolders: StateFlow<com.beykus.beygram.domain.model.TelegramChatFolders> =
        MutableStateFlow(com.beykus.beygram.domain.model.TelegramChatFolders()).asStateFlow()
    override val folderChats: StateFlow<Map<Int, List<ChatPreview>>> =
        MutableStateFlow<Map<Int, List<ChatPreview>>>(emptyMap()).asStateFlow()
    override val folderLoadStates: StateFlow<Map<Int, com.beykus.beygram.domain.model.TelegramFolderLoadState>> =
        MutableStateFlow<Map<Int, com.beykus.beygram.domain.model.TelegramFolderLoadState>>(emptyMap()).asStateFlow()
    override suspend fun loadMoreFolderChats(folderId: Int): Result<Unit> = unavailable()
    override suspend fun setFolderChatPinned(folderId: Int, chatId: Long, pinned: Boolean): Result<Unit> = unavailable()

    override suspend fun getFolderDraft(folderId: Int): Result<com.beykus.beygram.domain.model.TelegramFolderDraft> = unavailable()
    override suspend fun saveFolderDraft(draft: com.beykus.beygram.domain.model.TelegramFolderDraft): Result<Int> = unavailable()
    override suspend fun deleteServerFolder(folderId: Int): Result<Unit> = unavailable()
    override suspend fun reorderServerFolders(ids: List<Int>, mainPosition: Int): Result<Unit> = unavailable()

    override val connectionState: StateFlow<TelegramConnectionState> = MutableStateFlow(TelegramConnectionState.Unknown).asStateFlow()
    override val myProfile: StateFlow<ProfileState> = noProfile
    override val bioLengthLimit: StateFlow<Int> = MutableStateFlow(70).asStateFlow()
    override val messageLimits: StateFlow<MessageLimits> = MutableStateFlow(MessageLimits()).asStateFlow()
    override val notifications: StateFlow<TelegramNotifications> = MutableStateFlow(TelegramNotifications()).asStateFlow()
    override val notificationPresentation: StateFlow<NotificationPresentation> = MutableStateFlow(NotificationPresentation()).asStateFlow()

    private fun <T> unavailable(): Result<T> = Result.failure(UserMessageException(UiText.Res(R.string.err_telegram_not_running)))

    override fun observeChatInfo(chatId: Long): StateFlow<ProfileState> = noProfile
    override fun refreshChatInfo(chatId: Long) {}
    override suspend fun loadChatMembers(chatId: Long, offset: Int, limit: Int): Result<ChatMembersPage> = unavailable()
    override fun refreshMyProfile() {}
    override suspend fun updateMyProfile(edit: ProfileEdit): Result<Unit> = unavailable()
    override suspend fun setMyProfilePhoto(photo: OutgoingMedia): Result<Unit> = unavailable()
    override suspend fun loadProfilePhotos(profileId: Long, offset: Int, limit: Int): Result<com.beykus.beygram.domain.model.ProfilePhotosPage> = unavailable()
    override suspend fun deleteMyProfilePhoto(photoId: Long): Result<Unit> = unavailable()
    override suspend fun setPreviousProfilePhoto(photoId: Long): Result<Unit> = unavailable()
    override suspend fun setChatArchived(chatId: Long, archived: Boolean): Result<Unit> = unavailable()
    override suspend fun setChatPinned(chatId: Long, pinned: Boolean): Result<Unit> = unavailable()
    override suspend fun setChatMuted(chatId: Long, muted: Boolean): Result<Unit> = unavailable()
    override suspend fun deleteChatForSelf(chatId: Long): Result<Unit> = unavailable()
    override suspend fun blockPrivateChat(chatId: Long): Result<Unit> = unavailable()
    override val groupCreation: StateFlow<com.beykus.beygram.domain.model.GroupCreationState> =
        MutableStateFlow<com.beykus.beygram.domain.model.GroupCreationState>(com.beykus.beygram.domain.model.GroupCreationState.Idle).asStateFlow()
    override fun beginGroupCreation(title: String, memberUserIds: List<Long>): Result<Unit> = unavailable()
    override fun acknowledgeGroupCreation() {}
    override fun acknowledgeUncertainGroupCreation() {}
    override suspend fun createChannel(title: String, description: String): Result<Long> = unavailable()
    override suspend fun activeSessions(): Result<List<com.beykus.beygram.domain.model.DeviceSession>> = unavailable()
    override suspend fun terminateOtherSession(sessionId: Long): Result<Unit> = unavailable()
    override suspend fun terminateAllOtherSessions(): Result<Unit> = unavailable()
    override suspend fun archiveReturnsOnNewMessage(): Result<Boolean> = unavailable()
    override suspend fun setArchiveReturnsOnNewMessage(enabled: Boolean): Result<Unit> = unavailable()
    override suspend fun markChatRead(chatId: Long): Result<Unit> = unavailable()
    override suspend fun loadContacts(): Result<List<ContactPreview>> = Result.success(emptyList())
    override suspend fun addContact(firstName: String, lastName: String, phone: String): Result<Unit> = unavailable()
    override suspend fun openPrivateChat(userId: Long): Result<Long> = unavailable()
    override suspend fun loadCallHistory(offset: String): Result<CallHistoryPage> = Result.success(CallHistoryPage(emptyList(), ""))
    override suspend fun storageUsage(): Result<StorageUsage> = unavailable()
    override suspend fun clearStorage(categories: Set<StorageCategory>, onProgress: (done: Int, total: Int) -> Unit): Result<Long> = unavailable()
    override suspend fun applyCachePolicy(policy: CachePolicy): Result<Long> = unavailable()

    override fun startAuthorization() {}
    override fun submitPhoneNumber(phone: String) {}
    override fun submitCode(code: String) {}
    override fun resendCode() {}
    override fun submitPassword(password: String) {}
    override fun resetError() {}
    override fun reconnect() {}

    override suspend fun sendVideoNoteLossTest(media: OutgoingMedia, remoteCopy: java.io.File): Result<com.beykus.beygram.domain.model.VideoNoteLossRemoteResult> = unavailable()
    override suspend fun deleteVideoNoteLossTest(messageId: Long): Result<Unit> = unavailable()

    override suspend fun loadSavedProxies(): Result<List<SavedTelegramProxy>> = Result.success(emptyList())
    override suspend fun addProxy(draft: TelegramProxyDraft, enable: Boolean): Result<Unit> = unavailable()
    override suspend fun enableProxy(id: Int): Result<Unit> = unavailable()
    override suspend fun disableProxy(): Result<Unit> = unavailable()
    override suspend fun removeProxy(id: Int): Result<Unit> = unavailable()
    override suspend fun currentRoute(): Result<TelegramRoute> = unavailable()
    override suspend fun measureProxyLatency(server: String, port: Int): Result<Long> = unavailable()
    override suspend fun isMihomoProxyEnabled(): Result<Boolean> = Result.success(false)
    override suspend fun setMihomoProxyEnabled(enabled: Boolean): Result<Unit> = if (enabled) unavailable() else Result.success(Unit)

    override fun observeFile(fileId: Int): StateFlow<MediaFileState> = MutableStateFlow(MediaFileState(fileId)).asStateFlow()
    override fun downloadFile(fileId: Int) {}

    override suspend fun dismissNotifications(groupId: Int, maxNotificationId: Int): Result<Unit> = Result.success(Unit)
    override fun observeChat(chatId: Long): StateFlow<ChatPreview?> = MutableStateFlow<ChatPreview?>(null).asStateFlow()
    override suspend fun loadChat(chatId: Long): Result<Unit> = unavailable()

    // Канал подтверждения отчётов недоступен без Telegram-сессии.
    override suspend fun openBotChat(expectedBotUserId: Long, username: String): Result<Long> = unavailable()
    override suspend fun sendReportCommand(botChatId: Long, nonce: String): Result<Unit> = unavailable()
    override suspend fun awaitBotConfirmation(botChatId: Long, timeoutMillis: Long): Result<BotConfirmation> = unavailable()
    override fun cancelBotWait() {}

    override fun observeBotKeyboard(chatId: Long): StateFlow<BotKeyboard?> = MutableStateFlow<BotKeyboard?>(null).asStateFlow()
    override suspend fun dismissBotKeyboard(chatId: Long, messageId: Long): Result<Unit> = unavailable()
    override suspend fun botCallback(chatId: Long, messageId: Long, data: List<Byte>): Result<BotCallbackAnswer> = unavailable()
    override fun observePinnedMessagesVersion(chatId: Long): StateFlow<Long> = MutableStateFlow(0L).asStateFlow()
    override suspend fun pinnedMessages(chatId: Long, fromMessageId: Long, forumTopicId: Long): Result<PinnedMessagePage> = unavailable()
    override suspend fun setMessagePinned(chatId: Long, messageId: Long, pinned: Boolean): Result<Unit> = unavailable()
    override suspend fun sendMediaAlbum(chatId: Long, media: List<OutgoingMedia>, caption: String, replyToMessageId: Long?, entities: List<MessageTextEntity>, forumTopicId: Long): Result<Unit> = unavailable()
    override fun observeForumTopicDraft(chatId: Long, forumTopicId: Long): StateFlow<com.beykus.beygram.domain.model.MessageDraft> =
        MutableStateFlow(com.beykus.beygram.domain.model.MessageDraft()).asStateFlow()
    override fun updateForumTopicDraft(chatId: Long, forumTopicId: Long, transform: (com.beykus.beygram.domain.model.MessageDraft) -> com.beykus.beygram.domain.model.MessageDraft) {}
    override fun observeMessageDraft(chatId: Long): StateFlow<com.beykus.beygram.domain.model.MessageDraft> =
        MutableStateFlow(com.beykus.beygram.domain.model.MessageDraft()).asStateFlow()
    override fun updateMessageDraft(
        chatId: Long,
        transform: (com.beykus.beygram.domain.model.MessageDraft) -> com.beykus.beygram.domain.model.MessageDraft
    ) {}
    override fun observeChatHistory(chatId: Long): StateFlow<ChatHistory> = noHistory
    override fun observeForumTopicHistory(chatId: Long, forumTopicId: Long): StateFlow<ChatHistory> = noHistory
    override fun loadForumTopicHistory(chatId: Long, forumTopicId: Long) {}
    override fun loadOlderForumTopicMessages(chatId: Long, forumTopicId: Long) {}
    override suspend fun latestForumTopicPreview(chatId: Long, forumTopicId: Long): Result<String?> = unavailable()
    override suspend fun loadForumTopicMessagesAround(chatId: Long, forumTopicId: Long, messageId: Long): Result<Boolean> = unavailable()
    override fun openChat(chatId: Long) {}
    override fun closeChat(chatId: Long) {}
    override fun loadOlderMessages(chatId: Long) {}
    override suspend fun sendMessage(chatId: Long, text: String, replyToMessageId: Long?, entities: List<MessageTextEntity>, forumTopicId: Long, clearDraft: Boolean): Result<Unit> = unavailable()
    override suspend fun isForumChat(chatId: Long): Result<Boolean> = unavailable()

    override suspend fun forumTopics(chatId: Long, onPage: (List<ForumTopic>) -> Unit): Result<List<ForumTopic>?> = unavailable()
    override suspend fun canCreateForumTopic(chatId: Long): Result<Boolean> = unavailable()
    override suspend fun createForumTopic(chatId: Long, name: String): Result<Long> = unavailable()
    override suspend fun renameForumTopic(chatId: Long, forumTopicId: Long, name: String): Result<Unit> = unavailable()
    override suspend fun setForumTopicClosed(chatId: Long, forumTopicId: Long, closed: Boolean): Result<Unit> = unavailable()
    override suspend fun setForumTopicPinned(chatId: Long, forumTopicId: Long, pinned: Boolean): Result<Unit> = unavailable()
    override suspend fun deleteForumTopic(chatId: Long, forumTopicId: Long): Result<Unit> = unavailable()
    override suspend fun searchForumTopicMessages(chatId: Long, forumTopicId: Long, query: String, fromMessageId: Long): Result<MessageSearchPage> = unavailable()
    override suspend fun sendMedia(chatId: Long, media: OutgoingMedia, caption: String, replyToMessageId: Long?, entities: List<MessageTextEntity>, forumTopicId: Long): Result<Unit> = unavailable()
    override suspend fun retryMessages(chatId: Long, messageIds: List<Long>): Result<Unit> = unavailable()
    override suspend fun messageCapabilities(chatId: Long, messageId: Long): Result<MessageCapabilities> = unavailable()
    override suspend fun mediaPosition(chatId: Long, messageId: Long, kind: MediaKind): Result<MediaPosition> = unavailable()
    override suspend fun editMessage(chatId: Long, messageId: Long, text: String, entities: List<MessageTextEntity>): Result<Unit> = unavailable()
    override suspend fun deleteMessage(chatId: Long, messageId: Long, scope: DeleteMessageScope): Result<Unit> = unavailable()
    override suspend fun forwardMessage(fromChatId: Long, messageId: Long, toChatId: Long, asCopy: Boolean): Result<Unit> = unavailable()
    override suspend fun messageLink(chatId: Long, messageId: Long, inMessageThread: Boolean): Result<String> = unavailable()
    override suspend fun availableReactions(chatId: Long, messageId: Long): Result<List<ReactionOption>> = unavailable()
    override suspend fun toggleReaction(chatId: Long, messageId: Long, reaction: ReactionOption, addToRecent: Boolean): Result<List<MessageReaction>> = unavailable()
    override fun observeCustomEmoji(customEmojiId: Long): StateFlow<CustomEmoji?> = MutableStateFlow<CustomEmoji?>(null).asStateFlow()
    override suspend fun loadMessagesAround(chatId: Long, messageId: Long): Result<Boolean> = unavailable()
    override fun loadLatestMessages(chatId: Long) {}
    override suspend fun messageContext(chatId: Long, messageId: Long): Result<MessageContext> = unavailable()
    override suspend fun searchMessages(chatId: Long, query: String, fromMessageId: Long): Result<MessageSearchPage> = unavailable()
    override suspend fun searchGlobalMessages(query: String, offset: String, filter: GlobalSearchFilter): Result<GlobalMessagePage> = unavailable()
    override suspend fun sharedMessages(chatId: Long, kind: SharedMediaKind, fromMessageId: Long, newer: Boolean): Result<MessageSearchPage> = unavailable()
    override fun messagesVisible(chatId: Long, messageIds: List<Long>) {}
    override fun setAppForeground(foreground: Boolean) {}
    override fun refreshInteractionPolicy() {}
    override fun openMessageContent(chatId: Long, messageId: Long) {}
    override fun observeTyping(chatId: Long): StateFlow<List<String>> = noTyping
    override fun startChatActivity(chatId: Long, activity: ChatActivity) {}
    override fun endChatActivity(chatId: Long, activity: ChatActivity) {}
}
