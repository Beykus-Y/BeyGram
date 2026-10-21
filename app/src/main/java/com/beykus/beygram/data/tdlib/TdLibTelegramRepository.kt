package com.beykus.beygram.data.tdlib

import android.content.Context
import android.os.Build
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.ChatActivity
import com.beykus.beygram.domain.model.ChatMembersPage
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.GlobalSearchFilter
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MediaPosition
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.MessageDraft
import com.beykus.beygram.domain.model.BotKeyboard
import com.beykus.beygram.domain.model.BotCallbackAnswer
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.data.media.VideoNoteSendTracking
import com.beykus.beygram.data.archive.ArchiveSettingsStore
import com.beykus.beygram.data.storage.StorageSettingsStore
import com.beykus.beygram.domain.model.CachePolicy
import com.beykus.beygram.domain.model.StorageCategory
import com.beykus.beygram.domain.model.StorageUsage
import com.beykus.beygram.domain.model.ProfileEdit
import com.beykus.beygram.domain.model.ProfileState
import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.FolderChatPosition
import com.beykus.beygram.domain.model.TelegramChatFolders
import com.beykus.beygram.domain.model.TelegramFolderDraft
import com.beykus.beygram.domain.model.TelegramFolderLoadState
import com.beykus.beygram.domain.model.orderedTelegramFolderChats
import com.beykus.beygram.domain.model.GroupCreationState
import com.beykus.beygram.domain.model.mayBeginGroupCreation
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.orderedArchivedChats
import com.beykus.beygram.domain.model.orderedMainChats
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.InteractionPolicy
import com.beykus.beygram.domain.model.DeleteMessageScope
import com.beykus.beygram.domain.model.MessageReply
import com.beykus.beygram.domain.model.NotificationPresentation
import com.beykus.beygram.domain.model.SavedTelegramProxy
import com.beykus.beygram.domain.model.SharedMediaKind
import com.beykus.beygram.domain.model.TelegramRoute
import com.beykus.beygram.domain.model.TelegramProxyDraft
import com.beykus.beygram.domain.model.TelegramProxyKind
import com.beykus.beygram.data.vpn.LoopbackProxyCredentials
import com.beykus.beygram.data.vpn.MihomoProxyClient
import com.beykus.beygram.diagnostics.ConnectionEventLog
import com.beykus.beygram.diagnostics.StructuredEventLog
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.domain.repository.TelegramRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.net.InetSocketAddress
import java.net.Socket
import java.io.File
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

    private val appContext = context.applicationContext
    private val proxyMutex = Mutex()
    private val mihomoProxyPrefs = context.applicationContext.getSharedPreferences("mihomo_tdlib_proxy", Context.MODE_PRIVATE)
    private val loopbackCredentials = LoopbackProxyCredentials.get(context.applicationContext)
    private val tdRequests = TdRequests { client }
    private val videoNoteLossTransport = TdVideoNoteLossTransport(tdRequests)
    private val messageDrafts = TdChatDrafts(appContext) { client }

    // ---- Прокси Telegram -----------------------------------------------------

    override suspend fun loadSavedProxies(): Result<List<SavedTelegramProxy>> = requestResult {
        tdRequests(TdApi.GetProxies()).proxies.orEmpty()
            .filterNot { it.isMihomoEntry() }
            .mapNotNull { it.toSavedProxy() }
    }

    override suspend fun addProxy(draft: TelegramProxyDraft, enable: Boolean): Result<Unit> = proxyMutex.withLock {
        requestResult {
            tdRequests(TdApi.AddProxy(draft.toTdProxy(), enable, USER_PROXY_COMMENT))
            Unit
        }
    }

    override suspend fun enableProxy(id: Int): Result<Unit> = proxyMutex.withLock {
        requestResult { tdRequests(TdApi.EnableProxy(id)); Unit }
    }

    override suspend fun disableProxy(): Result<Unit> = proxyMutex.withLock {
        requestResult { tdRequests(TdApi.DisableProxy()); Unit }
    }

    override suspend fun removeProxy(id: Int): Result<Unit> = proxyMutex.withLock {
        requestResult { tdRequests(TdApi.RemoveProxy(id)); Unit }
    }

    override suspend fun currentRoute(): Result<TelegramRoute> = requestResult<TelegramRoute> {
        val enabled = tdRequests(TdApi.GetProxies()).proxies.orEmpty().firstOrNull { it.isEnabled }
        when {
            enabled == null -> TelegramRoute.Direct
            enabled.isMihomoEntry() -> TelegramRoute.Subscription
            else -> {
                val proxy = enabled.toSavedProxy() ?: error("Unsupported proxy type")
                TelegramRoute.Proxy(proxy.kind, proxy.server, proxy.port)
            }
        }
    }

    override suspend fun isMihomoProxyEnabled(): Result<Boolean> =
        requestResult { tdRequests(TdApi.GetProxies()).proxies.orEmpty().any { it.isEnabled && it.isMihomoEntry() } }

    override suspend fun measureProxyLatency(server: String, port: Int): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val started = System.nanoTime()
            Socket().use { it.connect(InetSocketAddress(server.trim(), port), PROXY_PING_TIMEOUT_MS) }
            Result.success((System.nanoTime() - started) / 1_000_000L)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(UserMessageException(UiText.Res(R.string.err_proxy_no_response), error))
        }
    }

    /** Routes TDLib through BeyGram's local Mihomo listener, or restores the proxy that was active before it. */
    override suspend fun setMihomoProxyEnabled(enabled: Boolean): Result<Unit> = proxyMutex.withLock {
        requestResult {
            val proxies = tdRequests(TdApi.GetProxies()).proxies.orEmpty().toList()
            val current = proxies.firstOrNull { it.isEnabled }
            if (enabled) {
                val localId = ensureMihomoEntry(proxies)
                // Remember a manual proxy (or direct mode) once, so a repeated enable keeps the original choice.
                if (current == null || !current.isMihomoEntry()) {
                    mihomoProxyPrefs.edit().putInt(KEY_PREVIOUS_PROXY_ID, current?.id ?: 0).apply()
                }
                tdRequests(TdApi.EnableProxy(localId))
            } else {
                // Only undo the local entry; a manual proxy the user chose later is left untouched.
                if (current?.isMihomoEntry() == true) {
                    val previousId = mihomoProxyPrefs.getInt(KEY_PREVIOUS_PROXY_ID, 0)
                    val previous = proxies.firstOrNull { it.id == previousId && !it.isMihomoEntry() }
                    if (previous != null) tdRequests(TdApi.EnableProxy(previous.id)) else tdRequests(TdApi.DisableProxy())
                }
                mihomoProxyPrefs.edit().remove(KEY_PREVIOUS_PROXY_ID).apply()
            }
            Unit
        }
    }

    /** Returns the id of BeyGram's local SOCKS entry with the current port and credentials; an outdated entry is replaced. */
    private suspend fun ensureMihomoEntry(proxies: List<TdApi.AddedProxy>): Int {
        val existing = proxies.firstOrNull { it.isMihomoEntry() }
        val type = existing?.proxy?.type as? TdApi.ProxyTypeSocks5
        if (existing != null && type != null && existing.proxy.port == MihomoProxyClient.PORT &&
            type.username == loopbackCredentials.username && type.password == loopbackCredentials.password
        ) {
            return existing.id
        }
        if (existing != null) tdRequests(TdApi.RemoveProxy(existing.id))
        val expected = TdApi.ProxyTypeSocks5(loopbackCredentials.username, loopbackCredentials.password)
        return tdRequests(TdApi.AddProxy(TdApi.Proxy(MihomoProxyClient.HOST, MihomoProxyClient.PORT, expected), false, MIHOMO_PROXY_COMMENT)).id
    }

    /**
     * Only the entry BeyGram created: a user's own SOCKS5 proxy on the same port has a different comment. The port is not
     * compared, so an entry saved with an earlier listener port is still recognised and replaced by [ensureMihomoEntry].
     */
    private fun TdApi.AddedProxy.isMihomoEntry(): Boolean =
        comment == MIHOMO_PROXY_COMMENT && proxy.server == MihomoProxyClient.HOST && proxy.type is TdApi.ProxyTypeSocks5

    private fun TdApi.AddedProxy.toSavedProxy(): SavedTelegramProxy? {
        val kind = when (proxy.type) {
            is TdApi.ProxyTypeMtproto -> TelegramProxyKind.MTPROTO
            is TdApi.ProxyTypeSocks5 -> TelegramProxyKind.SOCKS5
            is TdApi.ProxyTypeHttp -> TelegramProxyKind.HTTP
            else -> return null
        }
        return SavedTelegramProxy(id = id, kind = kind, server = proxy.server, port = proxy.port, enabled = isEnabled)
    }

    private fun TelegramProxyDraft.toTdProxy(): TdApi.Proxy {
        val host = server.trim()
        if (host.isEmpty()) throw UserMessageException(UiText.Res(R.string.err_proxy_server_empty))
        if (port !in 1..65_535) throw UserMessageException(UiText.Res(R.string.err_proxy_port_invalid, listOf(1, 65_535)))
        val type: TdApi.ProxyType = when (kind) {
            TelegramProxyKind.MTPROTO -> {
                if (secret.isBlank()) throw UserMessageException(UiText.Res(R.string.err_proxy_secret_empty))
                TdApi.ProxyTypeMtproto(secret.trim())
            }
            TelegramProxyKind.SOCKS5 -> TdApi.ProxyTypeSocks5(username.trim(), password)
            TelegramProxyKind.HTTP -> TdApi.ProxyTypeHttp(username.trim(), password, false)
        }
        return TdApi.Proxy(host, port, type)
    }


    private val config = TdLibConfig.from(context)

    // ---- Реактивное состояние ------------------------------------------------

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatPreview>>(emptyList())
    override val chats: StateFlow<List<ChatPreview>> = _chats.asStateFlow()

    private val _archivedChats = MutableStateFlow<List<ChatPreview>>(emptyList())
    override val archivedChats: StateFlow<List<ChatPreview>> = _archivedChats.asStateFlow()

    private val _chatFolders = MutableStateFlow(TelegramChatFolders())
    override val chatFolders: StateFlow<TelegramChatFolders> = _chatFolders.asStateFlow()
    private val _folderChats = MutableStateFlow<Map<Int, List<ChatPreview>>>(emptyMap())
    override val folderChats: StateFlow<Map<Int, List<ChatPreview>>> = _folderChats.asStateFlow()
    private val _folderLoadStates = MutableStateFlow<Map<Int, TelegramFolderLoadState>>(emptyMap())
    override val folderLoadStates: StateFlow<Map<Int, TelegramFolderLoadState>> = _folderLoadStates.asStateFlow()
    /** Real server counts can arrive before updateChatFolders or before a folder is opened. */
    private val folderUnreadCount = ConcurrentHashMap<Int, Int>()
    private val folderPageLocks = ConcurrentHashMap<Int, Mutex>()

    private val _connectionState = MutableStateFlow(TelegramConnectionState.Unknown)
    override val connectionState: StateFlow<TelegramConnectionState> = _connectionState.asStateFlow()

    private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var authTimeoutJob: Job? = null

    // ---- Вспомогательные структуры -------------------------------------------

    /** Кэш сообщений по chatId. */
    private val pinnedVersions = ConcurrentHashMap<Long, MutableStateFlow<Long>>()
    override fun observePinnedMessagesVersion(chatId: Long): StateFlow<Long> = pinnedVersions.getOrPut(chatId) { MutableStateFlow(0L) }.asStateFlow()
    override suspend fun pinnedMessages(chatId: Long, fromMessageId: Long, forumTopicId: Long) = conversationActions.pinnedMessages(chatId, fromMessageId, forumTopicId)
    override fun observeMessageDraft(chatId: Long): StateFlow<MessageDraft> = messageDrafts.observe(chatId)
    override fun observeForumTopicDraft(chatId: Long, forumTopicId: Long): StateFlow<MessageDraft> = messageDrafts.observeTopic(chatId, forumTopicId)
    override fun updateForumTopicDraft(chatId: Long, forumTopicId: Long, transform: (MessageDraft) -> MessageDraft) =
        messageDrafts.updateTopic(chatId, forumTopicId, transform)
    override fun updateMessageDraft(chatId: Long, transform: (MessageDraft) -> MessageDraft) =
        messageDrafts.update(chatId, transform)
    private fun invalidatePins(chatId: Long) { pinnedVersions[chatId]?.update { it + 1 } }

    private val messageHistory = MessageHistoryStore()
    // A forum topic is a separate history even though all of its messages have the parent chatId.
    private val topicStores = ConcurrentHashMap<Pair<Long, Long>, MessageHistoryStore>()
    private fun topicStore(chatId: Long, topicId: Long): MessageHistoryStore =
        topicStores.computeIfAbsent(chatId to topicId) { MessageHistoryStore(maxCachedChats = 1) }
    private fun forumId(message: TdApi.Message): Long =
        (message.topicId as? TdApi.MessageTopicForum)?.forumTopicId?.toLong() ?: 0L
    private fun putForumMessage(message: TdApi.Message, replaces: Long? = null) {
        val topicId = forumId(message)
        if (topicId > 0L) topicStore(message.chatId, topicId).put(message.toChatMessage(), replaces)
    }
    private fun updateForumMessage(chatId: Long, messageId: Long, transform: (ChatMessage) -> ChatMessage) {
        topicStores.filterKeys { it.first == chatId }.values.forEach { it.update(chatId, messageId, transform) }
    }
    private val mediaFiles = MediaFileStore()
    private val mediaMapper = TdMediaMapper(mediaFiles)
    private val serviceMessages = TdServiceMessageMapper(appContext)
    private val pendingServiceUserRequests = ConcurrentHashMap.newKeySet<Triple<Long, Long, Long>>()

    private fun hydrateServiceMember(message: TdApi.Message, userId: Long) {
        if (userId <= 0L || profiles.senderName(TdApi.MessageSenderUser(userId)).isNotBlank()) return
        val requestKey = Triple(message.chatId, message.id, userId)
        if (!pendingServiceUserRequests.add(requestKey)) return
        client.send(TdApi.GetUser(userId), Client.ResultHandler { result ->
            pendingServiceUserRequests.remove(requestKey)
            if (result is TdApi.User) {
                profiles.rememberServiceUser(result)
                val updated = serviceMessages.event(message) {
                    profiles.senderName(TdApi.MessageSenderUser(it))
                } ?: return@ResultHandler
                val transform: (ChatMessage) -> ChatMessage = {
                    it.copy(text = updated.text, serviceText = updated.text, serviceUserId = updated.userId)
                }
                messageHistory.update(message.chatId, message.id, transform)
                updateForumMessage(message.chatId, message.id, transform)
                // The service event can also be the chat's last message. Update its list preview,
                // but never overwrite a newer last message received during the async GetUser call.
                val currentPreview = chatCache[message.chatId]
                if (currentPreview?.lastMessageId == message.id) {
                    val rendered = UiText.Raw(updated.text)
                    if (currentPreview.lastMessage != rendered) {
                        chatCache[message.chatId] = currentPreview.copy(lastMessage = rendered)
                        publishChat(message.chatId)
                    }
                }
            }
        })
    }
    private val profiles = TdProfiles({ client }, mediaFiles)
    /** Chats being moved back from the archive: their alerts are not archived while the move is in flight. */
    private val returningFromArchive = ConcurrentHashMap.newKeySet<Long>()
    private val notificationFeed = TdNotifications({ client }, profiles::senderName) { chatId ->
        chatId !in returningFromArchive && chatCache[chatId]?.archiveOrder?.let { it > 0L } == true
    }
    override val notifications get() = notificationFeed.state
    private val _notificationPresentation = MutableStateFlow(NotificationPresentation())
    override val notificationPresentation = _notificationPresentation.asStateFlow()
    override suspend fun dismissNotifications(groupId: Int, maxNotificationId: Int) = notificationFeed.dismiss(groupId, maxNotificationId)
    private val chatStates = ConcurrentHashMap<Long, MutableStateFlow<ChatPreview?>>()
    override fun observeChat(chatId: Long): StateFlow<ChatPreview?> = chatStates.computeIfAbsent(chatId) { MutableStateFlow(chatCache[it]?.withMute()) }.asStateFlow()
    override suspend fun loadChat(chatId: Long): Result<Unit> = requestResult {
        handleNewChat(TdRequests { client }(TdApi.GetChat(chatId))); Unit
    }
    override suspend fun setChatArchived(chatId: Long, archived: Boolean): Result<Unit> = requestResult {
        val list: TdApi.ChatList = if (archived) TdApi.ChatListArchive() else TdApi.ChatListMain()
        TdRequests { client }(TdApi.AddChatToList(chatId, list)); Unit
    }

    /** Pinning in a folder must never modify the Main-list pinned order. */
    override suspend fun setFolderChatPinned(folderId: Int, chatId: Long, pinned: Boolean): Result<Unit> = requestResult {
        require(_chatFolders.value.folders.any { it.id == folderId }) { "Folder no longer exists" }
        tdRequests(TdApi.ToggleChatIsPinned(TdApi.ChatListFolder(folderId), chatId, pinned))
        Unit
    }

    /** Stable server snapshot: an edit never silently reverts another device's newer changes. */
    private fun TdApi.ChatFolder.folderStamp(): String = listOf(
        tdFolderDisplayName(name), icon?.name.orEmpty(), colorId, isShareable,
        pinnedChatIds?.joinToString(",").orEmpty(),
        includedChatIds?.joinToString(",").orEmpty(),
        excludedChatIds?.joinToString(",").orEmpty(),
        excludeMuted, excludeRead, excludeArchived,
        includeContacts, includeNonContacts, includeBots, includeGroups, includeChannels
    ).joinToString(separator = "\u001f")

    /** Read rules from TDLib each time the editor opens, including explicitly included/excluded IDs. */
    override suspend fun getFolderDraft(folderId: Int): Result<TelegramFolderDraft> = requestResult {
        require(_chatFolders.value.folders.any { it.id == folderId }) { "Folder no longer exists" }
        val f = tdRequests(TdApi.GetChatFolder(folderId))
        TelegramFolderDraft(
            id = folderId,
            title = tdFolderDisplayName(f.name),
            isShareable = f.isShareable,
            baselineFingerprint = f.folderStamp(),
            includeContacts = f.includeContacts,
            includeNonContacts = f.includeNonContacts,
            includeBots = f.includeBots,
            includeGroups = f.includeGroups,
            includeChannels = f.includeChannels,
            excludeMuted = f.excludeMuted,
            excludeRead = f.excludeRead,
            excludeArchived = f.excludeArchived,
            includedChatIds = f.includedChatIds?.toSet() ?: emptySet<Long>(),
            excludedChatIds = f.excludedChatIds?.toSet() ?: emptySet<Long>()
        )
    }

    /**
     * Edit by re-reading the original server folder, preserving icons, color, custom emoji in
     * unchanged names, sharing metadata and existing pinned order. Never overwrite an old snapshot.
     */
    override suspend fun saveFolderDraft(draft: TelegramFolderDraft): Result<Int> = requestResult {
        require(draft.valid()) { "Folder name (1-12 characters) and included chats are required" }
        val original = draft.id?.let { tdRequests(TdApi.GetChatFolder(it)) }
        if (original != null && (draft.baselineFingerprint == null ||
                    original.folderStamp() != draft.baselineFingerprint)) {
            throw UserMessageException(UiText.Res(R.string.folder_changed_while_editing))
        }
        val folder = original ?: TdApi.ChatFolder().apply {
            colorId = -1
            isShareable = false
            pinnedChatIds = longArrayOf()
        }
        if (original == null || tdFolderDisplayName(original.name) != draft.title.trim()) {
            folder.name = TdApi.ChatFolderName(
                TdApi.FormattedText(draft.title.trim(), emptyArray()),
                false
            )
        }
        // A chat can't remain pinned in a folder after being explicitly excluded.
        folder.pinnedChatIds = folder.pinnedChatIds?.filterNot { it in draft.excludedChatIds }?.toLongArray() ?: longArrayOf()
        folder.includedChatIds = draft.includedChatIds.toLongArray()
        folder.excludedChatIds = draft.excludedChatIds.toLongArray()
        folder.excludeMuted = draft.excludeMuted
        folder.excludeRead = draft.excludeRead
        folder.excludeArchived = draft.excludeArchived
        folder.includeContacts = draft.includeContacts
        folder.includeNonContacts = draft.includeNonContacts
        folder.includeBots = draft.includeBots
        folder.includeGroups = draft.includeGroups
        folder.includeChannels = draft.includeChannels
        if (draft.id != null) {
            tdRequests(TdApi.EditChatFolder(draft.id, folder)).id
        } else {
            tdRequests(TdApi.CreateChatFolder(folder)).id
        }
    }

    /** Remove the folder only; never leave or delete any of its Telegram chats. */
    override suspend fun deleteServerFolder(folderId: Int): Result<Unit> = requestResult {
        require(_chatFolders.value.folders.any { it.id == folderId }) { "Folder no longer exists" }
        tdRequests(TdApi.DeleteChatFolder(folderId, longArrayOf()))
        Unit
    }

    /** Telegram validates Premium limits. All folder IDs must be included exactly once. */
    override suspend fun reorderServerFolders(ids: List<Int>, mainPosition: Int): Result<Unit> = requestResult {
        val state = _chatFolders.value
        require(ids.size == ids.distinct().size && ids.toSet() == state.folders.map { it.id }.toSet()) {
            "Folder list changed; refresh and retry"
        }
        require(mainPosition == state.mainChatListPosition) { "Main tab position changed" }
        tdRequests(TdApi.ReorderChatFolders(ids.toIntArray(), mainPosition))
        Unit
    }

    /** TDLib is the cursor owner: each LoadChats call fetches the next server-backed page. */
    override suspend fun loadMoreFolderChats(folderId: Int): Result<Unit> = requestResult {
        require(folderId > 0 && _chatFolders.value.folders.any { it.id == folderId }) {
            "Telegram folder no longer exists"
        }
        folderPageLocks.computeIfAbsent(folderId) { Mutex() }.withLock {
            if (_folderLoadStates.value[folderId]?.hasMore == false ||
                _chatFolders.value.folders.none { it.id == folderId }) return@withLock
            setFolderLoading(folderId, TelegramFolderLoadState(loading = true))
            try {
                tdRequests(TdApi.LoadChats(TdApi.ChatListFolder(folderId), 100))
                setFolderLoading(folderId, TelegramFolderLoadState(hasMore = true))
            } catch (notFound: TdRequestError) {
                if (notFound.code == 404) {
                    setFolderLoading(folderId, TelegramFolderLoadState(hasMore = false))
                } else {
                    setFolderLoading(folderId, TelegramFolderLoadState(error = true))
                    throw notFound
                }
            } catch (cancelled: CancellationException) {
                setFolderLoading(folderId, TelegramFolderLoadState(error = true))
                throw cancelled
            } catch (error: Exception) {
                setFolderLoading(folderId, TelegramFolderLoadState(error = true))
                throw error
            }
        }
    }

    private fun setFolderLoading(folderId: Int, state: TelegramFolderLoadState) {
        // Ignore a response for a folder removed remotely while a page was loading.
        if (_chatFolders.value.folders.none { it.id == folderId }) return
        _folderLoadStates.update { it + (folderId to state) }
    }

    override suspend fun setChatPinned(chatId: Long, pinned: Boolean): Result<Unit> = requestResult {
        tdRequests(TdApi.ToggleChatIsPinned(TdApi.ChatListMain(), chatId, pinned))
        Unit
    }

    override suspend fun setChatMuted(chatId: Long, muted: Boolean): Result<Unit> = requestResult {
        val chat = tdRequests(TdApi.GetChat(chatId))
        // Reuse the existing chat settings to preserve previews, sounds and mentions.
        val settings = chat.notificationSettings
        settings.useDefaultMuteFor = false
        settings.muteFor = if (muted) Int.MAX_VALUE else 0
        tdRequests(TdApi.SetChatNotificationSettings(chatId, settings))
        Unit
    }

    override suspend fun deleteChatForSelf(chatId: Long): Result<Unit> = requestResult {
        val chat = tdRequests(TdApi.GetChat(chatId))
        val privateChat = chat.type is TdApi.ChatTypePrivate || chat.type is TdApi.ChatTypeSecret
        if (privateChat || chat.canBeDeletedOnlyForSelf) {
            // revoke=false is essential: never delete another member's chat history.
            tdRequests(TdApi.DeleteChatHistory(chatId, true, false))
        } else {
            // Groups and channels cannot be cleared for self only, and TDLib rejects that call, so the user leaves them.
            tdRequests(TdApi.LeaveChat(chatId))
        }
        Unit
    }

    override suspend fun blockPrivateChat(chatId: Long): Result<Unit> = requestResult {
        val chat = tdRequests(TdApi.GetChat(chatId))
        val privateType = chat.type as? TdApi.ChatTypePrivate
            ?: throw IllegalArgumentException("Only private conversations can be blocked")
        if (profiles.isSelf(privateType.userId)) throw IllegalArgumentException("Cannot block your own account")
        tdRequests(TdApi.SetMessageSenderBlockList(TdApi.MessageSenderUser(privateType.userId), TdApi.BlockListMain()))
        Unit
    }

    /**
     * Non-idempotent group creation lives with the TDLib client, not a Compose
     * coroutine or 30-second TdRequests waiter. Even after activity recreation,
     * repeated taps observe the same in-flight request rather than create a duplicate.
     */
    private val groupRequestLock = Any()
    private val groupCreationPrefs = appContext.getSharedPreferences("pending_group_creation", Context.MODE_PRIVATE)
    private val _groupCreation = MutableStateFlow<GroupCreationState>(
        if (groupCreationPrefs.getBoolean("unconfirmed", false)) GroupCreationState.NeedsVerification
        else GroupCreationState.Idle
    )
    override val groupCreation: StateFlow<GroupCreationState> = _groupCreation.asStateFlow()
    private val groupRequestGeneration = java.util.concurrent.atomic.AtomicInteger()

    override fun beginGroupCreation(title: String, memberUserIds: List<Long>): Result<Unit> {
        val name = title.trim()
        val participants = memberUserIds.distinct()
        if (name.length !in 1..128 || participants.isEmpty() ||
            participants.size > 200 || participants.any { it <= 0L }
        ) return Result.failure(IllegalArgumentException("Invalid group title or selected users"))
        synchronized(groupRequestLock) {
            if (!mayBeginGroupCreation(_groupCreation.value)) {
                return Result.failure(IllegalStateException("Group creation has not been confirmed yet"))
            }
            // Persist the uncertain intent before a remote creation can occur.
            // After process death, user must check their chat list before explicitly
            // acknowledging a new request; automatic retry would duplicate groups.
            if (!groupCreationPrefs.edit().putBoolean("unconfirmed", true).commit()) {
                return Result.failure(IllegalStateException("Cannot persist group creation intent"))
            }
            val generation = groupRequestGeneration.incrementAndGet()
            _groupCreation.value = GroupCreationState.Pending(name)
            authScope.launch(Dispatchers.IO) {
                try {
                    val created = suspendCancellableCoroutine<TdApi.CreatedBasicGroupChat> { continuation ->
                        client.send(TdApi.CreateNewBasicGroupChat().apply {
                            userIds = participants.toLongArray()
                            this.title = name
                            messageAutoDeleteTime = 0
                        }, Client.ResultHandler { response ->
                            if (!continuation.isActive) return@ResultHandler
                            when (response) {
                                is TdApi.CreatedBasicGroupChat -> continuation.resume(response)
                                is TdApi.Error -> continuation.resumeWith(
                                    Result.failure(TdRequestError(response.code, response.message))
                                )
                                else -> continuation.resumeWith(
                                    Result.failure(IllegalStateException("Unexpected group creation result"))
                                )
                            }
                        })
                    }
                    if (groupRequestGeneration.get() != generation) return@launch
                    // CreatedBasicGroupChat is authoritative even if a subsequent
                    // GetChat refresh is unavailable; report partial invitation failures.
                    groupCreationPrefs.edit().putBoolean("unconfirmed", false).apply()
                    _groupCreation.value = GroupCreationState.Created(
                        created.chatId,
                        created.failedToAddMembers?.failedToAddMembers?.size ?: 0
                    )
                    runCatching { tdRequests(TdApi.GetChat(created.chatId)) }
                        .onSuccess(::handleNewChat)
                } catch (cancelled: CancellationException) {
                    // The server may have completed; preserve the persisted uncertain
                    // status rather than turning an ambiguous outcome into retryable.
                    if (groupRequestGeneration.get() == generation) {
                        _groupCreation.value = GroupCreationState.NeedsVerification
                    }
                    throw cancelled
                } catch (failure: Exception) {
                    if (groupRequestGeneration.get() != generation) return@launch
                    if (failure is TdRequestError) {
                        // TDLib explicitly rejected the operation before creation.
                        groupCreationPrefs.edit().putBoolean("unconfirmed", false).apply()
                        _groupCreation.value = GroupCreationState.Failed
                    } else {
                        _groupCreation.value = GroupCreationState.NeedsVerification
                    }
                }
            }
            return Result.success(Unit)
        }
    }

    override fun acknowledgeGroupCreation() {
        synchronized(groupRequestLock) {
            if (_groupCreation.value is GroupCreationState.Created ||
                _groupCreation.value == GroupCreationState.Failed) {
                _groupCreation.value = GroupCreationState.Idle
            }
        }
    }

    override fun acknowledgeUncertainGroupCreation() {
        synchronized(groupRequestLock) {
            if (_groupCreation.value == GroupCreationState.NeedsVerification) {
                groupCreationPrefs.edit().putBoolean("unconfirmed", false).apply()
                _groupCreation.value = GroupCreationState.Idle
            }
        }
    }

    override suspend fun createChannel(title: String, description: String): Result<Long> = requestResult {
        val name = title.trim()
        require(name.length in 1..128) { "Channel title must contain 1–128 characters" }
        require(description.length <= 255) { "Channel description must contain at most 255 characters" }
        val chat = tdRequests(TdApi.CreateNewSupergroupChat().apply {
            this.title = name
            isChannel = true
            isForum = false
            this.description = description.trim()
            location = null
            messageAutoDeleteTime = 0
            forImport = false
        })
        handleNewChat(chat)
        chat.id
    }

    override suspend fun activeSessions(): Result<List<com.beykus.beygram.domain.model.DeviceSession>> = requestResult {
        tdRequests(TdApi.GetActiveSessions()).sessions.orEmpty().map { session ->
            com.beykus.beygram.domain.model.DeviceSession(
                id = session.id,
                current = session.isCurrent,
                device = session.deviceModel.orEmpty().ifBlank { session.platform.orEmpty() },
                application = session.applicationName.orEmpty(),
                location = session.location.orEmpty(),
                ip = session.ipAddress.orEmpty(),
                lastActive = session.lastActiveDate.toLong()
            )
        }
    }

    override suspend fun terminateOtherSession(sessionId: Long): Result<Unit> = requestResult {
        require(sessionId > 0 && activeSessions().getOrThrow().any { it.id == sessionId && !it.current }) {
            "Cannot terminate the current session"
        }
        tdRequests(TdApi.TerminateSession(sessionId))
        Unit
    }

    override suspend fun terminateAllOtherSessions(): Result<Unit> = requestResult {
        tdRequests(TdApi.TerminateAllOtherSessions())
        Unit
    }
    override suspend fun archiveReturnsOnNewMessage(): Result<Boolean> = requestResult {
        !TdRequests { client }(TdApi.GetArchiveChatListSettings()).keepUnmutedChatsArchived
    }
    override suspend fun setArchiveReturnsOnNewMessage(enabled: Boolean): Result<Unit> = requestResult {
        // Меняем одно поле: остальные настройки архива аккаунта должны остаться прежними.
        val settings = TdRequests { client }(TdApi.GetArchiveChatListSettings())
        settings.keepUnmutedChatsArchived = !enabled
        TdRequests { client }(TdApi.SetArchiveChatListSettings(settings)); Unit
    }
    override suspend fun markChatRead(chatId: Long): Result<Unit> = conversationResult {
        val lastMessageId = chatCache[chatId]?.lastMessageId?.takeIf { it > 0L } ?: throw UserMessageException(UiText.Res(R.string.err_chat_no_messages))
        TdRequests { client }(TdApi.ViewMessages(chatId, longArrayOf(lastMessageId), TdApi.MessageSourceChatList(), true)); Unit
    }
    private val contactBook = TdContacts({ client }, profiles)
    private val callHistory = TdCallHistory({ client }, profiles)
    override suspend fun loadContacts(): Result<List<ContactPreview>> = requestResult { contactBook.load() }
    override suspend fun addContact(firstName: String, lastName: String, phone: String): Result<Unit> =
        requestResult { contactBook.add(firstName, lastName, phone) }
    override suspend fun openPrivateChat(userId: Long): Result<Long> = requestResult {
        val chat = TdRequests { client }(TdApi.CreatePrivateChat(userId, false))
        handleNewChat(chat)
        chat.id
    }
    override suspend fun loadCallHistory(offset: String): Result<CallHistoryPage> = requestResult { callHistory.page(offset) }
    private val botKeyboards = TdBotKeyboards { client }
    override fun observeBotKeyboard(chatId: Long) = botKeyboards.observe(chatId)
    override suspend fun dismissBotKeyboard(chatId: Long, messageId: Long) = botKeyboards.dismiss(chatId, messageId)
    override suspend fun botCallback(chatId: Long, messageId: Long, data: List<Byte>) = botKeyboards.callback(chatId, messageId, data)
    private val conversationActions = TdConversationActions({ client }, messageHistory,
        map = { it.toChatMessage() },
        onTopicDraft = { chatId, topicId, raw -> messageDrafts.receiveTopic(chatId, topicId.toLong(), raw) })
    private val customEmojis = TdCustomEmojis(client = { client })
    private val conversationEffects = TdConversationEffects({ client }, policy)

    // ---- Память и кэш --------------------------------------------------------

    // Copies listed in the profile's shared media stay protected from the cache policy while that listing is recent.
    private val listedMediaFiles = ListedMediaFiles()
    private val storage = TdStorage({ client }, listOf(File(appContext.filesDir, "uploads"), File(appContext.noBackupFilesDir, "uploads")), File(appContext.filesDir, "uploads")) { messageHistory.mediaFileIds() + listedMediaFiles.current() }
    private val storageSettings = StorageSettingsStore.getInstance(context)
    private val cacheMaintenance = TdCacheMaintenance(authScope) { applyCachePolicy(storageSettings.settings.value.policy()) }
    override suspend fun storageUsage(): Result<StorageUsage> = storageResult { storage.usage() }
    override suspend fun clearStorage(categories: Set<StorageCategory>, onProgress: (done: Int, total: Int) -> Unit): Result<Long> =
        storageInAuthScope { storage.clear(categories, onProgress) }
    override suspend fun applyCachePolicy(policy: CachePolicy): Result<Long> = storageInAuthScope { storage.applyPolicy(policy) }

    /**
     * Очистка и лимит идут в области входа: уход с экрана их не прерывает, а очередь [TdStorage] держится до ответа TDLib.
     * Ждущий получает результат, когда он готов.
     */
    private suspend fun <T> storageInAuthScope(block: suspend () -> T): Result<T> = storageResult { authScope.async { block() }.await() }

    // ---- Канал подтверждения диагностических отчётов --------------------------
    // Pinning id бота задан публичной конфигурацией сборки; 0 — пиннинг выключен.
    private val reportBotChannel = TdReportBotChannel({ client }, BuildConfig.REPORT_BOT_USER_ID)
    override suspend fun openBotChat(expectedBotUserId: Long, username: String) =
        reportBotChannel.openBotChat(expectedBotUserId, username)
    override suspend fun sendReportCommand(botChatId: Long, nonce: String) =
        reportBotChannel.sendReportCommand(botChatId, nonce)
    override suspend fun awaitBotConfirmation(botChatId: Long, timeoutMillis: Long) =
        reportBotChannel.awaitBotConfirmation(botChatId, timeoutMillis)
    override fun cancelBotWait() = reportBotChannel.cancelBotWait()
    override val messageLimits get() = conversationActions.limits
    override suspend fun messageCapabilities(chatId: Long, messageId: Long) = conversationActions.capabilities(chatId, messageId)
    override suspend fun mediaPosition(chatId: Long, messageId: Long, kind: MediaKind) = conversationActions.mediaPosition(chatId, messageId, kind)
    override suspend fun editMessage(chatId: Long, messageId: Long, text: String, entities: List<MessageTextEntity>) = conversationActions.edit(chatId, messageId, text, entities)
    override suspend fun deleteMessage(chatId: Long, messageId: Long, scope: DeleteMessageScope) =
        conversationActions.delete(chatId, messageId, scope).onSuccess { conversationEffects.uploadFinished(chatId, messageId) }
    override suspend fun forwardMessage(fromChatId: Long, messageId: Long, toChatId: Long) = conversationActions.forward(fromChatId, messageId, toChatId)
    override suspend fun availableReactions(chatId: Long, messageId: Long) = conversationActions.availableReactions(chatId, messageId)
    override suspend fun toggleReaction(chatId: Long, messageId: Long, reaction: ReactionOption, addToRecent: Boolean) =
        conversationActions.toggleReaction(chatId, messageId, reaction, addToRecent)
    override fun observeCustomEmoji(customEmojiId: Long) = customEmojis.observe(customEmojiId)
    override suspend fun messageContext(chatId: Long, messageId: Long) = conversationActions.context(chatId, messageId)
    override suspend fun searchMessages(chatId: Long, query: String, fromMessageId: Long) = conversationActions.search(chatId, query, fromMessageId)
    override suspend fun searchForumTopicMessages(chatId: Long, forumTopicId: Long, query: String, fromMessageId: Long) =
        conversationActions.search(chatId, query, fromMessageId, forumTopicId)
    override suspend fun searchGlobalMessages(query: String, offset: String, filter: GlobalSearchFilter) =
        conversationActions.searchGlobal(query, offset, filter).map { page ->
            // One metadata lookup per distinct uncached chat, never per message. Bounded
            // concurrency prevents a page of old hits from flooding the TDLib request lane.
            val missing = page.messages.map { it.chatId }.distinct().filter { chatCache[it] == null }
            val limiter = Semaphore(6)
            val fetched = coroutineScope {
                missing.map { id ->
                    async {
                        limiter.withPermit {
                            val preview = try {
                                tdRequests(TdApi.GetChat(id)).toChatPreview()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null // A deleted or inaccessible chat must not hide the hit.
                            }
                            id to preview
                        }
                    }
                }.awaitAll().mapNotNull { (id, preview) -> preview?.let { id to it } }.toMap()
            }
            page.copy(chats = page.messages.mapNotNull { hit ->
                (chatCache[hit.chatId] ?: fetched[hit.chatId])?.let { hit.chatId to it }
            }.toMap())
        }
    override suspend fun sharedMessages(chatId: Long, kind: SharedMediaKind, fromMessageId: Long, newer: Boolean) = conversationActions.shared(chatId, kind, fromMessageId, newer)
        .onSuccess { page -> listedMediaFiles.remember(page.messages.flatMap { message -> listOfNotNull(message.media?.fileId, message.media?.thumbnailFileId) }) }
    override fun messagesVisible(chatId: Long, messageIds: List<Long>) = conversationEffects.visible(chatId, messageIds)
    override fun startChatActivity(chatId: Long, activity: ChatActivity) = conversationEffects.startActivity(chatId, activity)
    override fun endChatActivity(chatId: Long, activity: ChatActivity) = conversationEffects.endActivity(chatId, activity)
    override fun setAppForeground(foreground: Boolean) {
        conversationEffects.setForeground(foreground)
        _notificationPresentation.update { it.copy(foreground = foreground) }
    }
    override fun refreshInteractionPolicy() = conversationEffects.refreshPolicy()
    override val myProfile: StateFlow<ProfileState> get() = profiles.me
    override val bioLengthLimit: StateFlow<Int> get() = profiles.bioLimit
    override fun observeChatInfo(chatId: Long) = profiles.observe(chatId)
    override fun observeTyping(chatId: Long): StateFlow<List<String>> = profiles.observeTyping(chatId)
    override fun refreshChatInfo(chatId: Long) = profiles.refreshChat(chatId)
    override suspend fun loadChatMembers(chatId: Long, offset: Int, limit: Int): Result<ChatMembersPage> = try {
        Result.success(profiles.loadMembers(chatId, offset, limit))
    } catch (timeout: TimeoutCancellationException) {
        Result.failure(timeout)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
    override fun refreshMyProfile() = profiles.refreshMe()
    override suspend fun updateMyProfile(edit: ProfileEdit) = profiles.edit(edit)
    override suspend fun setMyProfilePhoto(photo: OutgoingMedia) = profiles.setPhoto(photo)
    override suspend fun loadProfilePhotos(profileId: Long, offset: Int, limit: Int) =
        profiles.loadProfilePhotos(profileId, offset, limit)
    override suspend fun deleteMyProfilePhoto(photoId: Long) = profiles.deleteMyPhoto(photoId)
    override suspend fun setPreviousProfilePhoto(photoId: Long) = profiles.setPreviousPhoto(photoId)

    /**
     * Промежуточный кэш чатов: TDLib отдаёт чаты через несколько обновлений,
     * поэтому собираем их здесь. Открытый чат переиздаётся сразу, а [_chats] и [_archivedChats]
     * пересобираются не чаще раза в [CHAT_LIST_PUBLISH_DELAY_MS].
     */
    private val chatCache = ConcurrentHashMap<Long, ChatPreview>()
    /** The user who sent each chat's last message. Their name can load after the preview, and then the preview shows it. */
    private val lastSenderUsers = ConcurrentHashMap<Long, Long>()
    private val muteState = ChatMuteState()
    // Each jump or latest load takes the next number; a jump answered after a newer one has started is dropped.
    private val historyRequests = ConcurrentHashMap<Long, Long>()
    private val chatListScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val chatListPublisher = CoalescedPublisher(chatListScope, CHAT_LIST_PUBLISH_DELAY_MS) {
        // Main-list ordering comes from Telegram, including pins and draft activity.
        val previews = chatCache.values.map { it.withMute() }
        _chats.value = orderedMainChats(previews)
        _archivedChats.value = orderedArchivedChats(previews)
        val activeIds = _chatFolders.value.folders.map { it.id }
        _folderChats.value = activeIds.associateWith { id -> orderedTelegramFolderChats(previews, id) }
    }

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
            if (result is TdApi.Error) {
                ConnectionEventLog.shared.record("SetNetworkType: ошибка ${result.code}")
                StructuredEventLog.shared.record(
                    kind = EventKind.NETWORK,
                    severity = Severity.WARNING,
                    code = "set_network_type_failed",
                    message = "",
                    attrs = mapOf("error_code" to result.code.toString())
                )
                if (BuildConfig.DEBUG) android.util.Log.w(TelegramNetworkMonitor.TAG, "SetNetworkType failed: ${result.code}")
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

    override fun observeChatHistory(chatId: Long): StateFlow<ChatHistory> = messageHistory.observe(chatId)
    override fun observeForumTopicHistory(chatId: Long, forumTopicId: Long): StateFlow<ChatHistory> =
        topicStore(chatId, forumTopicId).observe(chatId)

    override fun loadForumTopicHistory(chatId: Long, forumTopicId: Long) {
        val store = topicStore(chatId, forumTopicId)
        requestForumHistory(chatId, forumTopicId, 0L, store.restartFromLatest(chatId))
    }

    override fun loadOlderForumTopicMessages(chatId: Long, forumTopicId: Long) {
        val store = topicStore(chatId, forumTopicId)
        val oldest = store.observe(chatId).value.messages.firstOrNull()?.id ?: 0L
        store.beginLoad(chatId)?.let { requestForumHistory(chatId, forumTopicId, oldest, it) }
    }

    override suspend fun loadForumTopicMessagesAround(chatId: Long, forumTopicId: Long, messageId: Long): Result<Boolean> = requestResult {
        val store = topicStore(chatId, forumTopicId)
        val result = tdRequests(TdApi.GetForumTopicHistory().apply {
            this.chatId = chatId; this.forumTopicId = forumTopicId.toInt()
            fromMessageId = messageId; offset = -15; limit = 40
        })
        store.replaceWithWindow(chatId, result.messages.orEmpty().filterNotNull().map { it.toChatMessage() }, reachesLatest = false)
        true
    }

    private fun requestForumHistory(chatId: Long, forumTopicId: Long, fromMessageId: Long, epoch: Long) {
        val store = topicStore(chatId, forumTopicId)
        val version = store.version()
        client.send(TdApi.GetForumTopicHistory().apply {
            this.chatId = chatId; this.forumTopicId = forumTopicId.toInt()
            this.fromMessageId = fromMessageId; offset = 0; limit = 50
        }, Client.ResultHandler { result ->
            when (result) {
                is TdApi.Messages -> {
                    // GetForumTopicHistory already scopes messages. Older messages may have no explicit topicId.
                    val page = result.messages.orEmpty().filterNotNull().filter { it.id != fromMessageId }
                    val nextId = page.lastOrNull()?.id ?: fromMessageId
                    val hasMore = page.isNotEmpty() && nextId != fromMessageId
                    if (store.finishLoad(chatId, page.map { it.toChatMessage() }, hasMore, requestVersion = version, epoch = epoch)) {
                        page.forEach(conversationActions::hydrateReply)
                    }
                }
                is TdApi.Error -> store.fail(chatId, UiText.Res(R.string.err_chat_history_load_failed), epoch = epoch)
            }
        })
    }

    override fun observeFile(fileId: Int): StateFlow<MediaFileState> = mediaFiles.observe(fileId)

    override fun downloadFile(fileId: Int) {
        if (!mediaFiles.beginDownload(fileId)) return
        client.send(TdApi.DownloadFile(fileId, 16, 0, 0, false), Client.ResultHandler { result ->
            when (result) {
                is TdApi.File -> mediaFiles.update(result.toMediaFileState())
                is TdApi.Error -> {
                    recordAppEvent(
                        kind = EventKind.TDLIB,
                        code = "file_download_failed",
                        text = "Файл не загрузился, код ${result.code}",
                        severity = Severity.WARNING,
                        attrs = mapOf("error_code" to result.code.toString())
                    )
                    mediaFiles.fail(fileId, UiText.Res(R.string.err_media_download_failed))
                }
            }
        })
    }

    override fun openMessageContent(chatId: Long, messageId: Long) {
        conversationEffects.contentOpened(chatId, messageId)
    }

    override suspend fun sendVideoNoteLossTest(media: OutgoingMedia, remoteCopy: File): Result<com.beykus.beygram.domain.model.VideoNoteLossRemoteResult> =
        requestResult { videoNoteLossTransport.sendAndFetchOwnTest(media, remoteCopy) }

    override suspend fun deleteVideoNoteLossTest(messageId: Long): Result<Unit> =
        videoNoteLossTransport.deleteOwnTest(messageId)

    override suspend fun sendMediaAlbum(chatId: Long, media: List<OutgoingMedia>, caption: String, replyToMessageId: Long?, entities: List<MessageTextEntity>, forumTopicId: Long): Result<Unit> {
        leaveJumpWindow(chatId)
        return conversationActions.sendMediaAlbum(chatId, media, caption, replyToMessageId, entities, forumTopicId)
    }

    override suspend fun sendMedia(chatId: Long, media: OutgoingMedia, caption: String, replyToMessageId: Long?, entities: List<MessageTextEntity>, forumTopicId: Long): Result<Unit> {
        leaveJumpWindow(chatId)
        return conversationActions.sendMedia(chatId, media, caption, replyToMessageId, entities, forumTopicId)
    }
    override suspend fun retryMessages(chatId: Long, messageIds: List<Long>): Result<Unit> =
        conversationActions.retryMessages(chatId, messageIds)

    override fun openChat(chatId: Long) {
        botKeyboards.refresh(chatId)
        _notificationPresentation.update { it.copy(openedChats = it.openedChats + chatId) }
        conversationEffects.opened(chatId)
        messageHistory.opened(chatId)
        client.send(TdApi.OpenChat(chatId), Client.ResultHandler { })
        nextHistoryRequest(chatId)
        requestHistory(chatId, fromMessageId = 0L, remaining = 50, pagesLeft = 3, epoch = messageHistory.restartFromLatest(chatId))
    }

    override fun closeChat(chatId: Long) {
        messageDrafts.closed(chatId)
        _notificationPresentation.update { it.copy(openedChats = it.openedChats - chatId) }
        conversationEffects.closed(chatId)
        messageHistory.closed(chatId)
        client.send(TdApi.CloseChat(chatId), Client.ResultHandler { })
    }

    override fun loadOlderMessages(chatId: Long) {
        val oldest = messageHistory.observe(chatId).value.messages.firstOrNull()?.id ?: 0L
        messageHistory.beginLoad(chatId)?.let { epoch -> requestHistory(chatId, oldest, remaining = 50, pagesLeft = 3, epoch = epoch) }
    }

    override suspend fun loadMessagesAround(chatId: Long, messageId: Long): Result<Boolean> {
        val request = nextHistoryRequest(chatId)
        return conversationActions.window(chatId, messageId).map { messages ->
            // A newer jump or latest load started meanwhile, so this window must not replace its result.
            if (historyRequests[chatId] != request) false else {
                val newestId = chatCache[chatId]?.lastMessageId
                messageHistory.replaceWithWindow(chatId, messages.map { it.toChatMessage() }, reachesLatest = messages.any { it.id == newestId })
                messages.forEach(conversationActions::hydrateReply)
                true
            }
        }
    }

    override fun loadLatestMessages(chatId: Long) {
        nextHistoryRequest(chatId)
        requestHistory(chatId, fromMessageId = 0L, remaining = 50, pagesLeft = 3, epoch = messageHistory.restartFromLatest(chatId))
    }

    private fun nextHistoryRequest(chatId: Long): Long = historyRequests.merge(chatId, 1L) { current, _ -> current + 1 } ?: 1L

    /** Sending from a jump window first returns to the newest messages, so the sent message does not leave a gap. */
    private fun leaveJumpWindow(chatId: Long) {
        if (messageHistory.observe(chatId).value.hasNewerMessages) loadLatestMessages(chatId)
    }

    private fun requestHistory(chatId: Long, fromMessageId: Long, remaining: Int, pagesLeft: Int, epoch: Long) {
        val requestVersion = messageHistory.version()
        client.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, 50, false), Client.ResultHandler { result ->
            when (result) {
                is TdApi.Messages -> {
                    val page = result.messages.orEmpty().filterNot { it.id == fromMessageId }
                    val nextFrom = page.lastOrNull()?.id ?: fromMessageId
                    val more = page.isNotEmpty()
                    val continueLoading = more && nextFrom != fromMessageId && remaining > page.size && pagesLeft > 1
                    // A page from before the window was replaced is stale and must not be merged or continued.
                    if (messageHistory.finishLoad(chatId, page.map { it.toChatMessage() }, more, continueLoading, requestVersion, epoch)) {
                        page.forEach(conversationActions::hydrateReply)
                        if (BuildConfig.DEBUG) android.util.Log.i(TelegramNetworkMonitor.TAG, "History page received: ${page.size} messages")
                        if (continueLoading) requestHistory(chatId, nextFrom, remaining - page.size, pagesLeft - 1, epoch)
                    }
                }
                is TdApi.Error -> messageHistory.fail(chatId, UiText.Res(R.string.err_chat_history_load_failed), epoch = epoch)
            }
        })
    }

    override suspend fun isForumChat(chatId: Long): Result<Boolean> = conversationActions.isForum(chatId)

    override suspend fun canCreateForumTopic(chatId: Long) = conversationActions.canCreateTopic(chatId)
    override suspend fun createForumTopic(chatId: Long, name: String) = conversationActions.createTopic(chatId, name)
    override suspend fun renameForumTopic(chatId: Long, forumTopicId: Long, name: String) = conversationActions.renameTopic(chatId, forumTopicId, name)
    override suspend fun setForumTopicClosed(chatId: Long, forumTopicId: Long, closed: Boolean) = conversationActions.closeTopic(chatId, forumTopicId, closed)
    override suspend fun setForumTopicPinned(chatId: Long, forumTopicId: Long, pinned: Boolean) = conversationActions.pinTopic(chatId, forumTopicId, pinned)
    override suspend fun deleteForumTopic(chatId: Long, forumTopicId: Long) = conversationActions.deleteTopic(chatId, forumTopicId)

    override suspend fun forumTopics(chatId: Long): Result<List<ForumTopic>?> =
        conversationActions.forumTopics(chatId).onSuccess { topics ->
            topics?.forEach { topic ->
                topicStore(chatId, topic.id).apply {
                    readInbox(chatId, topic.lastReadInboxMessageId)
                    readOutbox(chatId, topic.lastReadOutboxMessageId)
                }
            }
        }

    override suspend fun sendMessage(chatId: Long, text: String, replyToMessageId: Long?, entities: List<MessageTextEntity>, forumTopicId: Long, clearDraft: Boolean): Result<Unit> {
        leaveJumpWindow(chatId)
        conversationEffects.endActivity(chatId, ChatActivity.Typing)
        return conversationActions.sendText(chatId, text, replyToMessageId, entities, forumTopicId, clearDraft)
    }

    // =========================================================================
    // Обработка обновлений TDLib
    // =========================================================================

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateMessageIsPinned -> invalidatePins(update.chatId)
            is TdApi.UpdateMessageContent -> invalidatePins(update.chatId)
            is TdApi.UpdateDeleteMessages -> if (update.isPermanent) invalidatePins(update.chatId)
        }
        profiles.handle(update)
        notificationFeed.handle(update)
        when (update) {
            is TdApi.UpdateUser -> refreshLastSenders(update.user.id)
            is TdApi.UpdateAuthorizationState -> handleAuthorizationState(update.authorizationState)
            is TdApi.UpdateNewChat            -> handleNewChat(update.chat)
            is TdApi.UpdateForumTopic -> {
                messageDrafts.receiveTopic(update.chatId, update.forumTopicId.toLong(), update.draftMessage)
                topicStores[update.chatId to update.forumTopicId.toLong()]?.apply {
                readInbox(update.chatId, update.lastReadInboxMessageId)
                readOutbox(update.chatId, update.lastReadOutboxMessageId)
                }
            }
            is TdApi.UpdateChatFolders -> handleChatFolders(update)
            is TdApi.UpdateUnreadChatCount -> {
                val list = update.chatList
                if (list is TdApi.ChatListFolder) {
                    val id = list.chatFolderId
                    folderUnreadCount[id] = update.unreadCount.coerceAtLeast(0)
                    _chatFolders.update { previous ->
                        previous.copy(folders = previous.folders.map {
                            if (it.id == id) it.copy(unreadChats = update.unreadCount.coerceAtLeast(0)) else it
                        })
                    }
                }
            }
            is TdApi.UpdateChatLastMessage    -> handleChatLastMessage(update.chatId, update.lastMessage, update.positions)
            is TdApi.UpdateChatReplyMarkup -> botKeyboards.update(update.chatId, update.replyMarkupMessage)
            is TdApi.UpdateChatPosition -> handleChatPosition(update.chatId, update.position)
            is TdApi.UpdateChatDraftMessage -> {
                messageDrafts.receive(update.chatId, update.draftMessage)
                chatCache[update.chatId]?.let { chatCache[update.chatId] = it.withPositions(update.positions) }
                notificationFeed.chatPositionChanged(update.chatId)
                publishChat(update.chatId)
            }
            is TdApi.UpdateChatReadInbox -> {
                messageHistory.readInbox(update.chatId, update.lastReadInboxMessageId)
                chatCache[update.chatId]?.let { chatCache[update.chatId] = it.copy(unreadCount = update.unreadCount) }
                publishChat(update.chatId)
            }
            is TdApi.UpdateChatReadOutbox -> {
                messageHistory.readOutbox(update.chatId, update.lastReadOutboxMessageId)
                chatCache[update.chatId]?.let { chatCache[update.chatId] = it.copy(lastReadOutboxMessageId = update.lastReadOutboxMessageId) }
                publishChat(update.chatId)
            }
            is TdApi.UpdateMessageContent -> {
                val attachment = mediaMapper.media(update.newContent)
                val service = serviceMessages.text(update.newContent)
                val transform: (ChatMessage) -> ChatMessage = { message ->
                    message.copy(text = service ?: contentText(update.newContent, attachment != null),
                        serviceText = service, entities = messageFormattedText(update.newContent)?.domainEntities().orEmpty(), media = attachment)
                }
                messageHistory.update(update.chatId, update.messageId, transform)
                updateForumMessage(update.chatId, update.messageId, transform)
            }
            is TdApi.UpdateMessageContentOpened -> {
                messageHistory.update(update.chatId, update.messageId) { it.copy(media = it.media?.copy(isViewed = true)) }
                updateForumMessage(update.chatId, update.messageId) { it.copy(media = it.media?.copy(isViewed = true)) }
            }
            is TdApi.UpdateMessageEdited -> {
                messageHistory.update(update.chatId, update.messageId) { it.copy(isEdited = update.editDate > 0, inlineKeyboard = inlineKeyboard(update.replyMarkup)) }
                updateForumMessage(update.chatId, update.messageId) { it.copy(isEdited = update.editDate > 0, inlineKeyboard = inlineKeyboard(update.replyMarkup)) }
            }
            is TdApi.UpdateMessageInteractionInfo -> {
                messageHistory.update(update.chatId, update.messageId) { it.copy(reactions = messageReactions(update.interactionInfo)) }
                updateForumMessage(update.chatId, update.messageId) { it.copy(reactions = messageReactions(update.interactionInfo)) }
            }
            is TdApi.UpdateOption -> conversationActions.option(update.name, update.value)
            is TdApi.UpdateActiveEmojiReactions -> conversationActions.activeEmojiReactions(update.emojis.orEmpty().toList())
            is TdApi.UpdateChatTitle          -> handleChatTitleChange(update.chatId, update.title)
            is TdApi.UpdateSupergroup, is TdApi.UpdateBasicGroup -> chatCache.keys.toList().forEach(::refreshPostingRight)
            is TdApi.UpdateChatPermissions -> refreshPostingRight(update.chatId)
            is TdApi.UpdateChatPhoto -> {
                chatCache[update.chatId]?.let { chatCache[update.chatId] = it.copy(photo = profiles.photo(update.photo)) }
                publishChat(update.chatId)
            }
            is TdApi.UpdateChatNotificationSettings -> {
                muteState.updateChat(update.chatId, update.notificationSettings)
                publishChat(update.chatId)
            }
            is TdApi.UpdateScopeNotificationSettings -> handleScopeNotificationSettings(NotificationScope.ofScope(update.scope), update.notificationSettings)
            is TdApi.UpdateNewMessage         -> {
                reportBotChannel.onNewMessage(update.message)
                handleNewMessage(update.message)
                conversationEffects.uploadStarted(update.message)
                if (update.message.isOutgoing && update.message.content is TdApi.MessageVideoNote &&
                    update.message.sendingState is TdApi.MessageSendingStatePending) {
                    VideoNoteSendTracking.tracker.onPending(update.message.chatId, update.message.id)
                }
            }
            is TdApi.UpdateFile -> mediaFiles.update(update.file.toMediaFileState())
            is TdApi.UpdateMessageSendSucceeded -> {
                videoNoteLossTransport.onSendSucceeded(update)
                VideoNoteSendTracking.tracker.onSucceeded(update.message.chatId, update.oldMessageId)
                reportBotChannel.onSendSucceeded(update)
                cleanupSentUploads(update.message)
                messageHistory.put(update.message.toChatMessage(), update.oldMessageId)
                putForumMessage(update.message, update.oldMessageId)
                conversationActions.hydrateReply(update.message)
                conversationEffects.uploadFinished(update.message.chatId, update.oldMessageId)
            }
            is TdApi.UpdateMessageSendFailed -> {
                videoNoteLossTransport.onSendFailed(update)
                reportBotChannel.onSendFailed(update)
                val sendError = (update.message.sendingState as? TdApi.MessageSendingStateFailed)?.error
                VideoNoteSendTracking.tracker.onFailed(update.message.chatId, update.oldMessageId, sendError?.code ?: 0)
                android.util.Log.e("BeyGramNet", "Message send failed chat=${update.message.chatId} id=${update.message.id} content=${update.message.content.javaClass.simpleName} code=${sendError?.code} error=${sendError?.message}")
                recordAppEvent(
                    kind = EventKind.TDLIB,
                    code = "message_send_failed",
                    text = "Сообщение не отправлено: ${update.message.content.javaClass.simpleName}, код ${sendError?.code ?: 0}",
                    severity = Severity.ERROR,
                    attrs = mapOf("content" to update.message.content.javaClass.simpleName, "error_code" to (sendError?.code ?: 0).toString())
                )
                messageHistory.put(update.message.toChatMessage(), update.oldMessageId)
                putForumMessage(update.message, update.oldMessageId)
                conversationEffects.uploadFinished(update.message.chatId, update.oldMessageId)
            }
            is TdApi.UpdateDeleteMessages -> {
                update.messageIds.forEach { conversationEffects.uploadFinished(update.chatId, it) }
                if (update.isPermanent) {
                    messageHistory.delete(update.chatId, update.messageIds.toList())
                    topicStores.filterKeys { it.first == update.chatId }.values.forEach { it.delete(update.chatId, update.messageIds.toList()) }
                }
            }
            is TdApi.UpdateConnectionState    -> {
                ConnectionEventLog.shared.record("Соединение с Telegram: ${update.state.logLabel()}")
                StructuredEventLog.shared.record(
                    kind = EventKind.TDLIB,
                    severity = Severity.INFO,
                    code = "tdlib_connection",
                    message = update.state.logLabel(),
                    attrs = mapOf("state" to update.state.wireState())
                )
                if (BuildConfig.DEBUG) android.util.Log.i(TelegramNetworkMonitor.TAG, "TDLib connection: ${update.state.javaClass.simpleName}")
                if (update.state is TdApi.ConnectionStateReady) messageDrafts.connectionReady()
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

    /**
     * Files stay on disk until TDLib reports the message sent. Then the folder of the copy gets a sent mark, so the
     * cache policy may remove it later; unsent files stay unmarked. Voice notes and video notes are removed at once:
     * TDLib keeps the local path of a sent photo, video, audio or document and reads it for the feed and the viewer,
     * so those copies wait for the cache policy.
     */
    private fun cleanupSentUploads(message: TdApi.Message) {
        val content = message.content
        content.localFiles().filterNotNull().filter { !it.local.path.isNullOrBlank() }.forEach { tdFile ->
            val localPath = tdFile.local.path
            runCatching {
                val uploads = File(appContext.filesDir, "uploads").canonicalFile
                val file = File(localPath).canonicalFile
                // TDLib's own cache is outside uploads and is never touched here.
                if (!file.path.startsWith(uploads.path + File.separator)) return@runCatching
                if (content !is TdApi.MessageVoiceNote && content !is TdApi.MessageVideoNote) {
                    val folder = generateSequence(file) { it.parentFile }.first { it.parentFile == uploads }
                    // The mark keeps the file id, so the cache policy can tell TDLib that this copy is gone.
                    markSentUpload(folder, tdFile.id)
                    return@runCatching
                }
                if (content is TdApi.MessageVideoNote) {
                    // A session folder can hold segments that still wait to be sent, so only this segment's files and an emptied folder go.
                    file.delete()
                    file.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
                } else {
                    // A voice note has a folder of its own. Removing it also removes files TDLib does not report.
                    generateSequence(file) { it.parentFile }.first { it.parentFile == uploads }.deleteRecursively()
                }
                android.util.Log.i("BeyGramNet", "Sent upload cleaned kind=${content.javaClass.simpleName} path=$localPath")
            }.onFailure { android.util.Log.w("BeyGramNet", "Could not clean sent upload: ${it.message}") }
        }
    }

    /** Local files of a message content, thumbnails included. */
    private fun TdApi.MessageContent.localFiles(): List<TdApi.File?> = when (this) {
        is TdApi.MessagePhoto -> photo.sizes.map { it.photo }
        is TdApi.MessageVideo -> listOf(video.video, video.thumbnail?.file)
        is TdApi.MessageAnimation -> listOf(animation.animation, animation.thumbnail?.file)
        is TdApi.MessageAudio -> listOf(audio.audio, audio.albumCoverThumbnail?.file)
        is TdApi.MessageDocument -> listOf(document.document, document.thumbnail?.file)
        is TdApi.MessageVideoNote -> listOf(videoNote.video, videoNote.thumbnail?.file)
        is TdApi.MessageVoiceNote -> listOf(voiceNote.voice)
        else -> emptyList()
    }

    // ---- Авторизация ---------------------------------------------------------

    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        ConnectionEventLog.shared.record("Вход в аккаунт: ${state.logLabel()}")
        StructuredEventLog.shared.record(
            kind = EventKind.TDLIB,
            severity = Severity.INFO,
            code = "tdlib_auth",
            message = state.logLabel(),
            attrs = mapOf("state" to state.wireState())
        )
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
                loadChatsWithNotificationScopes()
                // Folder metadata can arrive while TDLib is still authorizing. Re-attempt
                // first-page loads that failed before Ready; the per-folder mutex deduplicates.
                _chatFolders.value.folders.forEach { folder ->
                    val status = _folderLoadStates.value[folder.id]
                    if (status?.hasMore != false && status?.loading != true) {
                        chatListScope.launch { loadMoreFolderChats(folder.id) }
                    }
                }
                syncArchiveReturnSetting()
                profiles.refreshMe()
                conversationActions.initialize()
                conversationEffects.authorized()
                notificationFeed.initialize()
                cacheMaintenance.authorized()
                messageDrafts.connectionReady()
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                groupRequestGeneration.incrementAndGet()
                groupCreationPrefs.edit().remove("unconfirmed").apply()
                _groupCreation.value = GroupCreationState.Idle
                clearFolderState()
                messageDrafts.signedOut()
                _authState.value = AuthState.WaitingForPhone
                conversationEffects.signedOut()
                cacheMaintenance.signedOut()
            }
            is TdApi.AuthorizationStateClosed -> {
                clearFolderState()
                _authState.value = AuthState.WaitingForPhone
                conversationEffects.signedOut()
                cacheMaintenance.signedOut()
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
     * Запрашиваем у TDLib основной список и архив. TDLib присылает чаты порциями
     * через [TdApi.UpdateNewChat] и [TdApi.UpdateChatPosition].
     */
    private fun loadChats() {
        loadChatList(TdApi.ChatListMain())
        loadChatList(TdApi.ChatListArchive())
    }

    /**
     * Чаты грузим сразу, а общие настройки звука читаем параллельно. Пока настройка области не пришла, новое сообщение
     * не возвращает её архивный чат в основной список: чат может быть заглушён (см. [ChatMuteState.isKnown]).
     */
    private fun loadChatsWithNotificationScopes() {
        loadChats()
        chatListScope.launch { loadNotificationScopes() }
        // Чаты приходят раньше GetMe, поэтому «Избранное» помечаем снова, когда аккаунт станет известен.
        chatListScope.launch { profiles.me.map { it.info?.id }.filterNotNull().distinctUntilChanged().collect { markSavedMessages() } }
    }

    /** У личного чата id совпадает с id пользователя, так что чат с самим собой узнаётся без типа чата. */
    private fun markSavedMessages() {
        chatCache.keys.toList().forEach { chatId ->
            val existing = chatCache[chatId] ?: return@forEach
            if (existing.isSavedMessages || existing.isGroup || !profiles.isSelf(chatId)) return@forEach
            chatCache[chatId] = existing.copy(isSavedMessages = true, title = appContext.getString(R.string.saved_messages_title))
            publishChat(chatId)
        }
    }

    /**
     * Ошибка чтения одной области оставляет её без общего значения: чаты на общей настройке этой области не возвращаются
     * из архива, пока TDLib не пришлёт её значение.
     */
    private suspend fun loadNotificationScopes() = coroutineScope {
        NotificationScope.entries.map { scope ->
            async {
                runCatching { tdRequests(TdApi.GetScopeNotificationSettings(scope.toTd())) }
                    .onSuccess { handleScopeNotificationSettings(scope, it) }
            }
        }.awaitAll()
    }

    /** Общая настройка меняет только звук: списки пересобираются с ним при публикации, а наблюдатели одного чата звук не показывают. */
    private fun handleScopeNotificationSettings(scope: NotificationScope, settings: TdApi.ScopeNotificationSettings) {
        if (muteState.updateScope(scope, settings).isNotEmpty()) chatListPublisher.request()
    }

    /** TDLib gives the same order as other clients. IDs aren't local categories. */
    private fun handleChatFolders(update: TdApi.UpdateChatFolders) {
        val oldIds = _chatFolders.value.folders.map { it.id }.toSet()
        val folders = update.chatFolders.orEmpty().map {
            it.toFolderOverview(folderUnreadCount[it.id])
        }.distinctBy { it.id }
        val active = folders.map { it.id }.toSet()
        val removed = oldIds - active
        removed.forEach {
            folderUnreadCount.remove(it)
            folderPageLocks.remove(it)
        }
        // Remove membership left by a deleted folder; do not keep showing stale folder-only chats.
        if (removed.isNotEmpty()) {
            chatCache.forEach { (chatId, preview) ->
                if (preview.folderPositions.keys.any { it in removed }) {
                    chatCache[chatId] = preview.copy(
                        folderPositions = preview.folderPositions.filterKeys { it !in removed }
                    )
                }
            }
        }
        _chatFolders.value = TelegramChatFolders(
            initialized = true,
            folders = folders,
            mainChatListPosition = update.mainChatListPosition
        )
        _folderLoadStates.update { states ->
            active.associateWith { id -> states[id] ?: TelegramFolderLoadState() }
        }
        chatListPublisher.request()
        // Initial folder pages must be fetched from ChatListFolder, NOT filtered Main chats.
        (active - oldIds).forEach { id ->
            chatListScope.launch { loadMoreFolderChats(id) }
        }
    }

    private fun clearFolderState() {
        _chatFolders.value = TelegramChatFolders()
        _folderChats.value = emptyMap()
        _folderLoadStates.value = emptyMap()
        folderUnreadCount.clear()
        folderPageLocks.clear()
        // No previous account's folder membership may remain visible after logout.
        chatCache.clear()
        _chats.value = emptyList()
        _archivedChats.value = emptyList()
        chatStates.values.forEach { it.value = null }
    }

    /** Переключатель возврата из архива показывает настройку аккаунта, поэтому читаем её при входе. */
    private fun syncArchiveReturnSetting() {
        chatListScope.launch {
            archiveReturnsOnNewMessage().onSuccess { returns ->
                val store = ArchiveSettingsStore.get(appContext)
                store.update(store.state.value.copy(returnOnNewMessage = returns))
            }
        }
    }

    /**
     * Ok означает, что порция загружена и можно запросить следующую. Любая ошибка останавливает загрузку;
     * 404 означает, что список загружен целиком.
     */
    private fun loadChatList(list: TdApi.ChatList) {
        client.send(
            TdApi.LoadChats(list, 100),
            Client.ResultHandler { result -> if (result is TdApi.Ok) loadChatList(list) }
        )
    }

    private fun handleNewChat(chat: TdApi.Chat) {
        messageDrafts.receive(chat.id, chat.draftMessage)
        profiles.putChat(chat)
        rememberLastSender(chat.id, chat.lastMessage)
        messageHistory.readInbox(chat.id, chat.lastReadInboxMessageId)
        messageHistory.readOutbox(chat.id, chat.lastReadOutboxMessageId)
        muteState.putChat(chat.id, NotificationScope.ofChat(chat.type), chat.notificationSettings)
        val preview = chat.toChatPreview()
        // The notification groups read the archived flag from chatCache, so the preview is stored before they are updated.
        preview?.let { chatCache[chat.id] = it }
        notificationFeed.chat(chat)
        if (preview != null) publishChat(chat.id)
    }

    private fun handleChatLastMessage(chatId: Long, lastMessage: TdApi.Message?, positions: Array<TdApi.ChatPosition>) {
        rememberLastSender(chatId, lastMessage)
        val existing = chatCache[chatId] ?: return
        chatCache[chatId] = existing.withLastMessage(lastMessage).withPositions(positions)
        notificationFeed.chatPositionChanged(chatId)
        publishChat(chatId)
    }

    /** Republishes the chat only when its posting right changed, so unrelated group updates do not wake its observers. */
    private fun refreshPostingRight(chatId: Long) {
        val existing = chatCache[chatId] ?: return
        val chat = profiles.chat(chatId) ?: return
        val canPost = profiles.canPost(chat)
        if (canPost == existing.canPost) return
        chatCache[chatId] = existing.copy(canPost = canPost)
        publishChat(chatId)
    }

    private fun rememberLastSender(chatId: Long, message: TdApi.Message?) {
        val userId = (message?.senderId as? TdApi.MessageSenderUser)?.userId
        if (userId == null) lastSenderUsers.remove(chatId) else lastSenderUsers[chatId] = userId
    }

    /** A user loaded after a chat's preview was built: the preview shows the sender's name from now on. */
    private fun refreshLastSenders(userId: Long) {
        lastSenderUsers.forEach { (chatId, senderId) ->
            if (senderId != userId) return@forEach
            val existing = chatCache[chatId] ?: return@forEach
            val name = profiles.senderName(TdApi.MessageSenderUser(userId))
            if (existing.lastMessageSender == name) return@forEach
            chatCache[chatId] = existing.copy(lastMessageSender = name)
            publishChat(chatId)
        }
    }

    /** Поля строки чата, которые зависят от последнего сообщения: текст, время, отправитель и статус своего сообщения. */
    private fun ChatPreview.withLastMessage(message: TdApi.Message?): ChatPreview = copy(
        lastMessage = message?.toTextPreview() ?: UiText.Raw(""),
        lastMessageTime = message?.toTimeString().orEmpty(),
        lastMessageId = message?.id ?: 0L,
        lastMessageOutgoing = message?.isOutgoing == true,
        lastMessageSender = message?.let { profiles.senderName(it.senderId) }.orEmpty(),
        lastMessageSendState = message?.sendingState.toSendState()
    )

    /** Positions include ALL TDLib folder lists, not just Main and Archive. */
    private fun ChatPreview.withPositions(positions: Array<TdApi.ChatPosition>): ChatPreview {
        val main = positions.firstOrNull { it.list is TdApi.ChatListMain }
        val archive = positions.firstOrNull { it.list is TdApi.ChatListArchive }
        val folders = positions.mapNotNull { position ->
            val list = position.list as? TdApi.ChatListFolder ?: return@mapNotNull null
            if (position.order <= 0L) return@mapNotNull null
            list.chatFolderId to FolderChatPosition(position.order, position.isPinned)
        }.toMap()
        return copy(
            order = main?.order ?: 0L,
            isPinned = main?.isPinned == true,
            archiveOrder = archive?.order ?: 0L,
            folderPositions = folders
        )
    }

    private fun handleChatPosition(chatId: Long, position: TdApi.ChatPosition) {
        val existing = chatCache[chatId] ?: return
        chatCache[chatId] = when (val list = position.list) {
            is TdApi.ChatListMain -> existing.copy(order = position.order, isPinned = position.isPinned)
            is TdApi.ChatListArchive -> existing.copy(archiveOrder = position.order)
            is TdApi.ChatListFolder -> {
                val changed = existing.folderPositions.toMutableMap()
                if (position.order <= 0L) changed.remove(list.chatFolderId)
                else changed[list.chatFolderId] = FolderChatPosition(position.order, position.isPinned)
                existing.copy(folderPositions = changed)
            }
            else -> return
        }
        notificationFeed.chatPositionChanged(chatId)
        publishChat(chatId)
    }

    private fun handleChatTitleChange(chatId: Long, title: String) {
        val existing = chatCache[chatId] ?: return
        // Название «Избранного» задаёт приложение на языке интерфейса, поэтому присланное TDLib не перезаписывает его.
        chatCache[chatId] = existing.copy(title = if (existing.isSavedMessages) existing.title else title)
        publishChat(chatId)
    }

    private fun handleNewMessage(message: TdApi.Message) {
        messageHistory.put(message.toChatMessage())
        putForumMessage(message)
        conversationActions.hydrateReply(message)
        val chat = chatCache[message.chatId]
        // Muted archived chats stay in the archive, as they do in Telegram. Until the chat's notification settings are
        // known, it stays in the archive too: it may be muted.
        if (!message.isOutgoing && chat != null && chat.archiveOrder > 0L && muteState.isKnown(message.chatId) &&
            !muteState.isMuted(message.chatId) && ArchiveSettingsStore.get(appContext).state.value.returnOnNewMessage
        ) {
            returningFromArchive += message.chatId
            chatListScope.launch {
                setChatArchived(message.chatId, false).onFailure { ConnectionEventLog.shared.record("Возврат чата из архива: ошибка") }
                returningFromArchive -= message.chatId
            }
        }
    }

    private fun TdApi.Message.toChatMessage(): ChatMessage {
        val attachment = mediaMapper.media(content)
        val event = serviceMessages.event(this) { profiles.senderName(TdApi.MessageSenderUser(it)) }
        val service = event?.text
        if (event != null) hydrateServiceMember(this, event.userId)
        return ChatMessage(
        id = id,
        chatId = chatId,
        text = service ?: contentText(content, attachment != null),
        time = toTimeString(),
        isOutgoing = isOutgoing,
        dateSeconds = date.toLong(),
        sendState = sendingState.toSendState(),
        media = attachment,
        mediaAlbumId = mediaAlbumId,
        senderName = profiles.senderName(senderId),
        isEdited = editDate > 0,
        entities = messageFormattedText(content)?.domainEntities().orEmpty(),
        inlineKeyboard = inlineKeyboard(replyMarkup),
        reactions = messageReactions(interactionInfo),
        serviceText = service,
        serviceUserId = event?.userId ?: 0L,
        reply = (replyTo as? TdApi.MessageReplyToMessage)?.let { reply ->
            MessageReply(reply.chatId.takeIf { it != 0L } ?: chatId, reply.messageId,
                reply.quote?.text?.text?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: reply.content?.let { replyText(it) } ?: UiText.Res(R.string.message_title))
        }
    )
    }

    /** Updates observers of this chat right away and queues the shared lists; other chats are not touched. */
    private fun publishChat(chatId: Long) {
        chatStates[chatId]?.value = chatCache[chatId]?.withMute()
        chatListPublisher.request()
    }

    /** Заглушённость берём из состояния звука в момент публикации, поэтому общие настройки не пишутся в каждый чат. */
    private fun ChatPreview.withMute(): ChatPreview = copy(isMuted = muteState.isMuted(id))

    // =========================================================================
    // Конвертеры TdApi → domain models
    // =========================================================================

    private fun TdApi.Chat.toChatPreview(): ChatPreview? {
        val saved = type.let { it is TdApi.ChatTypePrivate && profiles.isSelf(it.userId) }
        val chatTitle = if (saved) appContext.getString(R.string.saved_messages_title) else title.ifBlank { return null }
        val mainPosition = positions.orEmpty().firstOrNull { it.list is TdApi.ChatListMain }
        val archivePosition = positions.orEmpty().firstOrNull { it.list is TdApi.ChatListArchive }
        val folderPositions = positions.orEmpty().mapNotNull { position ->
            val list = position.list as? TdApi.ChatListFolder ?: return@mapNotNull null
            if (position.order <= 0L) return@mapNotNull null
            list.chatFolderId to FolderChatPosition(position.order, position.isPinned)
        }.toMap()
        return ChatPreview(
            id = id,
            title = chatTitle,
            initials = chatTitle.initials(),
            lastMessage = lastMessage?.toTextPreview() ?: UiText.Raw(""),
            lastMessageTime = lastMessage?.toTimeString() ?: "",
            unreadCount = unreadCount,
            avatarColorArgb = id.stableColor(),
            isGroup = type is TdApi.ChatTypeBasicGroup || type is TdApi.ChatTypeSupergroup,
            isChannel = (type as? TdApi.ChatTypeSupergroup)?.isChannel == true,
            isSavedMessages = saved,
            isPinned = mainPosition?.isPinned == true,
            order = mainPosition?.order ?: 0L,
            archiveOrder = archivePosition?.order ?: 0L,
            lastMessageId = lastMessage?.id ?: 0L,
            lastMessageOutgoing = lastMessage?.isOutgoing == true,
            lastMessageSender = lastMessage?.let { profiles.senderName(it.senderId) }.orEmpty(),
            lastMessageSendState = lastMessage?.sendingState.toSendState(),
            lastReadOutboxMessageId = lastReadOutboxMessageId,
            canPost = profiles.canPost(this),
            photo = profiles.photo(photo),
            folderPositions = folderPositions
        )
    }

    private fun contentText(content: TdApi.MessageContent, hasMedia: Boolean): String =
        if (hasMedia) mediaMapper.caption(content) else (content as? TdApi.MessageText)?.text?.text.orEmpty()

    /** Текст ответа: цитата, подпись или текст; у медиа без подписи и у пустого текста — «Медиа». */
    private fun replyText(content: TdApi.MessageContent): UiText {
        val hasMedia = mediaMapper.media(content) != null
        val text = contentText(content, hasMedia)
        return when {
            text.isNotBlank() -> UiText.Raw(text)
            hasMedia || content is TdApi.MessageText -> UiText.Res(R.string.chat_reply_media)
            else -> previewText(content)
        }
    }

    private fun TdApi.Message.toTextPreview(): UiText =
        serviceMessages.event(this) { profiles.senderName(TdApi.MessageSenderUser(it)) }
            ?.let { UiText.Raw(it.text) } ?: previewText(content)

    private fun previewText(c: TdApi.MessageContent): UiText =
        serviceMessages.text(c)?.let(UiText::Raw) ?: when (c) {
            is TdApi.MessageText      -> UiText.Raw(c.text.text)
            is TdApi.MessagePhoto     -> UiText.Res(R.string.chat_preview_photo)
            is TdApi.MessageVideo     -> UiText.Res(R.string.chat_preview_video)
            is TdApi.MessageAudio     -> UiText.Res(R.string.chat_preview_audio)
            is TdApi.MessageDocument  -> UiText.Res(R.string.chat_preview_document)
            is TdApi.MessageSticker   -> UiText.Res(R.string.chat_preview_sticker, listOf(c.sticker.emoji))
            is TdApi.MessageVoiceNote -> UiText.Res(R.string.chat_preview_voice)
            else                      -> UiText.Res(R.string.message_title)
        }

    private fun TdApi.MessageSendingState?.toSendState(): MessageSendState = when (this) {
        is TdApi.MessageSendingStatePending -> MessageSendState.Sending
        is TdApi.MessageSendingStateFailed -> MessageSendState.Failed
        else -> MessageSendState.Sent
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
                _authState.value = AuthState.Error(authErrorText(result.message))
            }
        }

    /** Известные коды TDLib переводим в текст; остальные сообщения TDLib на экран не выводим. */
    private fun authErrorText(message: String?): UiText {
        val text = message.orEmpty()
        return when {
            text.contains("PHONE_NUMBER_INVALID") -> UiText.Res(R.string.err_phone_invalid)
            text.contains("PHONE_CODE_INVALID") -> UiText.Res(R.string.err_auth_code_invalid)
            text.contains("PHONE_CODE_EXPIRED") -> UiText.Res(R.string.err_auth_code_expired)
            text.contains("PASSWORD_HASH_INVALID") -> UiText.Res(R.string.err_auth_password_invalid)
            text.contains("FLOOD_WAIT") || text.contains("Too Many Requests") -> UiText.Res(R.string.err_tdlib_flood_wait)
            else -> UiText.Res(R.string.err_auth_failed)
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

    /** Разрешённое значение состояния соединения для структурированного отчёта (без свободных строк). */
    private fun TdApi.ConnectionState.wireState(): String = when (this) {
        is TdApi.ConnectionStateReady -> "ready"
        is TdApi.ConnectionStateConnecting -> "connecting"
        is TdApi.ConnectionStateConnectingToProxy -> "connecting_to_proxy"
        is TdApi.ConnectionStateUpdating -> "updating"
        is TdApi.ConnectionStateWaitingForNetwork -> "waiting_for_network"
        else -> "other"
    }

    /** Разрешённое значение этапа авторизации для структурированного отчёта. */
    private fun TdApi.AuthorizationState.wireState(): String = when (this) {
        is TdApi.AuthorizationStateWaitTdlibParameters -> "wait_parameters"
        is TdApi.AuthorizationStateWaitPhoneNumber -> "wait_phone"
        is TdApi.AuthorizationStateWaitCode -> "wait_code"
        is TdApi.AuthorizationStateWaitPassword -> "wait_password"
        is TdApi.AuthorizationStateReady -> "ready"
        is TdApi.AuthorizationStateLoggingOut -> "logging_out"
        is TdApi.AuthorizationStateClosed -> "closed"
        else -> "other"
    }

    companion object {
        private const val MIHOMO_PROXY_COMMENT = "BeyGram Mihomo"
        private const val USER_PROXY_COMMENT = "BeyGram"
        private const val KEY_PREVIOUS_PROXY_ID = "previous_proxy_id"
        private const val PROXY_PING_TIMEOUT_MS = 5_000
        private const val AUTH_REQUEST_TIMEOUT_MS = 35_000L
        private const val CHAT_LIST_PUBLISH_DELAY_MS = 100L
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

/** Стабильный цвет из chatId — один из шести цветов палитры чатов дизайн-системы. */
private val CHAT_COLORS = longArrayOf(
    0xFFC2593F, 0xFF5B4BC4, 0xFF2F8A7B,
    0xFFA9731D, 0xFF3A78C2, 0xFFB0476E
)

internal fun Long.stableColor(): Long {
    val idx = (this % CHAT_COLORS.size).toInt()
    return CHAT_COLORS[if (idx < 0) idx + CHAT_COLORS.size else idx]
}

private fun TdApi.ConnectionState.logLabel(): String = when (this) {
    is TdApi.ConnectionStateWaitingForNetwork -> "ожидание сети"
    is TdApi.ConnectionStateConnectingToProxy -> "подключение через прокси"
    is TdApi.ConnectionStateConnecting -> "подключение"
    is TdApi.ConnectionStateUpdating -> "обновление"
    is TdApi.ConnectionStateReady -> "подключено"
    else -> "неизвестно"
}

private fun TdApi.AuthorizationState.logLabel(): String = when (this) {
    is TdApi.AuthorizationStateWaitTdlibParameters -> "подготовка"
    is TdApi.AuthorizationStateWaitPhoneNumber -> "ожидание номера"
    is TdApi.AuthorizationStateWaitCode -> "ожидание кода"
    is TdApi.AuthorizationStateWaitPassword -> "ожидание пароля"
    is TdApi.AuthorizationStateReady -> "вход выполнен"
    is TdApi.AuthorizationStateLoggingOut -> "выход"
    is TdApi.AuthorizationStateClosed -> "закрыто"
    else -> "другой этап"
}
