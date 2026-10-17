package com.beykus.beygram.data.mock

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MediaPosition
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.ProfileInfo
import com.beykus.beygram.domain.model.ProfileState
import com.beykus.beygram.domain.model.ProfileEdit
import com.beykus.beygram.domain.model.ProfileKind
import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.CallRecord
import com.beykus.beygram.domain.model.CallStatus
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.orderedArchivedChats
import com.beykus.beygram.domain.model.orderedMainChats
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.MessageLimits
import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.domain.model.MessageContext
import com.beykus.beygram.domain.model.MessageSearchPage
import com.beykus.beygram.domain.model.MessageReply
import com.beykus.beygram.domain.model.DeleteMessageScope
import com.beykus.beygram.domain.model.TelegramNotifications
import com.beykus.beygram.domain.model.NotificationPresentation
import com.beykus.beygram.domain.model.SavedTelegramProxy
import com.beykus.beygram.domain.model.TelegramProxyDraft
import com.beykus.beygram.domain.model.TelegramProxyKind
import com.beykus.beygram.domain.repository.TelegramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DateFormat
import java.util.Date

/** Демо-данные (имена, тексты, время) заданы прямо здесь, а не в ресурсах: это мок для разработки. */
class MockTelegramRepository : TelegramRepository {

    override suspend fun setMihomoProxyEnabled(enabled: Boolean): Result<Unit> = Result.success(Unit)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _chats = MutableStateFlow(orderedMainChats(buildMockChats()))
    override val chats: StateFlow<List<ChatPreview>> = _chats.asStateFlow()
    private val _archivedChats = MutableStateFlow(orderedArchivedChats(buildMockChats()))
    override val archivedChats: StateFlow<List<ChatPreview>> = _archivedChats.asStateFlow()
    override val connectionState: StateFlow<TelegramConnectionState> = MutableStateFlow(TelegramConnectionState.Connected).asStateFlow()

    private var nextMessageId = 100L
    private var submittedPhone = ""
    override val notifications = MutableStateFlow(TelegramNotifications(initialized = true)).asStateFlow()
    override val notificationPresentation = MutableStateFlow(NotificationPresentation()).asStateFlow()
    override suspend fun dismissNotifications(groupId: Int, maxNotificationId: Int) = Result.success(Unit)
    override fun observeChat(chatId: Long) = MutableStateFlow((_chats.value + _archivedChats.value).firstOrNull { it.id == chatId }).asStateFlow()
    override suspend fun loadChat(chatId: Long): Result<Unit> = if ((_chats.value + _archivedChats.value).any { it.id == chatId }) Result.success(Unit) else Result.failure(UserMessageException(UiText.Res(R.string.err_chat_unavailable)))
    override suspend fun setChatArchived(chatId: Long, archived: Boolean): Result<Unit> {
        val archivedChat = _archivedChats.value.firstOrNull { it.id == chatId }
        val chat = archivedChat ?: _chats.value.firstOrNull { it.id == chatId } ?: return Result.failure(UserMessageException(UiText.Res(R.string.err_chat_unavailable)))
        if ((archivedChat != null) == archived) return Result.success(Unit)
        if (archived) {
            val nextArchiveOrder = (_archivedChats.value.maxOfOrNull { it.archiveOrder } ?: 0L) + 1L
            _chats.value = orderedMainChats(_chats.value.filterNot { it.id == chatId })
            _archivedChats.value = orderedArchivedChats(_archivedChats.value + chat.copy(order = 0L, archiveOrder = nextArchiveOrder))
        } else {
            val nextOrder = (_chats.value.maxOfOrNull { it.order } ?: 0L) + 1L
            _archivedChats.value = orderedArchivedChats(_archivedChats.value.filterNot { it.id == chatId })
            _chats.value = orderedMainChats(_chats.value + chat.copy(order = nextOrder, archiveOrder = 0L))
        }
        return Result.success(Unit)
    }

