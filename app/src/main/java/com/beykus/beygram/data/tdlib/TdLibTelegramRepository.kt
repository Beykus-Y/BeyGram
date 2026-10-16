package com.beykus.beygram.data.tdlib

import android.content.Context
import android.os.Build
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.ProfileEdit
import com.beykus.beygram.domain.model.ProfileState
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.orderedMainChats
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.InteractionPolicy
import com.beykus.beygram.domain.model.DeleteMessageScope
import com.beykus.beygram.domain.model.MessageReply
import com.beykus.beygram.domain.model.NotificationPresentation
import com.beykus.beygram.domain.repository.TelegramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.text.DateFormat
import java.util.Date

/**
 * Реальная реализация репозитория поверх TDLib.
 *
 * Все ответы TDLib приходят в [handleUpdate] на внутреннем потоке TDLib.
 * Состояние хранится в [MutableStateFlow], что гарантирует:
 *  - потокобезопасную запись (value=),
 *  - автоматический recomposition в Compose через collectAsStateWithLifecycle().
 */
class TdLibTelegramRepository private constructor(context: Context, policy: InteractionPolicy) : TelegramRepository {

    private val config = TdLibConfig.from(context)

    // ---- Реактивное состояние ------------------------------------------------

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatPreview>>(emptyList())
    override val chats: StateFlow<List<ChatPreview>> = _chats.asStateFlow()

    private val _connectionState = MutableStateFlow(TelegramConnectionState.Unknown)
    override val connectionState: StateFlow<TelegramConnectionState> = _connectionState.asStateFlow()

    private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var authTimeoutJob: Job? = null

    // ---- Вспомогательные структуры -------------------------------------------

    /** Кэш сообщений по chatId. */
    private val messageHistory = MessageHistoryStore()
    private val mediaFiles = MediaFileStore()
    private val mediaMapper = TdMediaMapper(mediaFiles)
    private val profiles = TdProfiles({ client }, mediaFiles)
    private val notificationFeed = TdNotifications({ client }, profiles::senderName)
    override val notifications get() = notificationFeed.state
    private val _notificationPresentation = MutableStateFlow(NotificationPresentation())
    override val notificationPresentation = _notificationPresentation.asStateFlow()
    override suspend fun dismissNotifications(groupId: Int, maxNotificationId: Int) = notificationFeed.dismiss(groupId, maxNotificationId)
    private val chatStates = ConcurrentHashMap<Long, MutableStateFlow<ChatPreview?>>()
    override fun observeChat(chatId: Long): StateFlow<ChatPreview?> = chatStates.computeIfAbsent(chatId) { MutableStateFlow(chatCache[it]) }.asStateFlow()
    override suspend fun loadChat(chatId: Long): Result<Unit> = conversationResult {
        handleNewChat(TdRequests { client }(TdApi.GetChat(chatId))); Unit
    }
    private val conversationActions = TdConversationActions({ client }, messageHistory) { it.toChatMessage() }
    private val conversationEffects = TdConversationEffects({ client }, policy)
    override val messageLimits get() = conversationActions.limits
    override suspend fun messageCapabilities(chatId: Long, messageId: Long) = conversationActions.capabilities(chatId, messageId)
    override suspend fun editMessage(chatId: Long, messageId: Long, text: String) = conversationActions.edit(chatId, messageId, text)
    override suspend fun deleteMessage(chatId: Long, messageId: Long, scope: DeleteMessageScope) = conversationActions.delete(chatId, messageId, scope)
    override suspend fun messageContext(chatId: Long, messageId: Long) = conversationActions.context(chatId, messageId)
    override fun messagesVisible(chatId: Long, messageIds: List<Long>) = conversationEffects.visible(chatId, messageIds)
    override fun setAppForeground(foreground: Boolean) {
        conversationEffects.setForeground(foreground)
        _notificationPresentation.update { it.copy(foreground = foreground) }
    }
    override fun refreshInteractionPolicy() = conversationEffects.refreshPolicy()
    override val myProfile: StateFlow<ProfileState> get() = profiles.me
    override val bioLengthLimit: StateFlow<Int> get() = profiles.bioLimit
    override fun observeChatInfo(chatId: Long) = profiles.observe(chatId)
    override fun refreshChatInfo(chatId: Long) = profiles.refreshChat(chatId)
    override fun refreshMyProfile() = profiles.refreshMe()
    override suspend fun updateMyProfile(edit: ProfileEdit) = profiles.edit(edit)
    override suspend fun setMyProfilePhoto(photo: OutgoingMedia) = profiles.setPhoto(photo)

