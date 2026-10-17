package com.beykus.beygram.data.unavailable

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.DeleteMessageScope
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MediaPosition
import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.domain.model.MessageContext
import com.beykus.beygram.domain.model.MessageLimits
import com.beykus.beygram.domain.model.MessageSearchPage
import com.beykus.beygram.domain.model.NotificationPresentation
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.ProfileEdit
import com.beykus.beygram.domain.model.ProfileState
import com.beykus.beygram.domain.model.SavedTelegramProxy
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.TelegramNotifications
import com.beykus.beygram.domain.model.TelegramProxyDraft
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
    override val connectionState: StateFlow<TelegramConnectionState> = MutableStateFlow(TelegramConnectionState.Unknown).asStateFlow()
    override val myProfile: StateFlow<ProfileState> = noProfile
    override val bioLengthLimit: StateFlow<Int> = MutableStateFlow(70).asStateFlow()
    override val messageLimits: StateFlow<MessageLimits> = MutableStateFlow(MessageLimits()).asStateFlow()
    override val notifications: StateFlow<TelegramNotifications> = MutableStateFlow(TelegramNotifications()).asStateFlow()
    override val notificationPresentation: StateFlow<NotificationPresentation> = MutableStateFlow(NotificationPresentation()).asStateFlow()

    private fun <T> unavailable(): Result<T> = Result.failure(UserMessageException(UiText.Res(R.string.start_sign_in_unavailable)))

    override fun observeChatInfo(chatId: Long): StateFlow<ProfileState> = noProfile
    override fun refreshChatInfo(chatId: Long) {}
    override fun refreshMyProfile() {}
    override suspend fun updateMyProfile(edit: ProfileEdit): Result<Unit> = unavailable()
    override suspend fun setMyProfilePhoto(photo: OutgoingMedia): Result<Unit> = unavailable()
    override suspend fun setChatArchived(chatId: Long, archived: Boolean): Result<Unit> = unavailable()
    override suspend fun markChatRead(chatId: Long): Result<Unit> = unavailable()
    override suspend fun loadContacts(): Result<List<ContactPreview>> = Result.success(emptyList())
    override suspend fun addContact(firstName: String, lastName: String, phone: String): Result<Unit> = unavailable()
    override suspend fun openPrivateChat(userId: Long): Result<Long> = unavailable()
    override suspend fun loadCallHistory(offset: String): Result<CallHistoryPage> = Result.success(CallHistoryPage(emptyList(), ""))

    override fun startAuthorization() {}
    override fun submitPhoneNumber(phone: String) {}
    override fun submitCode(code: String) {}
    override fun resendCode() {}
    override fun submitPassword(password: String) {}
    override fun resetError() {}
    override fun reconnect() {}

    override suspend fun loadSavedProxies(): Result<List<SavedTelegramProxy>> = Result.success(emptyList())
    override suspend fun addProxy(draft: TelegramProxyDraft, enable: Boolean): Result<Unit> = unavailable()
    override suspend fun enableProxy(id: Int): Result<Unit> = unavailable()
    override suspend fun disableProxy(): Result<Unit> = unavailable()
    override suspend fun removeProxy(id: Int): Result<Unit> = unavailable()
    override suspend fun measureProxyLatency(server: String, port: Int): Result<Long> = unavailable()
    override suspend fun isMihomoProxyEnabled(): Result<Boolean> = Result.success(false)
    override suspend fun setMihomoProxyEnabled(enabled: Boolean): Result<Unit> = Result.success(Unit)

    override fun observeFile(fileId: Int): StateFlow<MediaFileState> = MutableStateFlow(MediaFileState(fileId)).asStateFlow()
    override fun downloadFile(fileId: Int) {}

    override suspend fun dismissNotifications(groupId: Int, maxNotificationId: Int): Result<Unit> = Result.success(Unit)
    override fun observeChat(chatId: Long): StateFlow<ChatPreview?> = MutableStateFlow<ChatPreview?>(null).asStateFlow()
    override suspend fun loadChat(chatId: Long): Result<Unit> = unavailable()

    override fun observeChatHistory(chatId: Long): StateFlow<ChatHistory> = noHistory
    override fun openChat(chatId: Long) {}
    override fun closeChat(chatId: Long) {}
    override fun loadOlderMessages(chatId: Long) {}
    override suspend fun sendMessage(chatId: Long, text: String, replyToMessageId: Long?): Result<Unit> = unavailable()
    override suspend fun sendMedia(chatId: Long, media: OutgoingMedia, caption: String, replyToMessageId: Long?): Result<Unit> = unavailable()
    override suspend fun retryMessages(chatId: Long, messageIds: List<Long>): Result<Unit> = unavailable()
    override suspend fun messageCapabilities(chatId: Long, messageId: Long): Result<MessageCapabilities> = unavailable()
    override suspend fun mediaPosition(chatId: Long, messageId: Long, kind: MediaKind): Result<MediaPosition> = unavailable()
    override suspend fun editMessage(chatId: Long, messageId: Long, text: String): Result<Unit> = unavailable()
    override suspend fun deleteMessage(chatId: Long, messageId: Long, scope: DeleteMessageScope): Result<Unit> = unavailable()
    override suspend fun forwardMessage(fromChatId: Long, messageId: Long, toChatId: Long): Result<Unit> = unavailable()
    override suspend fun messageContext(chatId: Long, messageId: Long): Result<MessageContext> = unavailable()
    override suspend fun searchMessages(chatId: Long, query: String, fromMessageId: Long): Result<MessageSearchPage> = unavailable()
    override fun messagesVisible(chatId: Long, messageIds: List<Long>) {}
    override fun setAppForeground(foreground: Boolean) {}
    override fun refreshInteractionPolicy() {}
    override fun openMessageContent(chatId: Long, messageId: Long) {}
    override fun observeTyping(chatId: Long): StateFlow<List<String>> = noTyping
}