    override suspend fun markChatRead(chatId: Long): Result<Unit> {
        fun List<ChatPreview>.markRead() = map { if (it.id == chatId) it.copy(unreadCount = 0) else it }
        _chats.value = _chats.value.markRead()
        _archivedChats.value = _archivedChats.value.markRead()
        return Result.success(Unit)
    }

    private val mockContacts = mutableListOf(
        ContactPreview(5L, "Насальникова", "+7 900 000-00-05", null, 0xFF2F8A7B),
        ContactPreview(7L, "Анна Смирнова", "@anna_s", null, 0xFFC2593F),
        ContactPreview(8L, "Дима Лебедев", "+7 900 000-00-08", null, 0xFF3A78C2)
    )
    private var nextContactId = 9L
    override suspend fun loadContacts(): Result<List<ContactPreview>> = Result.success(mockContacts.sortedBy { it.title.lowercase() })
    override suspend fun addContact(firstName: String, lastName: String, phone: String): Result<Unit> {
        val title = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        if (title.isBlank()) return Result.failure(UserMessageException(UiText.Res(R.string.err_name_empty)))
        mockContacts += ContactPreview(nextContactId++, title, phone, null, 0xFF5B4BC4)
        return Result.success(Unit)
    }
    override suspend fun openPrivateChat(userId: Long): Result<Long> =
        if ((_chats.value + _archivedChats.value).any { it.id == userId }) Result.success(userId) else Result.failure(UserMessageException(UiText.Res(R.string.err_chat_unavailable)))
    private val mockCalls = run {
        val now = System.currentTimeMillis() / 1000
        listOf(
            CallRecord(9001L, 5L, "Насальникова", null, 0xFF2F8A7B, isVideo = false, isOutgoing = false, status = CallStatus.Answered, durationSeconds = 754, dateSeconds = now - 3_600),
            CallRecord(9002L, 2L, "Android Dev", null, 0xFF14B8A6, isVideo = true, isOutgoing = true, status = CallStatus.NoAnswer, durationSeconds = 0, dateSeconds = now - 7_200),
            CallRecord(9003L, 3L, "Product Notes", null, 0xFF8B5CF6, isVideo = false, isOutgoing = false, status = CallStatus.Missed, durationSeconds = 0, dateSeconds = now - 86_400)
        )
    }
    override suspend fun loadCallHistory(offset: String): Result<CallHistoryPage> = Result.success(CallHistoryPage(mockCalls, ""))
    private val profileState = MutableStateFlow(ProfileState(info = ProfileInfo(1, "Beykus", firstName = "Beykus", isSelf = true)))
    override val myProfile = profileState.asStateFlow()
    override val bioLengthLimit = MutableStateFlow(70).asStateFlow()
    override fun refreshMyProfile() = Unit
    override fun refreshChatInfo(chatId: Long) = Unit
    private val typing = MutableStateFlow(emptyList<String>()).asStateFlow()
    override fun observeTyping(chatId: Long): StateFlow<List<String>> = typing
    override fun observeChatInfo(chatId: Long): StateFlow<ProfileState> = MutableStateFlow(ProfileState(info = _chats.value.firstOrNull { it.id == chatId }?.let {
        ProfileInfo(it.id, it.title, kind = if (it.isGroup) ProfileKind.Group else ProfileKind.Person)
    })).asStateFlow()
    override suspend fun updateMyProfile(edit: ProfileEdit): Result<Unit> {
        profileState.value = ProfileState(info = profileState.value.info?.copy(title = "${edit.firstName} ${edit.lastName}".trim(), firstName = edit.firstName, lastName = edit.lastName, username = edit.username, bio = edit.bio))
        return Result.success(Unit)
    }
    override suspend fun setMyProfilePhoto(photo: OutgoingMedia): Result<Unit> = Result.success(Unit)