    /**
     * Промежуточный кэш чатов: TDLib отдаёт чаты через несколько обновлений,
     * поэтому собираем их здесь и переиздаём [_chats] при каждом изменении.
     */
    private val chatCache = ConcurrentHashMap<Long, ChatPreview>()

    /** Номер телефона, введённый пользователем — нужен для [WaitingForCode]. */
    private var submittedPhone: String = ""

    // ---- TDLib Client --------------------------------------------------------

    private val client: Client = run {
        // Don't let TDLib include API credentials or phone numbers in logcat.
        runCatching { Client.execute(TdApi.SetLogVerbosityLevel(if (BuildConfig.DEBUG) 2 else 0)) }
        Client.create(
            /* updateHandler          = */ Client.ResultHandler { update -> handleUpdate(update) },
            /* updateExceptionHandler = */ null,
            /* defaultExceptionHandler= */ null
        )
    }

    // The repository and this callback live for the process lifetime. One client owns the database.
    private val networkMonitor = TelegramNetworkMonitor(context.applicationContext, authScope) { type ->
        client.send(TdApi.SetNetworkType(type), Client.ResultHandler { result ->
            if (BuildConfig.DEBUG && result is TdApi.Error) {
                android.util.Log.w(TelegramNetworkMonitor.TAG, "SetNetworkType failed: ${result.code}")
            }
        })
    }.also { it.start() }

    // =========================================================================
    // Публичные методы репозитория
    // =========================================================================

    override fun startAuthorization() {
        // Клиент уже создан; TDLib сам отправит UpdateAuthorizationState.
        if (_authState.value !is AuthState.Ready) {
            _authState.value = AuthState.Loading
        }
    }

    override fun submitPhoneNumber(phone: String) {
        submittedPhone = phone
        _authState.value = AuthState.Loading
        startAuthTimeout()
        client.send(
            TdApi.SetAuthenticationPhoneNumber(phone, null),
            errorHandler()
        )
    }

    override fun submitCode(code: String) {
        _authState.value = AuthState.Loading
        startAuthTimeout()
        client.send(
            TdApi.CheckAuthenticationCode(code),
            errorHandler()
        )
    }

    override fun resendCode() {
        // Передаём null-причину — TDLib выберет способ сам (SMS/звонок и т.д.)
        client.send(
            TdApi.ResendAuthenticationCode(null),
            errorHandler()
        )
    }

    override fun submitPassword(password: String) {
        _authState.value = AuthState.Loading
        startAuthTimeout()
        client.send(
            TdApi.CheckAuthenticationPassword(password),
            errorHandler()
        )
    }

    override fun resetError() {
        authTimeoutJob?.cancel()
        _authState.value = AuthState.WaitingForPhone
    }

    override fun reconnect() {
        networkMonitor.reconnect()
        if (_authState.value == AuthState.ConnectionTimeout) {
            _authState.value = AuthState.Loading
            startAuthTimeout()
        }
    }

    override fun addProxy(
        server: String,
        port: Int,
        isMtproto: Boolean,
        username: String,
        password: String,
        secret: String
    ) {
        val type = if (isMtproto) {
            TdApi.ProxyTypeMtproto(secret.trim())
        } else {
            TdApi.ProxyTypeSocks5(username.trim(), password)
        }
        val proxy = TdApi.Proxy(server.trim(), port, type)
        client.send(TdApi.AddProxy(proxy, false, "BeyGram"), Client.ResultHandler { result ->
            when (result) {
                is TdApi.AddedProxy -> client.send(TdApi.EnableProxy(result.id), errorHandler())
                is TdApi.Error -> _authState.value = AuthState.Error(result.message)
            }
        })
    }

    override fun observeChatHistory(chatId: Long): StateFlow<ChatHistory> = messageHistory.observe(chatId)

    override fun observeFile(fileId: Int): StateFlow<MediaFileState> = mediaFiles.observe(fileId)

    override fun downloadFile(fileId: Int) {
        if (!mediaFiles.beginDownload(fileId)) return
        client.send(TdApi.DownloadFile(fileId, 16, 0, 0, false), Client.ResultHandler { result ->
            when (result) {
                is TdApi.File -> mediaFiles.update(result.toMediaFileState())
                is TdApi.Error -> mediaFiles.fail(fileId, "Не удалось загрузить файл: ${result.message}")
            }
        })
    }

    override fun openMessageContent(chatId: Long, messageId: Long) {
        conversationEffects.contentOpened(chatId, messageId)
    }

    override suspend fun sendMedia(chatId: Long, media: OutgoingMedia, caption: String, replyToMessageId: Long?): Result<Unit> =
        conversationActions.sendMedia(chatId, media, caption, replyToMessageId)

    override fun openChat(chatId: Long) {
        _notificationPresentation.update { it.copy(openedChats = it.openedChats + chatId) }
        conversationEffects.opened(chatId)
        client.send(TdApi.OpenChat(chatId), Client.ResultHandler { })
        if (messageHistory.beginLoad(chatId)) requestHistory(chatId, fromMessageId = 0L, remaining = 50, pagesLeft = 3)
    }

    override fun closeChat(chatId: Long) {
        _notificationPresentation.update { it.copy(openedChats = it.openedChats - chatId) }
        conversationEffects.closed(chatId)
        client.send(TdApi.CloseChat(chatId), Client.ResultHandler { })
    }

    override fun loadOlderMessages(chatId: Long) {
        val oldest = messageHistory.observe(chatId).value.messages.firstOrNull()?.id ?: 0L
        if (messageHistory.beginLoad(chatId)) requestHistory(chatId, oldest, remaining = 50, pagesLeft = 3)
    }