    private val messagesByChatId = mutableMapOf(
        1L to mutableListOf(
            ChatMessage(1L, 1L, "Привет. С чего начнем клиент?", "12:40", false),
            ChatMessage(2L, 1L, "Сначала разделим экраны и данные.", "12:42", true),
            ChatMessage(3L, 1L, "Закинул архитектуру под новый клиент.", "12:48", false)
        ),
        2L to mutableListOf(
            ChatMessage(4L, 2L, "Навигация не должна жить внутри экранов.", "11:14", false),
            ChatMessage(5L, 2L, "Да, экраны оставим stateless.", "11:16", true),
            ChatMessage(6L, 2L, "Compose оставляем только во view-слое.", "11:20", false)
        ),
        3L to mutableListOf(
            ChatMessage(7L, 3L, "Записал базовые задачи для MVP.", "18:05", true),
            ChatMessage(8L, 3L, "Дальше нужны реальные состояния авторизации.", "18:07", false)
        ),
        4L to mutableListOf(
            ChatMessage(9L, 4L, "Градиентный стартовый экран выглядит лишним.", "09:12", true),
            ChatMessage(10L, 4L, "Убираем лишнюю декоративность.", "09:15", false)
        )
    )

    override fun startAuthorization() {
        _authState.value = AuthState.WaitingForPhone
    }

    override fun submitPhoneNumber(phone: String) {
        submittedPhone = phone
        _authState.value = AuthState.WaitingForCode(phone)
    }

    override fun submitCode(code: String) {
        _authState.value = AuthState.Ready
    }

    override fun resendCode() {
        // В моке просто остаёмся на том же экране; ничего не делаем.
    }

    override fun submitPassword(password: String) {
        _authState.value = AuthState.Ready
    }

    override fun resetError() {
        _authState.value = AuthState.WaitingForPhone
    }

    override fun reconnect() = Unit

    private val savedProxies = mutableListOf<SavedTelegramProxy>()
    private var nextProxyId = 1

    override suspend fun loadSavedProxies(): Result<List<SavedTelegramProxy>> = Result.success(savedProxies.toList())

    override suspend fun addProxy(draft: TelegramProxyDraft, enable: Boolean): Result<Unit> {
        if (enable) updateSavedProxies { it.copy(enabled = false) }
        savedProxies += SavedTelegramProxy(nextProxyId++, draft.kind, draft.server.trim(), draft.port, enabled = enable)
        return Result.success(Unit)
    }

    override suspend fun enableProxy(id: Int): Result<Unit> {
        if (savedProxies.none { it.id == id }) return Result.failure(UserMessageException(UiText.Res(R.string.err_proxy_not_found)))
        updateSavedProxies { it.copy(enabled = it.id == id) }
        return Result.success(Unit)
    }

    override suspend fun disableProxy(): Result<Unit> {
        updateSavedProxies { it.copy(enabled = false) }
        return Result.success(Unit)
    }

    override suspend fun removeProxy(id: Int): Result<Unit> {
        savedProxies.removeAll { it.id == id }
        return Result.success(Unit)
    }

    override suspend fun measureProxyLatency(server: String, port: Int): Result<Long> = Result.success(40L + port % 90)

    override suspend fun isMihomoProxyEnabled(): Result<Boolean> = Result.success(false)

    private fun updateSavedProxies(transform: (SavedTelegramProxy) -> SavedTelegramProxy) {
        for (index in savedProxies.indices) savedProxies[index] = transform(savedProxies[index])
    }

    private val historyStates = mutableMapOf<Long, MutableStateFlow<ChatHistory>>()
    private val fileStates = mutableMapOf<Int, MutableStateFlow<MediaFileState>>()
    override val messageLimits = MutableStateFlow(MessageLimits()).asStateFlow()
    override fun setAppForeground(foreground: Boolean) = Unit
    override fun refreshInteractionPolicy() = Unit
    override fun messagesVisible(chatId: Long, messageIds: List<Long>) {
        val state = historyStates[chatId] ?: return
        state.value = state.value.copy(lastReadInboxMessageId = maxOf(state.value.lastReadInboxMessageId, messageIds.maxOrNull() ?: 0))
    }
    override suspend fun messageCapabilities(chatId: Long, messageId: Long): Result<MessageCapabilities> {
        val message = messagesByChatId[chatId]?.firstOrNull { it.id == messageId } ?: return Result.failure(UserMessageException(UiText.Res(R.string.chat_message_unavailable)))
        return Result.success(MessageCapabilities(true, true, message.isOutgoing, true, message.isOutgoing, canForward = true, canSave = true))
    }
    override suspend fun mediaPosition(chatId: Long, messageId: Long, kind: MediaKind): Result<MediaPosition> {
        // Фото и видео считаются вместе, как в TDLib (SearchMessagesFilterPhotoAndVideo).
        val kinds = if (kind == MediaKind.Photo || kind == MediaKind.Video) setOf(MediaKind.Photo, MediaKind.Video) else setOf(kind)
        val sameGroup = messagesByChatId[chatId].orEmpty().filter { it.media?.kind in kinds }
        val index = sameGroup.indexOfFirst { it.id == messageId }
        if (index < 0) return Result.failure(UserMessageException(UiText.Res(R.string.chat_message_unavailable)))
        return Result.success(MediaPosition(index + 1, sameGroup.size))
    }
    override suspend fun editMessage(chatId: Long, messageId: Long, text: String): Result<Unit> {
        val messages = messagesByChatId[chatId] ?: return Result.failure(UserMessageException(UiText.Res(R.string.chat_message_unavailable)))
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return Result.failure(UserMessageException(UiText.Res(R.string.chat_message_unavailable)))
        messages[index] = messages[index].copy(text = text, isEdited = true)
        historyStates[chatId]?.let { it.value = it.value.copy(messages = messages.toList()) }
        return Result.success(Unit)
    }
    override suspend fun deleteMessage(chatId: Long, messageId: Long, scope: DeleteMessageScope): Result<Unit> {
        messagesByChatId[chatId]?.removeAll { it.id == messageId }
        historyStates[chatId]?.let { it.value = it.value.copy(messages = messagesByChatId[chatId].orEmpty().toList()) }
        return Result.success(Unit)
    }
    override suspend fun forwardMessage(fromChatId: Long, messageId: Long, toChatId: Long): Result<Unit> = Result.success(Unit)
    override suspend fun messageContext(chatId: Long, messageId: Long): Result<MessageContext> = Result.success(MessageContext(chatId, (_chats.value + _archivedChats.value).firstOrNull { it.id == chatId }?.title.orEmpty(), messageId, messagesByChatId[chatId].orEmpty().toList()))
    override suspend fun searchMessages(chatId: Long, query: String, fromMessageId: Long): Result<MessageSearchPage> =
        Result.success(MessageSearchPage(messagesByChatId[chatId].orEmpty().filter { it.text.contains(query, ignoreCase = true) }, 0L))
    private fun reply(chatId: Long, id: Long?) = messagesByChatId[chatId]?.firstOrNull { it.id == id }?.let { MessageReply(chatId, it.id, UiText.Raw(it.text), "Собеседник") }
    override fun observeFile(fileId: Int): StateFlow<MediaFileState> = fileStates.getOrPut(fileId) { MutableStateFlow(MediaFileState(fileId)) }.asStateFlow()
    override fun downloadFile(fileId: Int) = Unit
    override fun openMessageContent(chatId: Long, messageId: Long) = Unit
    override suspend fun sendMedia(chatId: Long, media: OutgoingMedia, caption: String, replyToMessageId: Long?): Result<Unit> {
        val id = nextMessageId++
        val fileId = id.toInt()
        fileStates[fileId] = MutableStateFlow(MediaFileState(fileId, media.size, media.path))
        val message = ChatMessage(id, chatId, caption, nowTime(), true, reply = reply(chatId, replyToMessageId),
            media = MessageMedia(media.kind, fileId, fileName = media.fileName, mimeType = media.mimeType, width = media.width, height = media.height, durationSeconds = media.durationSeconds))
        messagesByChatId.getOrPut(chatId) { mutableListOf() }.add(message)
        observeChatHistory(chatId)
        historyStates.getValue(chatId).value = ChatHistory(messagesByChatId[chatId].orEmpty().toList(), hasOlderMessages = false)
        return Result.success(Unit)
    }
    override suspend fun retryMessages(chatId: Long, messageIds: List<Long>): Result<Unit> = Result.success(Unit)