    private fun requestHistory(chatId: Long, fromMessageId: Long, remaining: Int, pagesLeft: Int) {
        val requestVersion = messageHistory.version()
        client.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, 50, false), Client.ResultHandler { result ->
            when (result) {
                is TdApi.Messages -> {
                    val page = result.messages.orEmpty().filterNot { it.id == fromMessageId }
                    val nextFrom = page.lastOrNull()?.id ?: fromMessageId
                    val more = page.isNotEmpty()
                    val continueLoading = more && nextFrom != fromMessageId && remaining > page.size && pagesLeft > 1
                    messageHistory.finishLoad(chatId, page.map { it.toChatMessage() }, more, continueLoading, requestVersion)
                    page.forEach(conversationActions::hydrateReply)
                    if (BuildConfig.DEBUG) android.util.Log.i(TelegramNetworkMonitor.TAG, "History page received: ${page.size} messages")
                    if (continueLoading) requestHistory(chatId, nextFrom, remaining - page.size, pagesLeft - 1)
                }
                is TdApi.Error -> messageHistory.fail(chatId, "Не удалось загрузить историю: ${result.message}")
            }
        })
    }

    override suspend fun sendMessage(chatId: Long, text: String, replyToMessageId: Long?): Result<Unit> =
        conversationActions.sendText(chatId, text, replyToMessageId)

    // =========================================================================
    // Обработка обновлений TDLib
    // =========================================================================

    private fun handleUpdate(update: TdApi.Object) {
        profiles.handle(update)
        notificationFeed.handle(update)
        when (update) {
            is TdApi.UpdateAuthorizationState -> handleAuthorizationState(update.authorizationState)
            is TdApi.UpdateNewChat            -> handleNewChat(update.chat)
            is TdApi.UpdateChatLastMessage    -> handleChatLastMessage(update.chatId, update.lastMessage, update.positions)
            is TdApi.UpdateChatPosition -> handleChatPosition(update.chatId, update.position)
            is TdApi.UpdateChatDraftMessage -> {
                chatCache[update.chatId]?.let { chatCache[update.chatId] = it.withMainPosition(update.positions) }
                publishChats()
            }
            is TdApi.UpdateChatReadInbox -> {
                messageHistory.readInbox(update.chatId, update.lastReadInboxMessageId)
                chatCache[update.chatId]?.let { chatCache[update.chatId] = it.copy(unreadCount = update.unreadCount) }
                publishChats()
            }
            is TdApi.UpdateChatReadOutbox -> messageHistory.readOutbox(update.chatId, update.lastReadOutboxMessageId)
            is TdApi.UpdateMessageContent -> messageHistory.update(update.chatId, update.messageId) { message ->
                val attachment = mediaMapper.media(update.newContent)
                message.copy(text = contentText(update.newContent, attachment != null), media = attachment)
            }
            is TdApi.UpdateMessageEdited -> messageHistory.update(update.chatId, update.messageId) { it.copy(isEdited = update.editDate > 0) }
            is TdApi.UpdateOption -> conversationActions.option(update.name, update.value)
            is TdApi.UpdateChatTitle          -> handleChatTitleChange(update.chatId, update.title)
            is TdApi.UpdateChatPhoto -> {
                chatCache[update.chatId]?.let { chatCache[update.chatId] = it.copy(photo = profiles.photo(update.photo)) }
                publishChats()
            }
            is TdApi.UpdateNewMessage         -> handleNewMessage(update.message)
            is TdApi.UpdateFile -> mediaFiles.update(update.file.toMediaFileState())
            is TdApi.UpdateMessageSendSucceeded -> {
                messageHistory.put(update.message.toChatMessage(), update.oldMessageId)
                conversationActions.hydrateReply(update.message)
            }
            is TdApi.UpdateMessageSendFailed -> messageHistory.put(update.message.toChatMessage(), update.oldMessageId)
            is TdApi.UpdateDeleteMessages -> if (update.isPermanent) messageHistory.delete(update.chatId, update.messageIds.toList())
            is TdApi.UpdateConnectionState    -> {
                if (BuildConfig.DEBUG) android.util.Log.i(TelegramNetworkMonitor.TAG, "TDLib connection: ${update.state.javaClass.simpleName}")
                _connectionState.value = when (update.state) {
                    is TdApi.ConnectionStateReady -> TelegramConnectionState.Connected
                    is TdApi.ConnectionStateConnecting,
                    is TdApi.ConnectionStateConnectingToProxy -> TelegramConnectionState.Connecting
                    is TdApi.ConnectionStateUpdating -> TelegramConnectionState.Updating
                    is TdApi.ConnectionStateWaitingForNetwork -> TelegramConnectionState.WaitingForNetwork
                    else -> TelegramConnectionState.Unknown
                }
            }
            else                              -> Unit
        }
    }

    // ---- Авторизация ---------------------------------------------------------

    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        if (BuildConfig.DEBUG) android.util.Log.i(TelegramNetworkMonitor.TAG, "TDLib authorization: ${state.javaClass.simpleName}")
        authTimeoutJob?.cancel()
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                client.send(buildTdLibParameters(), errorHandler())
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _authState.value = AuthState.WaitingForPhone
            }
            is TdApi.AuthorizationStateWaitCode -> {
                _authState.value = AuthState.WaitingForCode(submittedPhone)
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                _authState.value = AuthState.WaitingForPassword(state.passwordHint.orEmpty())
            }
            is TdApi.AuthorizationStateReady -> {
                _authState.value = AuthState.Ready
                loadChats()
                profiles.refreshMe()
                conversationActions.initialize()
                conversationEffects.authorized()
                notificationFeed.initialize()
            }
            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosed -> {
                _authState.value = AuthState.WaitingForPhone
            }
            else -> Unit
        }
    }

    private fun buildTdLibParameters(): TdApi.SetTdlibParameters =
        TdApi.SetTdlibParameters(
            /* useTestDc             = */ false,
            /* databaseDirectory     = */ config.databaseDirectory,
            /* filesDirectory        = */ config.filesDirectory,
            /* databaseEncryptionKey = */ ByteArray(0),
            /* useFileDatabase       = */ true,
            /* useChatInfoDatabase   = */ true,
            /* useMessageDatabase    = */ config.useMessageDatabase,
            /* useSecretChats        = */ config.useSecretChats,
            /* apiId                 = */ config.apiId,
            /* apiHash               = */ config.apiHash,
            /* systemLanguageCode    = */ config.systemLanguageCode,
            /* deviceModel           = */ "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            /* systemVersion         = */ Build.VERSION.RELEASE,
            /* applicationVersion    = */ config.applicationVersion
        )

    // ---- Чаты ----------------------------------------------------------------

    /**
     * Запрашиваем у TDLib список чатов (основной список, лимит 100).
     * TDLib будет присылать [TdApi.UpdateNewChat] для каждого чата.
     */
    private fun loadChats() {
        client.send(
            TdApi.LoadChats(TdApi.ChatListMain(), 100),
            Client.ResultHandler { /* LoadChats не возвращает полезного ответа */ }
        )
    }

    private fun handleNewChat(chat: TdApi.Chat) {
        profiles.putChat(chat)
        notificationFeed.chat(chat)
        messageHistory.readInbox(chat.id, chat.lastReadInboxMessageId)
        messageHistory.readOutbox(chat.id, chat.lastReadOutboxMessageId)
        val preview = chat.toChatPreview() ?: return
        chatCache[chat.id] = preview
        publishChats()
    }

    private fun handleChatLastMessage(chatId: Long, lastMessage: TdApi.Message?, positions: Array<TdApi.ChatPosition>) {
        val existing = chatCache[chatId] ?: return
        chatCache[chatId] = existing.copy(
            lastMessage = lastMessage?.toTextPreview().orEmpty(),
            lastMessageTime = lastMessage?.toTimeString().orEmpty()
        ).withMainPosition(positions)
        publishChats()
    }

    private fun ChatPreview.withMainPosition(positions: Array<TdApi.ChatPosition>): ChatPreview {
        val main = positions.firstOrNull { it.list is TdApi.ChatListMain }
        return copy(order = main?.order ?: 0L, isPinned = main?.isPinned == true)
    }

    private fun handleChatPosition(chatId: Long, position: TdApi.ChatPosition) {
        if (position.list !is TdApi.ChatListMain) return
        val existing = chatCache[chatId] ?: return
        chatCache[chatId] = existing.copy(order = position.order, isPinned = position.isPinned)
        publishChats()
    }

    private fun handleChatTitleChange(chatId: Long, title: String) {
        val existing = chatCache[chatId] ?: return
        chatCache[chatId] = existing.copy(title = title)
        publishChats()
    }

    private fun handleNewMessage(message: TdApi.Message) {
        messageHistory.put(message.toChatMessage())
        conversationActions.hydrateReply(message)
    }

    private fun TdApi.Message.toChatMessage(): ChatMessage {
        val attachment = mediaMapper.media(content)
        return ChatMessage(
        id = id,
        chatId = chatId,
        text = contentText(content, attachment != null),
        time = toTimeString(),
        isOutgoing = isOutgoing,
        dateSeconds = date.toLong(),
        sendState = when (sendingState) {
            is TdApi.MessageSendingStatePending -> MessageSendState.Sending
            is TdApi.MessageSendingStateFailed -> MessageSendState.Failed
            else -> MessageSendState.Sent
        },
        media = attachment,
        senderName = profiles.senderName(senderId),
        isEdited = editDate > 0,
        reply = (replyTo as? TdApi.MessageReplyToMessage)?.let { reply ->
            MessageReply(reply.chatId.takeIf { it != 0L } ?: chatId, reply.messageId,
                reply.quote?.text?.text?.takeIf { it.isNotBlank() } ?: reply.content?.let { contentText(it, mediaMapper.media(it) != null).ifBlank { "Медиа" } } ?: "Сообщение")
        }
    )
    }

    /** Main-list ordering comes from Telegram, including pins and draft activity. */
    private fun publishChats() {
        _chats.value = orderedMainChats(chatCache.values)
        chatStates.forEach { (id, flow) -> flow.value = chatCache[id] }
    }

    // =========================================================================
    // Конвертеры TdApi → domain models
    // =========================================================================

    private fun TdApi.Chat.toChatPreview(): ChatPreview? {
        val chatTitle = title.ifBlank { return null }
        val mainPosition = positions.orEmpty().firstOrNull { it.list is TdApi.ChatListMain }
        return ChatPreview(
            id = id,
            title = chatTitle,
            initials = chatTitle.initials(),
            lastMessage = lastMessage?.toTextPreview() ?: "",
            lastMessageTime = lastMessage?.toTimeString() ?: "",
            unreadCount = unreadCount,
            avatarColorArgb = id.stableColor(),
            isGroup = type is TdApi.ChatTypeBasicGroup || type is TdApi.ChatTypeSupergroup,
            isPinned = mainPosition?.isPinned == true,
            isMuted = notificationSettings?.muteFor?.let { it > 0 } == true,
            order = mainPosition?.order ?: 0L,
            photo = profiles.photo(photo)
        )
    }

    private fun contentText(content: TdApi.MessageContent, hasMedia: Boolean): String =
        if (hasMedia) mediaMapper.caption(content) else previewText(content)

    private fun TdApi.Message.toTextPreview(): String = previewText(content)

    private fun previewText(c: TdApi.MessageContent): String =
        when (c) {
            is TdApi.MessageText      -> c.text.text
            is TdApi.MessagePhoto     -> "\uD83D\uDCF7 Фото"
            is TdApi.MessageVideo     -> "\uD83C\uDFA5 Видео"
            is TdApi.MessageAudio     -> "\uD83C\uDFB5 Аудио"
            is TdApi.MessageDocument  -> "\uD83D\uDCC4 Документ"
            is TdApi.MessageSticker   -> "${c.sticker.emoji} Стикер"
            is TdApi.MessageVoiceNote -> "\uD83C\uDFA4 Голосовое"
            else                      -> "Сообщение"
        }

    private fun TdApi.Message.toTimeString(): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(date * 1000L))

    // =========================================================================
    // Утилиты
    // =========================================================================

    /**
     * Возвращает обработчик ошибок для запросов TDLib.
     * Если TDLib вернул [TdApi.Error], обновляем [_authState].
     */
    private fun errorHandler(): Client.ResultHandler =
        Client.ResultHandler { result ->
            if (result is TdApi.Error) {
                authTimeoutJob?.cancel()
                _authState.value = AuthState.Error(result.message)
            }
        }

    private fun startAuthTimeout() {
        authTimeoutJob?.cancel()
        authTimeoutJob = authScope.launch {
            delay(AUTH_REQUEST_TIMEOUT_MS)
            if (_authState.value is AuthState.Loading) {
                _authState.value = AuthState.ConnectionTimeout
            }
        }
    }

    companion object {
        private const val AUTH_REQUEST_TIMEOUT_MS = 35_000L
        @Volatile private var instance: TdLibTelegramRepository? = null

        fun getInstance(context: Context, policy: InteractionPolicy = InteractionPolicy.Standard): TdLibTelegramRepository =
            instance ?: synchronized(this) {
                instance ?: TdLibTelegramRepository(context.applicationContext, policy).also { instance = it }
            }
    }
}

// ---- Строковые утилиты ------------------------------------------------------

/** Первые буквы первых двух слов. */
private fun String.initials(): String =
    trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { take(2).uppercase() }

/** Стабильный цвет из chatId — один из семи Telegram-оттенков. */
private val CHAT_COLORS = longArrayOf(
    0xFF2AABEE, 0xFF5F9FE0, 0xFF14B8A6,
    0xFF8B5CF6, 0xFFEF4444, 0xFFF59E0B, 0xFF10B981
)

private fun Long.stableColor(): Long {
    val idx = (this % CHAT_COLORS.size).toInt()
    return CHAT_COLORS[if (idx < 0) idx + CHAT_COLORS.size else idx]
}