    override fun observeChatHistory(chatId: Long): StateFlow<ChatHistory> = historyStates.getOrPut(chatId) {
        MutableStateFlow(ChatHistory(messages = messagesByChatId[chatId].orEmpty().toList(), hasOlderMessages = false))
    }.asStateFlow()

    override fun openChat(chatId: Long) = Unit
    override fun closeChat(chatId: Long) = Unit
    override fun loadOlderMessages(chatId: Long) = Unit

    override suspend fun sendMessage(chatId: Long, text: String, replyToMessageId: Long?): Result<Unit> {
        val message = ChatMessage(
            id = nextMessageId++,
            chatId = chatId,
            text = text,
            time = nowTime(),
            isOutgoing = true,
            reply = reply(chatId, replyToMessageId)
        )
        messagesByChatId.getOrPut(chatId) { mutableListOf() }.add(message)
        observeChatHistory(chatId)
        historyStates.getValue(chatId).value = ChatHistory(messagesByChatId[chatId].orEmpty().toList(), hasOlderMessages = false)

        val updated = _chats.value.map { chat ->
            if (chat.id == chatId) chat.copy(lastMessage = UiText.Raw(text), lastMessageTime = nowTime(), order = (_chats.value.maxOfOrNull { it.order } ?: 0L) + 1L)
            else chat
        }
        _chats.value = orderedMainChats(updated)
        return Result.success(Unit)

    }
}

private fun buildMockChats() = listOf(
    ChatPreview(
        id = 1L,
        title = "Beykus",
        initials = "BK",
        lastMessage = UiText.Raw("Закинул архитектуру под новый клиент."),
        lastMessageTime = "12:48",
        unreadCount = 2,
        avatarColorArgb = 0xFF2AABEE,
        isPinned = true,
        order = 40L
    ),
    ChatPreview(
        id = 2L,
        title = "Android Dev",
        initials = "AD",
        lastMessage = UiText.Raw("Compose оставляем только во view-слое."),
        lastMessageTime = "11:20",
        unreadCount = 0,
        avatarColorArgb = 0xFF14B8A6,
        isGroup = true,
        isPinned = true,
        order = 30L
    ),
    ChatPreview(
        id = 3L,
        title = "Product Notes",
        initials = "PN",
        lastMessage = UiText.Raw("Дальше нужны реальные состояния авторизации."),
        lastMessageTime = "18:07",
        unreadCount = 5,
        avatarColorArgb = 0xFF8B5CF6,
        isGroup = true,
        order = 20L
    ),
    ChatPreview(
        id = 4L,
        title = "Design Lab",
        initials = "DL",
        lastMessage = UiText.Raw("Убираем лишнюю декоративность."),
        lastMessageTime = "09:15",
        unreadCount = 0,
        avatarColorArgb = 0xFFEF4444,
        order = 10L
    ),
    ChatPreview(
        id = 5L,
        title = "Насальникова",
        initials = "Н",
        lastMessage = UiText.Raw("Ок, пришлю файлы завтра"),
        lastMessageTime = "18:07",
        unreadCount = 1,
        avatarColorArgb = 0xFF2F8A7B,
        archiveOrder = 20L
    ),
    ChatPreview(
        id = 6L,
        title = "Команда «Релиз»",
        initials = "Р",
        lastMessage = UiText.Raw("Артём: сборка 1.4 готова"),
        lastMessageTime = "09:15",
        unreadCount = 14,
        avatarColorArgb = 0xFF5B4BC4,
        isGroup = true,
        isMuted = true,
        archiveOrder = 10L
    )
)

/** Текущее время в формате, который показывает экран чата. */
private fun nowTime(): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
