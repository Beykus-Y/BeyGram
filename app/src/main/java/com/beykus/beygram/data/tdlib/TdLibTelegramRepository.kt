package com.beykus.beygram.data.tdlib

import android.content.Context
import android.os.Build
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.analytics.AnalyticsSync
import com.beykus.beygram.data.export.ChatExportMessage
import com.beykus.beygram.diagnostics.MultiAccountDiagnostics
import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.ChatActivity
import com.beykus.beygram.domain.model.ChatExportProgress
import com.beykus.beygram.domain.model.ChatExportRequest
import com.beykus.beygram.domain.model.ChatExportSummary
import com.beykus.beygram.domain.model.ChatExportTarget
import com.beykus.beygram.domain.model.ChatMembersPage
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatSearchFilter
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.GlobalSearchFilter
import com.beykus.beygram.domain.model.GlobalSearchPage
import com.beykus.beygram.domain.model.PublicChatOpenResult
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MediaPosition
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageTranslation
import com.beykus.beygram.domain.model.MessageDraft
import com.beykus.beygram.domain.model.MessageThread
import com.beykus.beygram.domain.model.MessageThreadPage
import com.beykus.beygram.domain.model.BotKeyboard
import com.beykus.beygram.domain.model.BotCallbackAnswer
import com.beykus.beygram.domain.model.BotCommandMenu
import com.beykus.beygram.domain.model.BotWebAppLaunch
import com.beykus.beygram.domain.model.BotWebAppSource
import com.beykus.beygram.domain.model.BotWebAppTheme
import com.beykus.beygram.domain.model.InlineBotResults
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.GroupAdministratorRights
import com.beykus.beygram.domain.model.GroupInviteLink
import com.beykus.beygram.domain.model.GroupInviteLinkDraft
import com.beykus.beygram.domain.model.GroupJoinRequestsPage
import com.beykus.beygram.domain.model.GroupManagementInfo
import com.beykus.beygram.domain.model.GroupMemberAddition
import com.beykus.beygram.domain.model.GroupMemberFilter
import com.beykus.beygram.domain.model.GroupMemberPage
import com.beykus.beygram.domain.model.GroupSettingsEdit
import com.beykus.beygram.domain.model.ForwardedMessages
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSearchPage
import com.beykus.beygram.domain.model.StickerSetContent
import com.beykus.beygram.domain.model.StickerSetSummary
import com.beykus.beygram.data.media.VideoNoteSendTracking
import com.beykus.beygram.data.media.AutoDownloadDecision
import com.beykus.beygram.data.media.AutoDownloadNetwork
import com.beykus.beygram.data.media.AutoDownloadNetworkMonitor
import com.beykus.beygram.data.media.AutoDownloadSettings
import com.beykus.beygram.data.media.AutoDownloadTdlibMapping
import com.beykus.beygram.data.media.TrafficUsageStore
import com.beykus.beygram.data.media.currentAutoDownloadNetwork
import com.beykus.beygram.data.media.decideAutoDownload
import com.beykus.beygram.data.settings.AutoDownloadSettingsStore
import com.beykus.beygram.data.archive.ArchiveSettingsStore
import com.beykus.beygram.data.storage.StorageSettingsStore
import com.beykus.beygram.domain.model.CachePolicy
import com.beykus.beygram.domain.model.StorageCategory
import com.beykus.beygram.domain.model.StorageUsage
import com.beykus.beygram.domain.model.ProfileEdit
import com.beykus.beygram.domain.model.ProfileState
import com.beykus.beygram.domain.model.SecretChatDetails
import com.beykus.beygram.domain.model.QrLoginLinkTracker
import com.beykus.beygram.domain.model.QrLoginLinkUpdate
import com.beykus.beygram.domain.model.QR_LOGIN_REFRESH_TIMEOUT_MS
import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.ChannelAdminEvent
import com.beykus.beygram.domain.model.ChannelAdminEventPage
import com.beykus.beygram.domain.model.ChannelChartId
import com.beykus.beygram.domain.model.ChannelChartKind
import com.beykus.beygram.domain.model.ChannelEventFilter
import com.beykus.beygram.domain.model.ChannelStatisticsChart
import com.beykus.beygram.domain.model.ChannelStatisticsPerson
import com.beykus.beygram.domain.model.ChannelStatisticsSummary
import com.beykus.beygram.domain.model.ChannelTopPost
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ChatStories
import com.beykus.beygram.domain.model.StoryDetail
import com.beykus.beygram.domain.model.StoryPostEvent
import com.beykus.beygram.domain.model.StoryPostPermission
import com.beykus.beygram.domain.model.StoryPostTicket
import com.beykus.beygram.domain.model.StoryPrivacy
import com.beykus.beygram.domain.model.ChatInvitePreview
import com.beykus.beygram.domain.model.InviteJoinOutcome
import com.beykus.beygram.domain.model.GiftSendOutcome
import com.beykus.beygram.domain.model.GiftsInfo
import com.beykus.beygram.domain.model.MessageLinkTarget
import com.beykus.beygram.domain.model.PremiumCheckoutOutcome
import com.beykus.beygram.domain.model.PremiumGiftCodeApplyOutcome
import com.beykus.beygram.domain.model.PremiumGiftCodeInfo
import com.beykus.beygram.domain.model.PremiumInfo
import com.beykus.beygram.domain.model.PremiumPlan
import com.beykus.beygram.domain.model.StarTopUpOption
import com.beykus.beygram.domain.model.StarsTopUpOutcome
import com.beykus.beygram.domain.model.StarTransactionsPage
import com.beykus.beygram.domain.model.StarsInfo
import com.beykus.beygram.domain.model.FolderChatPosition
import com.beykus.beygram.domain.model.TelegramChatFolders
import com.beykus.beygram.domain.model.TelegramFolderDraft
import com.beykus.beygram.domain.model.TelegramFolderLoadState
import com.beykus.beygram.domain.model.orderedTelegramFolderChats
import com.beykus.beygram.domain.model.GroupCreationState
import com.beykus.beygram.domain.model.mayBeginGroupCreation
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.BlockedSenderKind
import com.beykus.beygram.domain.model.BlockedSendersPage
import com.beykus.beygram.domain.model.PrivacyRules
import com.beykus.beygram.domain.model.PrivacySetting
import com.beykus.beygram.domain.model.PrivacySettingState
import com.beykus.beygram.domain.model.orderedArchivedChats
import com.beykus.beygram.domain.model.orderedMainChats
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.InteractionPolicy
import com.beykus.beygram.domain.model.DeleteMessageScope
import com.beykus.beygram.domain.model.MessageReply
import com.beykus.beygram.domain.model.MessageSpecialContent
import com.beykus.beygram.domain.model.GeoPoint
import com.beykus.beygram.domain.model.OutgoingPoll
import com.beykus.beygram.domain.model.PollVoterInfo
import com.beykus.beygram.domain.model.PollVotersPage
import com.beykus.beygram.domain.model.pollVotersNextOffset
import com.beykus.beygram.domain.model.ChatNotificationPreferences
import com.beykus.beygram.domain.model.NotificationPresentation
import com.beykus.beygram.domain.model.NotificationSoundOption
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
import kotlinx.coroutines.withTimeout
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
class TdLibTelegramRepository private constructor(
    context: Context,
    policy: InteractionPolicy,
    private val nativeSecondary: TdSecondaryNativeLogin? = null
) : TelegramRepository {
    val accountId: String = nativeSecondary?.accountId ?: TdAccountStorageLayout.LEGACY_ACCOUNT_ID
    private val isLegacyAccount: Boolean get() = nativeSecondary == null
    @Volatile private var secondaryAuthorizationLost = false
    internal val hasSecondaryAuthorizationLoss: Boolean get() = secondaryAuthorizationLost

    private val appContext = context.applicationContext
    private val proxyMutex = Mutex()
    private val mihomoProxyPrefs = context.applicationContext.getSharedPreferences(if (isLegacyAccount) "mihomo_tdlib_proxy" else "mihomo_tdlib_proxy_$accountId", Context.MODE_PRIVATE)
    private val loopbackCredentials = LoopbackProxyCredentials.get(context.applicationContext)
    private val tdRequests = TdRequests { client }
    private val accountSecurity = TdAccountSecurity(tdRequests)
    private val secretChatDirectory = TdSecretChats(tdRequests)
    @Volatile private var secretChatSession = 0L
    private val payments = TdPayments(tdRequests)
    private val videoNoteLossTransport = TdVideoNoteLossTransport(tdRequests)
    private val messageDrafts = TdChatDrafts(appContext, accountId) { client }

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


    private val config = nativeSecondary?.config ?: TdLibConfig.from(context)

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

    // ---- QR login ------------------------------------------------------------
    private val qrLinks = QrLoginLinkTracker()
    @Volatile private var qrRefreshJob: Job? = null
    @Volatile private var qrStateActive = false
    @Volatile private var qrRequestInFlight = false
    /** Suppresses a late QR update that arrives after the user already asked for the phone form. */
    @Volatile private var qrCancelled = false
    /** A stale token restarts the client; request a fresh QR once it is waiting for authorization again. */
    @Volatile private var qrRetryAfterReopen = false

    // ---- Вспомогательные структуры -------------------------------------------

    /** Кэш сообщений по chatId. */
    private val pinnedVersions = ConcurrentHashMap<Long, MutableStateFlow<Long>>()
    override fun observePinnedMessagesVersion(chatId: Long): StateFlow<Long> = pinnedVersions.getOrPut(chatId) { MutableStateFlow(0L) }.asStateFlow()
    override suspend fun pinnedMessages(chatId: Long, fromMessageId: Long, forumTopicId: Long) = conversationActions.pinnedMessages(chatId, fromMessageId, forumTopicId)
    override suspend fun setMessagePinned(chatId: Long, messageId: Long, pinned: Boolean) =
        conversationActions.pinMessage(chatId, messageId, pinned).onSuccess { invalidatePins(chatId) }
    override fun observeMessageDraft(chatId: Long): StateFlow<MessageDraft> = messageDrafts.observe(chatId)
    override fun observeForumTopicDraft(chatId: Long, forumTopicId: Long): StateFlow<MessageDraft> = messageDrafts.observeTopic(chatId, forumTopicId)
    override fun updateForumTopicDraft(chatId: Long, forumTopicId: Long, transform: (MessageDraft) -> MessageDraft) =
        messageDrafts.updateTopic(chatId, forumTopicId, transform)
    override fun updateMessageDraft(chatId: Long, transform: (MessageDraft) -> MessageDraft) =
        messageDrafts.update(chatId, transform)
    private fun invalidatePins(chatId: Long) { pinnedVersions[chatId]?.update { it + 1 } }

    private val messageHistory = MessageHistoryStore()
    private val deletedMessages = ConcurrentHashMap<Long, MutableStateFlow<Set<Long>>>()
    // A forum topic is a separate history even though all of its messages have the parent chatId.
    private val topicStores = ConcurrentHashMap<Pair<Long, Long>, MessageHistoryStore>()
    private fun topicStore(chatId: Long, topicId: Long): MessageHistoryStore =
        topicStores.computeIfAbsent(chatId to topicId) { MessageHistoryStore(maxCachedChats = 1) }
    private fun forumId(message: TdApi.Message): Long =
        (message.topicId as? TdApi.MessageTopicForum)?.forumTopicId?.toLong() ?: 0L
    // Only topics whose history was loaded take live messages: an unopened topic would keep
    // every message forever and later show a gap between them and its first loaded page.
    private val loadedTopics = ConcurrentHashMap.newKeySet<Pair<Long, Long>>()
    // A newer jump or latest load of the same topic wins over a slower earlier response.
    private val topicRequests = ConcurrentHashMap<Pair<Long, Long>, Long>()
    private fun nextTopicRequest(chatId: Long, topicId: Long): Long =
        topicRequests.merge(chatId to topicId, 1L) { current, _ -> current + 1 } ?: 1L
    private fun putForumMessage(message: TdApi.Message, replaces: Long? = null) {
        val topicId = forumId(message)
        if (topicId > 0L && (message.chatId to topicId) in loadedTopics) {
            topicStore(message.chatId, topicId).put(message.toChatMessage(), replaces)
        }
    }
    private fun updateForumMessage(chatId: Long, messageId: Long, transform: (ChatMessage) -> ChatMessage) {
        topicStores.filterKeys { it.first == chatId }.values.forEach { it.update(chatId, messageId, transform) }
    }
    private val mediaFiles = MediaFileStore(TrafficUsageStore.session(accountId))
    private val autoDownloadSettingsStore = AutoDownloadSettingsStore.getInstance(appContext, accountId)
    /** Files this repository started automatically; a network or settings change may cancel them. */
    private val autoDownloads = ConcurrentHashMap<Int, AutoDownloadRequest>()
    /** Automatic downloads stopped by policy; their late TDLib error must not replace the retry button with an error. */
    private val cancelledAutoDownloads = ConcurrentHashMap.newKeySet<Int>()
    private var autoDownloadSettingsJob: Job? = null
    private val autoDownloadNetworkMonitor = AutoDownloadNetworkMonitor(appContext, authScope, ::reevaluateAutoDownloads)
    private val mediaMapper = TdMediaMapper(mediaFiles)
    private val stickerCatalog = TdStickerCatalog(tdRequests, mediaFiles)

    init {
        autoDownloadNetworkMonitor.start()
    }
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
    private val stories = TdStories({ client }, profiles, mediaFiles, accountId, File(appContext.filesDir, "uploads"))
    private val groupAdministration = TdGroupAdministration(tdRequests, profiles)
    private val privacy = TdPrivacy({ client }, profiles)
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
            pinnedChatIds = f.pinnedChatIds?.toList() ?: emptyList(),
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
        // Pinned members keep their order; a chat can't stay pinned after being excluded or removed.
        val pinned = draft.pinnedChatIds.filterNot { it in draft.excludedChatIds }
        folder.pinnedChatIds = pinned.toLongArray()
        folder.includedChatIds = (draft.includedChatIds - pinned.toSet()).toLongArray()
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

    override val activeStories: StateFlow<List<ChatStories>> = stories.items
    override fun refreshActiveStories() = stories.refreshActive()
    override fun loadMoreActiveStories() = stories.loadMoreActive()
    override val storyPostEvents: SharedFlow<StoryPostEvent> = stories.postEvents
    override suspend fun loadStory(posterChatId: Long, storyId: Int) = stories.story(posterChatId, storyId)
    override suspend fun openStory(posterChatId: Long, storyId: Int) = stories.openStory(posterChatId, storyId)
    override suspend fun closeStory(posterChatId: Long, storyId: Int) = stories.closeStory(posterChatId, storyId)
    override suspend fun storyReactions() = stories.availableReactions()
    override suspend fun setStoryReaction(posterChatId: Long, storyId: Int, reaction: ReactionOption?) =
        stories.setReaction(posterChatId, storyId, reaction)
    override suspend fun replyToStory(posterChatId: Long, storyId: Int, text: String) =
        stories.replyToStory(posterChatId, storyId, text)
    override suspend fun deleteStory(posterChatId: Long, storyId: Int) = stories.deleteStory(posterChatId, storyId)
    override suspend fun storyArchive(fromStoryId: Int) = stories.archive(fromStoryId)
    override suspend fun storyPostPermission(chatId: Long) = stories.postPermission(chatId)
    override suspend fun storyPostTargets() = stories.postTargets()
    override fun storySubmissionState(submissionId: String) = stories.submissionState(submissionId)
    override fun abandonStoryUpload(posterChatId: Long, storyId: Int) = stories.abandonUpload(posterChatId, storyId)
    override fun abandonStorySubmission(submissionId: String) = stories.abandonSubmission(submissionId)
    override suspend fun storyCaptionLimit() = stories.captionLimit()
    override suspend fun editStoryCaption(posterChatId: Long, storyId: Int, caption: String) =
        stories.editCaption(posterChatId, storyId, caption)
    override suspend fun setStoryPrivacy(storyId: Int, privacy: StoryPrivacy, selectedUserIds: List<Long>) =
        stories.editPrivacy(storyId, privacy, selectedUserIds)
    override suspend fun setStoryPostedToChatPage(posterChatId: Long, storyId: Int, posted: Boolean) =
        stories.setPostedToChatPage(posterChatId, storyId, posted)
    override suspend fun publishStory(
        submissionId: String,
        targetChatId: Long,
        media: OutgoingMedia,
        caption: String,
        privacy: StoryPrivacy,
        selectedUserIds: List<Long>,
        activePeriodSeconds: Int,
        protectContent: Boolean,
        keepInProfile: Boolean
    ) = stories.publishStory(submissionId, targetChatId, media, caption, privacy, selectedUserIds, activePeriodSeconds, protectContent, keepInProfile)

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

    override suspend fun chatNotificationPreferences(chatId: Long): Result<ChatNotificationPreferences> = requestResult {
        val chat = tdRequests(TdApi.GetChat(chatId))
        // An inherited preview or mention setting shows what the chat's notification scope currently uses.
        val scopeSettings = tdRequests(TdApi.GetScopeNotificationSettings(NotificationScope.ofChat(chat.type).toTd()))
        chat.notificationSettings.toChatNotificationPreferences(scopeSettings)
    }

    override suspend fun setChatNotificationPreferences(chatId: Long, preferences: ChatNotificationPreferences): Result<Unit> = requestResult {
        // Only the fields the user changed become explicit; the others keep following the scope.
        val settings = tdRequests(TdApi.GetChat(chatId)).notificationSettings.withChatNotificationPreferences(preferences)
        tdRequests(TdApi.SetChatNotificationSettings(chatId, settings))
        Unit
    }

    override suspend fun savedNotificationSounds(): Result<List<NotificationSoundOption>> = requestResult {
        tdRequests(TdApi.GetSavedNotificationSounds()).notificationSounds.orEmpty().mapNotNull { sound ->
            sound.title?.takeIf { it.isNotBlank() }?.let { NotificationSoundOption(sound.id, it) }
        }
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

    override suspend fun blockPrivateChat(chatId: Long): Result<Unit> = setPrivateChatBlocked(chatId, true)

    override suspend fun setPrivateChatBlocked(chatId: Long, blocked: Boolean): Result<Unit> = requestResult {
        val chat = tdRequests(TdApi.GetChat(chatId))
        val privateType = chat.type as? TdApi.ChatTypePrivate
            ?: throw IllegalArgumentException("Only private conversations can be blocked")
        if (profiles.isSelf(privateType.userId)) throw IllegalArgumentException("Cannot block your own account")
        privacy.setBlocked(BlockedSenderKind.User, privateType.userId, blocked)
    }

    override suspend fun loadPrivacySettings(): Result<List<PrivacySettingState>> = requestResult {
        privacy.loadSettings()
    }

    override suspend fun setPrivacySetting(setting: PrivacySetting, rules: PrivacyRules): Result<Unit> = requestResult {
        privacy.setSetting(setting, rules)
    }

    override suspend fun loadBlockedSenders(offset: Int, limit: Int): Result<BlockedSendersPage> = requestResult {
        privacy.loadBlockedSenders(offset, limit)
    }

    override suspend fun setSenderBlocked(kind: BlockedSenderKind, senderId: Long, blocked: Boolean): Result<Unit> = requestResult {
        privacy.setBlocked(kind, senderId, blocked)
    }

    override suspend fun reportSpam(chatId: Long): Result<Unit> = requestResult {
        reportChatAsSpam(chatId)
    }

    override suspend fun reportSpamForUser(userId: Long): Result<Unit> = requestResult {
        require(userId > 0L) { "User id must be positive" }
        // Reporting a blocked sender must not require the user to open the conversation first.
        val chat = tdRequests(TdApi.CreatePrivateChat(userId, false))
        handleNewChat(chat)
        reportChatAsSpam(chat.id)
    }

    /**
     * Telegram first asks for a report reason. Choose only the canonical spam option; sending
     * an unrelated reason would be a false report, so a missing spam option is reported as an
     * error instead. A text/message requirement is surfaced instead of inventing content.
     */
    private suspend fun reportChatAsSpam(chatId: Long) {
        var result: TdApi.ReportChatResult = tdRequests(reportChatRequest(chatId))
        if (result is TdApi.ReportChatResultOptionRequired) {
            val option = chooseSpamReportOption(result.options)
                ?: throw UserMessageException(UiText.Res(R.string.err_report_spam_unavailable))
            result = tdRequests(reportChatRequest(chatId, option.id ?: ByteArray(0)))
        }
        when (result) {
            is TdApi.ReportChatResultOk -> Unit
            is TdApi.ReportChatResultTextRequired -> {
                if (!result.isOptional) throw UserMessageException(UiText.Res(R.string.err_report_spam_requires_text))
                val retried = tdRequests(reportChatRequest(chatId, result.optionId ?: ByteArray(0)))
                if (retried !is TdApi.ReportChatResultOk) {
                    throw UserMessageException(UiText.Res(R.string.err_report_spam_unavailable))
                }
            }
            is TdApi.ReportChatResultMessagesRequired ->
                throw UserMessageException(UiText.Res(R.string.err_report_spam_requires_messages))
            else -> throw UserMessageException(UiText.Res(R.string.err_report_spam_unavailable))
        }
    }

    /**
     * Non-idempotent group creation lives with the TDLib client, not a Compose
     * coroutine or 30-second TdRequests waiter. Even after activity recreation,
     * repeated taps observe the same in-flight request rather than create a duplicate.
     */
    private val groupRequestLock = Any()
    private val groupCreationPrefs = appContext.getSharedPreferences(if (isLegacyAccount) "pending_group_creation" else "pending_group_creation_$accountId", Context.MODE_PRIVATE)
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

    override suspend fun cloudPasswordState() = accountSecurity.passwordState()
    override suspend fun setCloudPassword(
        currentPassword: String,
        newPassword: String,
        hint: String
    ) = accountSecurity.setPassword(currentPassword, newPassword, hint)
    override suspend fun setRecoveryEmail(currentPassword: String, email: String) =
        accountSecurity.setRecoveryEmail(currentPassword, email)
    override suspend fun confirmRecoveryEmail(code: String) = accountSecurity.confirmRecoveryEmail(code)
    override suspend fun resendRecoveryEmailCode() = accountSecurity.resendRecoveryEmailCode()
    override suspend fun cancelRecoveryEmailChange() = accountSecurity.cancelRecoveryEmailChange()
    override suspend fun deleteTelegramAccount(password: String): Result<Unit> {
        if (!isLegacyAccount) return accountSecurity.deleteAccount(password)
        // DeleteAccount destroys the legacy authorization and closes the client rather
        // than returning it to WaitPhoneNumber. Ask the existing close path to rebuild
        // the client so the login screen sends requests to a live instance.
        synchronized(legacyClientLock) { recreateLegacyClientOnClose = true }
        val result = accountSecurity.deleteAccount(password)
        val timedOut = result.exceptionOrNull()?.let {
            it is TimeoutCancellationException || it.cause is TimeoutCancellationException
        } == true
        // A timeout is ambiguous: DeleteAccount may have succeeded and the close may
        // still be on its way. Keep recreation armed so that close recovers the client.
        if (result.isFailure && !timedOut) {
            synchronized(legacyClientLock) { recreateLegacyClientOnClose = false }
        }
        return result
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
        val lastMessageId = chatCache[chatId]?.lastMessageId?.takeIf { it > 0L }
        val markedAsUnread = chatCache[chatId]?.isMarkedAsUnread == true
        // An empty chat can still be marked unread; clearing that marker must not require a message to view.
        if (!canMarkChatRead(lastMessageId, markedAsUnread)) throw UserMessageException(UiText.Res(R.string.err_chat_no_messages))
        if (lastMessageId != null) TdRequests { client }(TdApi.ViewMessages(chatId, longArrayOf(lastMessageId), TdApi.MessageSourceChatList(), true))
        // Opening a chat also clears an explicit "unread" marker, not only the server read watermark.
        if (markedAsUnread) TdRequests { client }(markChatReadToggleRequest(chatId))
        Unit
    }

    override suspend fun markChatUnread(chatId: Long): Result<Unit> = conversationResult {
        TdRequests { client }(markChatUnreadRequest(chatId)); Unit
    }
    private val contactBook = TdContacts({ client }, profiles)
    private val callHistory = TdCallHistory({ client }, profiles)
    override suspend fun loadContacts(): Result<List<ContactPreview>> = requestResult { contactBook.load() }
    override suspend fun addContact(firstName: String, lastName: String, phone: String): Result<Unit> =
        requestResult { contactBook.add(firstName, lastName, phone) }
    override suspend fun updateContact(userId: Long, firstName: String, lastName: String, phone: String): Result<Unit> =
        requestResult { contactBook.update(userId, firstName, lastName, phone) }
    override suspend fun deleteContact(userId: Long): Result<Unit> =
        requestResult { contactBook.delete(userId) }
    override suspend fun sendContact(chatId: Long, userId: Long): Result<Unit> {
        val card = requestResult { contactBook.shareCard(userId) }.getOrElse { return Result.failure(it) }
        return conversationActions.sendContact(chatId, card)
    }
    override suspend fun openPrivateChat(userId: Long): Result<Long> = requestResult {
        val chat = TdRequests { client }(TdApi.CreatePrivateChat(userId, false))
        handleNewChat(chat)
        chat.id
    }

    /** Device-specific E2E chat; TDLib validates the partner again before creating the keys. */
    override suspend fun createSecretChat(userId: Long): Result<Long> = requestResult {
        val chat = secretChatDirectory.create(userId)
        handleNewChat(chat)
        chat.id
    }

    override val secretChats: StateFlow<Map<Long, SecretChatDetails>> = secretChatDirectory.details

    override fun refreshSecretChat(chatId: Long) {
        val session = secretChatSession
        chatListScope.launch {
            if (session != secretChatSession) return@launch
            try {
                secretChatDirectory.load(chatId)
            } catch (_: TimeoutCancellationException) {
                // TDLib did not answer in time; a later open retries.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Details are advisory; the chat remains usable and a later open retries the refresh.
            }
            // A response that raced with logout must not resurrect in-memory secret metadata.
            if (session != secretChatSession) secretChatDirectory.remove(chatId)
        }
    }

    override suspend fun setSecretChatTtl(chatId: Long, seconds: Int): Result<Unit> = requestResult {
        secretChatDirectory.setTtl(chatId, seconds)
    }

    override suspend fun closeSecretChat(chatId: Long): Result<Unit> = requestResult {
        secretChatDirectory.close(chatId)
    }

    /**
     * Local and server chat search. TDLib returns only ids and has no offset, so [limit] grows for
     * the next page. SearchChatsOnServer is best-effort: a server-side failure must not hide the
     * locally known chats that SearchChats already returned.
     */
    override suspend fun searchGlobalChats(query: String, limit: Int): Result<GlobalSearchPage<ChatPreview>> = requestResult {
        if (query.isBlank()) return@requestResult GlobalSearchPage(emptyList(), 0)
        val pageSize = limit.coerceIn(1, GLOBAL_SEARCH_PEOPLE_MAX)
        val (local, server) = coroutineScope {
            val localRequest = async { tdRequests(TdApi.SearchChats(query, null, pageSize)) }
            val serverRequest = async {
                try {
                    tdRequests(TdApi.SearchChatsOnServer(query, null, pageSize))
                } catch (_: TimeoutCancellationException) {
                    // A slow server search must not hide local matches; only genuine caller cancellation aborts.
                    null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
            localRequest.await() to serverRequest.await()
        }
        val ids = (local.chatIds.toList() + server?.chatIds?.toList().orEmpty()).distinct()
        GlobalSearchPage(
            items = globalChatPreviews(ids),
            totalCount = maxOf(local.totalCount, server?.totalCount ?: 0)
        )
    }

    override suspend fun searchGlobalContacts(query: String, limit: Int): Result<GlobalSearchPage<ContactPreview>> = requestResult {
        if (query.isBlank()) return@requestResult GlobalSearchPage(emptyList(), 0)
        contactBook.search(query, limit.coerceIn(1, GLOBAL_SEARCH_PEOPLE_MAX))
    }

    /** Public chats are only listed here; joining happens in [openPublicChat] after an explicit tap. */
    override suspend fun searchGlobalPublicChats(query: String): Result<GlobalSearchPage<ChatPreview>> = requestResult {
        if (query.isBlank()) return@requestResult GlobalSearchPage(emptyList(), 0)
        val found = tdRequests(TdApi.SearchPublicChats(query, null))
        GlobalSearchPage(globalChatPreviews(found.chatIds.toList()), found.totalCount)
    }

    /**
     * Channels open in preview without joining. A public group is joined only when it is opened;
     * a join request awaiting approval is reported to the UI instead of being treated as an error.
     */
    override suspend fun openPublicChat(chatId: Long): Result<PublicChatOpenResult> = requestResult {
        if (chatId == 0L) throw UserMessageException(UiText.Res(R.string.err_app_open_chat_failed))
        var chat = tdRequests(TdApi.GetChat(chatId))
        val type = chat.type
        if (type is TdApi.ChatTypeSupergroup && !type.isChannel) {
            val status = try {
                tdRequests(TdApi.GetSupergroup(type.supergroupId)).status
            } catch (_: TimeoutCancellationException) {
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (status is TdApi.ChatMemberStatusBanned) {
                throw UserMessageException(UiText.Res(R.string.global_search_public_join_declined))
            }
            if (!status.isJoinedChat()) {
                val joined = try {
                    tdRequests(TdApi.JoinChat(chatId))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    throw UserMessageException(publicChatJoinError(error), error)
                }
                when (joined) {
                    is TdApi.ChatJoinResultSuccess -> {
                        val openedChatId = joined.chatId.takeIf { it != 0L } ?: chatId
                        chat = tdRequests(TdApi.GetChat(openedChatId))
                    }
                    is TdApi.ChatJoinResultRequestSent -> return@requestResult PublicChatOpenResult.JoinRequestSent
                    is TdApi.ChatJoinResultDeclined ->
                        throw UserMessageException(UiText.Res(R.string.global_search_public_join_declined))
                    is TdApi.ChatJoinResultGuardBotApprovalRequired ->
                        throw UserMessageException(UiText.Res(R.string.global_search_public_join_guard))
                    else -> throw UserMessageException(UiText.Res(R.string.global_search_public_open_failed))
                }
            }
        }
        handleNewChat(chat)
        PublicChatOpenResult.Opened(chat.id)
    }

    /** Reuses cached metadata and bounds concurrent GetChat requests for one search page. */
    private suspend fun globalChatPreviews(chatIds: List<Long>): List<ChatPreview> = coroutineScope {
        val limiter = Semaphore(6)
        chatIds.distinct().map { id ->
            async { limiter.withPermit { globalChatPreview(id) } }
        }.awaitAll().filterNotNull()
    }

    private suspend fun globalChatPreview(chatId: Long): ChatPreview? {
        chatCache[chatId]?.let { return it }
        return try {
            val preview = tdRequests(TdApi.GetChat(chatId)).toChatPreview()
            if (preview != null) chatCache[chatId] = preview
            preview
        } catch (_: TimeoutCancellationException) {
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun resolvePublicChat(username: String): Result<Long> = requestResult {
        val cleaned = username.trim().removePrefix("@")
        require(cleaned.isNotEmpty()) { "Empty public username" }
        val chat = tdRequests(TdApi.SearchPublicChat(cleaned))
        handleNewChat(chat)
        chat.id
    }

    override suspend fun resolvePhoneNumber(phone: String): Result<Long> = requestResult {
        val normalized = phone.trim().let { if (it.startsWith("+")) it else "+$it" }
        require(normalized.length > 1) { "Empty phone number" }
        val user = tdRequests(TdApi.SearchUserByPhoneNumber(normalized, false))
        val chat = tdRequests(TdApi.CreatePrivateChat(user.id, false))
        handleNewChat(chat)
        chat.id
    }

    override suspend fun checkInviteLink(url: String): Result<ChatInvitePreview> = requestResult {
        val info = tdRequests(TdApi.CheckChatInviteLink(url))
        ChatInvitePreview(
            title = info.title,
            memberCount = info.memberCount,
            createsJoinRequest = info.createsJoinRequest,
            isPublic = info.isPublic
        )
    }

    override suspend fun joinInviteLink(url: String): Result<InviteJoinOutcome> = requestResult {
        when (val result = tdRequests(TdApi.JoinChatByInviteLink(url))) {
            is TdApi.ChatJoinResultSuccess -> {
                handleNewChat(tdRequests(TdApi.GetChat(result.chatId)))
                InviteJoinOutcome.Joined(result.chatId)
            }
            is TdApi.ChatJoinResultRequestSent -> InviteJoinOutcome.RequestSent
            else -> throw UserMessageException(UiText.Res(R.string.err_app_open_chat_failed))
        }
    }

    override suspend fun resolveMessageLink(url: String): Result<MessageLinkTarget> = requestResult {
        val info = tdRequests(TdApi.GetMessageLinkInfo(url))
        val message = info.message
            ?: throw UserMessageException(UiText.Res(R.string.chat_message_open_failed))
        handleNewChat(tdRequests(TdApi.GetChat(info.chatId)))
        val forumTopicId = when (val topic = info.topicId) {
            is TdApi.MessageTopicForum -> topic.forumTopicId.toLong()
            else -> 0L
        }
        MessageLinkTarget(info.chatId, message.id, forumTopicId)
    }
    override suspend fun channelStatistics(chatId: Long): Result<ChannelStatisticsSummary> = requestResult {
        when (val statistics = tdRequests(TdApi.GetChatStatistics(chatId, false))) {
            is TdApi.ChatStatisticsChannel -> channelStatisticsSummary(chatId, statistics)
            is TdApi.ChatStatisticsSupergroup -> supergroupStatisticsSummary(chatId, statistics)
            else -> throw UserMessageException(UiText.Res(R.string.err_statistics_unavailable))
        }
    }

    private suspend fun channelStatisticsSummary(
        chatId: Long,
        statistics: TdApi.ChatStatisticsChannel
    ): ChannelStatisticsSummary = coroutineScope {
        val charts = async { loadStatisticsCharts(chatId, channelGraphSpecs(statistics)) }
        val topPosts = async { channelTopPosts(chatId, statistics.recentInteractions.orEmpty()) }
        ChannelStatisticsSummary(
            periodStart = statistics.period.startDate,
            periodEnd = statistics.period.endDate,
            isChannel = true,
            memberCount = statistics.memberCount.value.toInt(),
            memberGrowthPercent = statistics.memberCount.growthRatePercentage,
            meanViewCount = statistics.meanMessageViewCount.value.toInt(),
            meanShareCount = statistics.meanMessageShareCount.value.toInt(),
            meanReactionCount = statistics.meanMessageReactionCount.value.toInt(),
            notificationsPercent = statistics.enabledNotificationsPercentage.toInt(),
            charts = charts.await(),
            topPosts = topPosts.await()
        )
    }

    private suspend fun supergroupStatisticsSummary(
        chatId: Long,
        statistics: TdApi.ChatStatisticsSupergroup
    ): ChannelStatisticsSummary {
        val charts = loadStatisticsCharts(chatId, supergroupGraphSpecs(statistics))
        return ChannelStatisticsSummary(
            periodStart = statistics.period.startDate,
            periodEnd = statistics.period.endDate,
            isChannel = false,
            memberCount = statistics.memberCount.value.toInt(),
            memberGrowthPercent = statistics.memberCount.growthRatePercentage,
            messageCount = statistics.messageCount.value.toInt(),
            viewerCount = statistics.viewerCount.value.toInt(),
            senderCount = statistics.senderCount.value.toInt(),
            topSenders = statistics.topSenders.orEmpty().take(5).map {
                ChannelStatisticsPerson(it.userId, profiles.senderName(TdApi.MessageSenderUser(it.userId)), it.sentMessageCount)
            },
            charts = charts
        )
    }

    private data class StatisticsGraphSpec(
        val id: ChannelChartId,
        val fallbackKind: ChannelChartKind,
        val graph: TdApi.StatisticalGraph?
    )

    private fun channelGraphSpecs(statistics: TdApi.ChatStatisticsChannel) = listOf(
        StatisticsGraphSpec(ChannelChartId.Members, ChannelChartKind.Line, statistics.memberCountGraph),
        StatisticsGraphSpec(ChannelChartId.Joins, ChannelChartKind.Line, statistics.joinGraph),
        StatisticsGraphSpec(ChannelChartId.Mutes, ChannelChartKind.Line, statistics.muteGraph),
        StatisticsGraphSpec(ChannelChartId.ViewsByHour, ChannelChartKind.Bar, statistics.viewCountByHourGraph),
        StatisticsGraphSpec(ChannelChartId.ViewsBySource, ChannelChartKind.Bar, statistics.viewCountBySourceGraph),
        StatisticsGraphSpec(ChannelChartId.JoinsBySource, ChannelChartKind.Bar, statistics.joinBySourceGraph),
        StatisticsGraphSpec(ChannelChartId.Languages, ChannelChartKind.Area, statistics.languageGraph),
        StatisticsGraphSpec(ChannelChartId.MessageInteractions, ChannelChartKind.Line, statistics.messageInteractionGraph),
        StatisticsGraphSpec(ChannelChartId.MessageReactions, ChannelChartKind.Line, statistics.messageReactionGraph)
    )

    // Group graphs count members and messages, so they get their own titles instead of the channel ones.
    private fun supergroupGraphSpecs(statistics: TdApi.ChatStatisticsSupergroup) = listOf(
        StatisticsGraphSpec(ChannelChartId.GroupMembers, ChannelChartKind.Line, statistics.memberCountGraph),
        StatisticsGraphSpec(ChannelChartId.GroupJoins, ChannelChartKind.Line, statistics.joinGraph),
        StatisticsGraphSpec(ChannelChartId.MessagesByHour, ChannelChartKind.Bar, statistics.dayGraph),
        StatisticsGraphSpec(ChannelChartId.JoinsBySource, ChannelChartKind.Bar, statistics.joinBySourceGraph),
        StatisticsGraphSpec(ChannelChartId.Languages, ChannelChartKind.Area, statistics.languageGraph),
        StatisticsGraphSpec(ChannelChartId.MessageContent, ChannelChartKind.Bar, statistics.messageContentGraph),
        StatisticsGraphSpec(ChannelChartId.Actions, ChannelChartKind.Line, statistics.actionGraph),
        StatisticsGraphSpec(ChannelChartId.MessagesByWeekday, ChannelChartKind.Bar, statistics.weekGraph)
    )

    /**
     * TDLib returns some graphs either inline or as tokens that need a second request. A failed
     * secondary graph is skipped: one missing chart must not hide the rest of the statistics.
     */
    private suspend fun loadStatisticsCharts(
        chatId: Long,
        specs: List<StatisticsGraphSpec>
    ): List<ChannelStatisticsChart> = coroutineScope {
        val gate = Semaphore(4)
        specs.filter { it.graph != null }.map { spec ->
            async {
                gate.withPermit {
                    try {
                        val graph = spec.graph ?: return@withPermit null
                        val data = loadStatisticsGraph(chatId, graph)
                        data?.let { parseChannelChart(spec.id, it.jsonData, spec.fallbackKind) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // An unknown graph format is not fatal: show the charts that did parse.
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun loadStatisticsGraph(
        chatId: Long,
        graph: TdApi.StatisticalGraph
    ): TdApi.StatisticalGraphData? = when (graph) {
        is TdApi.StatisticalGraphData -> graph
        is TdApi.StatisticalGraphAsync -> {
            val loaded = try {
                tdRequests(TdApi.GetStatisticalGraph(chatId, graph.token, 0L))
            } catch (timeout: TimeoutCancellationException) {
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            loaded as? TdApi.StatisticalGraphData
        }
        else -> null
    }

    /**
     * TDLib lists only recent interactions, so "top posts" cannot cover the whole period. The
     * metrics come with the statistics; message text and date need one best-effort lookup.
     */
    private suspend fun channelTopPosts(
        chatId: Long,
        interactions: Array<out TdApi.ChatStatisticsInteractionInfo>
    ): List<ChannelTopPost> {
        val ranked = rankTopPosts(
            interactions.mapNotNull { info ->
                val messageId = (info.objectType as? TdApi.ChatStatisticsObjectTypeMessage)?.messageId
                    ?: return@mapNotNull null
                ChannelTopPost(
                    messageId = messageId,
                    viewCount = info.viewCount,
                    forwardCount = info.forwardCount,
                    reactionCount = info.reactionCount
                )
            }
        )
        if (ranked.isEmpty()) return emptyList()
        val messages: Array<out TdApi.Message> = try {
            tdRequests(TdApi.GetMessages(chatId, ranked.map { it.messageId }.toLongArray())).messages.orEmpty()
        } catch (timeout: TimeoutCancellationException) {
            emptyArray<TdApi.Message>()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyArray<TdApi.Message>()
        }
        val byId = messages.filterNotNull().associateBy { it.id }
        return ranked.map { post ->
            val message = byId[post.messageId]
            post.copy(
                preview = message?.topPostPreview() ?: UiText.Raw(""),
                date = message?.date ?: 0
            )
        }
    }

    private fun TdApi.Message.topPostPreview(): UiText = when (val text = previewText(content)) {
        is UiText.Raw -> UiText.Raw(text.text.trim().take(TOP_POST_PREVIEW_LENGTH))
        else -> text
    }

    override suspend fun channelAdminEvents(chatId: Long, fromEventId: Long, filter: ChannelEventFilter): Result<ChannelAdminEventPage> = requestResult {
        val found = tdRequests(
            TdApi.GetChatEventLog(
                chatId, "", fromEventId, ADMIN_LOG_PAGE_SIZE, adminLogFilters(filter), longArrayOf()
            )
        )
        val events = found.events.orEmpty().map { event ->
            val actorId = (event.memberId as? TdApi.MessageSenderUser)?.userId?.toLong() ?: 0L
            ChannelAdminEvent(
                id = event.id,
                date = event.date,
                actorId = actorId,
                actorName = if (actorId > 0L) profiles.senderName(TdApi.MessageSenderUser(actorId)) else "",
                text = event.action.adminText(appContext, profiles)
            )
        }
        val next = events.lastOrNull()?.id?.takeIf { it != 0L && it != fromEventId } ?: 0L
        ChannelAdminEventPage(events, next)
    }

    override suspend fun loadCallHistory(offset: String): Result<CallHistoryPage> = requestResult { callHistory.page(offset) }
    private val botKeyboards = TdBotKeyboards { client }
    private val botFeatures = TdBotFeatures { client }
    override fun observeBotKeyboard(chatId: Long) = botKeyboards.observe(chatId)
    override suspend fun dismissBotKeyboard(chatId: Long, messageId: Long) = botKeyboards.dismiss(chatId, messageId)
    override suspend fun botCallback(chatId: Long, messageId: Long, data: List<Byte>) = botKeyboards.callback(chatId, messageId, data)
    override suspend fun botCommandMenu(chatId: Long): Result<BotCommandMenu?> = botFeatures.commandMenu(chatId)
    override suspend fun inlineBotQuery(chatId: Long, username: String, query: String, offset: String): Result<InlineBotResults?> =
        botFeatures.inlineQuery(chatId, username, query, offset)
    override suspend fun sendInlineBotResult(
        chatId: Long,
        queryId: Long,
        resultId: String,
        replyToMessageId: Long?,
        forumTopicId: Long,
        options: MessageSendOptions
    ): Result<Unit> {
        leaveJumpWindow(chatId)
        conversationEffects.endActivity(chatId, ChatActivity.Typing)
        return botFeatures.sendInlineResult(chatId, queryId, resultId, replyToMessageId, forumTopicId, options)
    }
    override suspend fun openBotWebApp(
        chatId: Long,
        botUserId: Long,
        url: String,
        source: BotWebAppSource,
        forumTopicId: Long,
        replyToMessageId: Long?,
        theme: BotWebAppTheme,
        buttonText: String
    ): Result<BotWebAppLaunch> = botFeatures.openWebApp(chatId, botUserId, url, source, forumTopicId, replyToMessageId, theme, buttonText)
    override suspend fun sendBotWebAppData(botUserId: Long, buttonText: String, data: String): Result<Unit> =
        botFeatures.sendWebAppData(botUserId, buttonText, data)
    override fun closeBotWebApp(launchId: Long) {
        // TDLib launch ids are signed and may be negative; only zero means there is nothing to close.
        if (launchId == 0L) return
        authScope.launch(Dispatchers.IO) { botFeatures.closeWebApp(launchId) }
    }
    private val conversationActions = TdConversationActions({ client }, messageHistory,
        map = { it.toChatMessage() },
        onTopicDraft = { chatId, topicId, raw -> messageDrafts.receiveTopic(chatId, topicId.toLong(), raw) })
    private val chatExporter = TdChatExporter(tdRequests) { it.toExportMessage() }
    private val customEmojis = TdCustomEmojis(client = { client })
    private val messageEffects = TdMessageEffects(client = { client })
    private val conversationEffects = TdConversationEffects({ client }, policy)

    // ---- Память и кэш --------------------------------------------------------

    // Copies listed in the profile's shared media stay protected from the cache policy while that listing is recent.
    private val listedMediaFiles = ListedMediaFiles()
    private val storage = TdStorage({ client }, listOf(File(appContext.filesDir, "uploads"), File(appContext.noBackupFilesDir, "uploads")), File(appContext.filesDir, "uploads"), { messageHistory.mediaFileIds() + listedMediaFiles.current() }, accountId)
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
    override suspend fun translateMessage(chatId: Long, messageId: Long, toLanguageCode: String): Result<MessageTranslation> =
        translationResult {
            require(toLanguageCode.isNotBlank()) { "Target language must not be blank" }
            if (resolveIsSecretChat(chatId)) throw UserMessageException(UiText.Res(R.string.err_translation_secret_chat))
            // TDLib's "tone" argument is optional; the empty string asks for the default neutral translation.
            val translated = tdRequests(TdApi.TranslateMessageText(chatId, messageId, toLanguageCode, ""))
            if (translated.text.isBlank()) throw UserMessageException(UiText.Res(R.string.err_translation_failed))
            translated.toMessageTranslation(toLanguageCode)
        }
    override suspend fun recognizeSpeech(chatId: Long, messageId: Long): Result<Unit> = speechRecognitionResult {
        if (resolveIsSecretChat(chatId)) throw UserMessageException(UiText.Res(R.string.err_transcription_secret_chat))
        tdRequests(TdApi.RecognizeSpeech(chatId, messageId))
        Unit
    }
    override suspend fun rateSpeechRecognition(chatId: Long, messageId: Long, isGood: Boolean): Result<Unit> =
        speechRecognitionResult {
            tdRequests(TdApi.RateSpeechRecognition(chatId, messageId, isGood))
            Unit
        }

    /** The open chat is normally cached; fetch the type if a caller got here before the chat list was populated. */
    private suspend fun resolveIsSecretChat(chatId: Long): Boolean =
        chatCache[chatId]?.isSecret ?: (tdRequests(TdApi.GetChat(chatId)).type is TdApi.ChatTypeSecret)
    override suspend fun mediaPosition(chatId: Long, messageId: Long, kind: MediaKind) = conversationActions.mediaPosition(chatId, messageId, kind)
    override suspend fun editMessage(chatId: Long, messageId: Long, text: String, entities: List<MessageTextEntity>) = conversationActions.edit(chatId, messageId, text, entities)
    override suspend fun deleteMessage(chatId: Long, messageId: Long, scope: DeleteMessageScope) =
        conversationActions.delete(chatId, messageId, scope).onSuccess { conversationEffects.uploadFinished(chatId, messageId) }
    override suspend fun deleteMessages(chatId: Long, messageIds: List<Long>, scope: DeleteMessageScope) =
        conversationActions.deleteMessages(chatId, messageIds, scope)
            .onSuccess { messageIds.forEach { conversationEffects.uploadFinished(chatId, it) } }
    override suspend fun forwardMessage(
        fromChatId: Long,
        messageId: Long,
        toChatId: Long,
        asCopy: Boolean,
        messageThreadId: Long?,
        options: MessageSendOptions
    ) = conversationActions.forward(fromChatId, messageId, toChatId, asCopy, messageThreadId, options)
    override suspend fun forwardMessages(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        asCopy: Boolean,
        messageThreadId: Long?,
        options: MessageSendOptions
    ) = conversationActions.forwardMessages(fromChatId, messageIds, toChatId, asCopy, messageThreadId, options)
    override suspend fun messageLink(chatId: Long, messageId: Long, inMessageThread: Boolean) =
        conversationActions.messageLink(chatId, messageId, inMessageThread)
    override suspend fun availableReactions(chatId: Long, messageId: Long) = conversationActions.availableReactions(chatId, messageId)
    override suspend fun toggleReaction(chatId: Long, messageId: Long, reaction: ReactionOption, addToRecent: Boolean) =
        conversationActions.toggleReaction(chatId, messageId, reaction, addToRecent)
    override fun observeCustomEmoji(customEmojiId: Long) = customEmojis.observe(customEmojiId)
    override fun observeMessageEffect(effectId: Long) = messageEffects.observe(effectId)
    override suspend fun messageContext(chatId: Long, messageId: Long) = conversationActions.context(chatId, messageId)
    override suspend fun linkedDiscussionChatId(chatId: Long) = conversationActions.linkedDiscussionChatId(chatId)
    override suspend fun loadMessageThread(chatId: Long, messageId: Long) = conversationActions.messageThread(chatId, messageId)
    override suspend fun loadMessageThreadComments(discussionChatId: Long, threadId: Long, fromMessageId: Long) =
        conversationActions.threadComments(discussionChatId, threadId, fromMessageId)
    override suspend fun sendThreadReply(discussionChatId: Long, threadId: Long, text: String, entities: List<MessageTextEntity>, replyToMessageId: Long?) =
        conversationActions.sendThreadReply(discussionChatId, threadId, text, entities, replyToMessageId)
    override suspend fun searchMessages(chatId: Long, query: String, fromMessageId: Long, filter: ChatSearchFilter) =
        conversationActions.search(chatId, query, fromMessageId, filter = filter)
    override suspend fun searchForumTopicMessages(chatId: Long, forumTopicId: Long, query: String, fromMessageId: Long, filter: ChatSearchFilter) =
        conversationActions.search(chatId, query, fromMessageId, forumTopicId, filter)
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
                                val loaded = tdRequests(TdApi.GetChat(id))
                                val cached = loaded.toChatPreview()
                                // Retain successful off-list metadata, including for the next page
                                // and for opening a result. Do not force it into a visible chat list.
                                if (cached != null) chatCache[id] = cached
                                cached
                            } catch (_: TimeoutCancellationException) {
                                null // Metadata lookup timed out; preserve the search hits.
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
    override suspend fun exportChat(
        chatId: Long,
        target: ChatExportTarget,
        request: ChatExportRequest,
        onProgress: (ChatExportProgress) -> Unit
    ): Result<ChatExportSummary> = chatExporter.export(chatId, target, request, onProgress)
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

    override suspend fun premiumInfo(): Result<PremiumInfo> = payments.premiumInfo()
    override suspend fun premiumPaymentUrl(plan: PremiumPlan): PremiumCheckoutOutcome =
        payments.premiumPaymentUrl(plan)
    override suspend fun checkPremiumGiftCode(code: String): Result<PremiumGiftCodeInfo> =
        payments.checkPremiumGiftCode(code)
    override suspend fun applyPremiumGiftCode(code: String): PremiumGiftCodeApplyOutcome =
        payments.applyPremiumGiftCode(code)
    override suspend fun starsInfo(): Result<StarsInfo> = payments.starsInfo()
    override suspend fun starsTopUpPayment(option: StarTopUpOption): StarsTopUpOutcome =
        payments.starsTopUpPayment(option)
    override suspend fun starTransactions(offset: String): Result<StarTransactionsPage> = payments.starTransactions(offset)
    override suspend fun giftsInfo(profileId: Long, isSelf: Boolean): Result<GiftsInfo> = payments.giftsInfo(profileId, isSelf)
    override suspend fun sendGift(profileId: Long, isSelf: Boolean, giftId: Long): GiftSendOutcome =
        payments.sendGift(profileId, isSelf, giftId)

    override suspend fun loadGroupManagement(chatId: Long): Result<GroupManagementInfo> =
        groupResult { groupAdministration.info(chatId) }

    override suspend fun loadGroupMembers(
        chatId: Long,
        filter: GroupMemberFilter,
        query: String,
        offset: Int,
        limit: Int
    ): Result<GroupMemberPage> = groupResult { groupAdministration.members(chatId, filter, query, offset, limit) }

    override suspend fun addGroupMembers(chatId: Long, userIds: List<Long>): Result<GroupMemberAddition> =
        groupResult { groupAdministration.addMembers(chatId, userIds) }

    override suspend fun banGroupMember(chatId: Long, userId: Long): Result<Unit> =
        groupResult { groupAdministration.banMember(chatId, userId) }

    override suspend fun unbanGroupMember(chatId: Long, userId: Long): Result<Unit> =
        groupResult { groupAdministration.unbanMember(chatId, userId) }

    override suspend fun restrictGroupMember(chatId: Long, userId: Long): Result<Unit> =
        groupResult { groupAdministration.restrictMember(chatId, userId) }

    override suspend fun unrestrictGroupMember(chatId: Long, userId: Long, keepMembership: Boolean): Result<Unit> =
        groupResult { groupAdministration.unrestrictMember(chatId, userId, keepMembership) }

    override suspend fun setGroupMemberRights(
        chatId: Long,
        userId: Long,
        rights: GroupAdministratorRights
    ): Result<Unit> = groupResult { groupAdministration.setAdministratorRights(chatId, userId, rights) }

    override suspend fun demoteGroupMember(chatId: Long, userId: Long): Result<Unit> =
        groupResult { groupAdministration.demoteMember(chatId, userId) }

    override suspend fun loadGroupInviteLinks(chatId: Long): Result<List<GroupInviteLink>> =
        groupResult { groupAdministration.inviteLinks(chatId) }

    override suspend fun createGroupInviteLink(chatId: Long, draft: GroupInviteLinkDraft): Result<GroupInviteLink> =
        groupResult { groupAdministration.createInviteLink(chatId, draft) }

    override suspend fun editGroupInviteLink(chatId: Long, link: String, draft: GroupInviteLinkDraft): Result<GroupInviteLink> =
        groupResult { groupAdministration.editInviteLink(chatId, link, draft) }

    override suspend fun renameGroupSubscriptionInviteLink(chatId: Long, link: String, name: String): Result<GroupInviteLink> =
        groupResult { groupAdministration.renameSubscriptionInviteLink(chatId, link, name) }

    override suspend fun revokeGroupInviteLink(chatId: Long, link: String): Result<Unit> =
        groupResult { groupAdministration.revokeInviteLink(chatId, link) }

    override suspend fun loadGroupJoinRequests(chatId: Long, offset: String?, limit: Int): Result<GroupJoinRequestsPage> =
        groupResult { groupAdministration.joinRequests(chatId, offset, limit) }

    override suspend fun processGroupJoinRequest(chatId: Long, userId: Long, approve: Boolean): Result<Unit> =
        groupResult { groupAdministration.processJoinRequest(chatId, userId, approve) }

    override suspend fun saveGroupSettings(chatId: Long, edit: GroupSettingsEdit): Result<Unit> =
        groupResult { groupAdministration.saveSettings(chatId, edit) }

    override suspend fun setGroupPhoto(chatId: Long, photo: OutgoingMedia): Result<Unit> =
        groupResult { groupAdministration.setPhoto(chatId, photo) }

    override suspend fun removeGroupPhoto(chatId: Long): Result<Unit> =
        groupResult { groupAdministration.removePhoto(chatId) }

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
    @Volatile private var secondaryDisposed = false
    /** The native TDLib owner remains responsible for Client/lease shutdown. */
    internal fun disposeSecondaryRepository() {
        if (isLegacyAccount || secondaryDisposed) return
        synchronized(this) {
            if (secondaryDisposed) return
            secondaryDisposed = true
        }
        networkMonitor.stop()
        autoDownloadNetworkMonitor.stop()
        autoDownloadSettingsJob?.cancel()
        autoDownloadSettingsJob = null
        autoDownloads.clear()
        cancelledAutoDownloads.clear()
        messageDrafts.dispose()
        notificationFeed.dispose()
        // Local account removal disposes this repository without an auth-state callback: drop secret metadata here too.
        secretChatSession++
        secretChatDirectory.clear()
        com.beykus.beygram.data.media.VideoNoteLossDiagnostics.releaseRepository(this)
        chatListScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        authScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private val chatListScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Keep logout + account-ID publications ordered even if a pending profile lookup completes. */
    private val analyticsAccountLock = Any()
    private var analyticsSessionGeneration = 0L
    private var analyticsProfileJob: Job? = null
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

    private val legacyClientLifecycle =
        if (nativeSecondary == null) TdAccountClientLifecycle(appContext.filesDir) else null
    private val legacyClientLock = Any()
    private var recreateLegacyClientOnClose = false
    @Volatile private var client: Client = nativeSecondary?.client ?: createLegacyClient()

    /**
     * Reopens the legacy client after a QR reset. TDLib has no cancel-authentication API, so the client is closed
     * without logging out: a confirmation that races cancellation is then restored as a normal authorized session,
     * while an unconfirmed attempt restarts at WaitPhoneNumber.
     */
    private fun createLegacyClient(): Client {
        val lifecycle = checkNotNull(legacyClientLifecycle) { "The legacy TDLib client is not owned here" }
        return lifecycle.open(TdAccountStorageLayout.LEGACY_ACCOUNT_ID) { directories, onNativeClientClosed ->
            // Existing installs MUST keep precisely the old filesDir/tdlib database,
            // even though the registry may already contain prepared secondary IDs.
            check(config.databaseDirectory == directories.database.absolutePath &&
                config.filesDirectory == directories.files.absolutePath) {
                "TDLib legacy session storage does not match its database lease"
            }
            // Don't let TDLib include API credentials or phone numbers in logcat.
            runCatching { Client.execute(TdApi.SetLogVerbosityLevel(if (BuildConfig.DEBUG) 2 else 0)) }
            Client.create(
                /* updateHandler          = */ Client.ResultHandler { update ->
                    try {
                        handleUpdate(update)
                    } finally {
                        // TDLib owns the SQLite database until native close finishes.
                        // Never release on logout, screen disposal, or a Close request.
                        if (update is TdApi.UpdateAuthorizationState &&
                            update.authorizationState is TdApi.AuthorizationStateClosed) {
                            onNativeClientClosed()
                            onLegacyClientClosed()
                        }
                    }
                },
                /* updateExceptionHandler = */ null,
                /* defaultExceptionHandler= */ null
            )
        }
    }

    private fun isLegacyClientRecreatePending(): Boolean = synchronized(legacyClientLock) {
        recreateLegacyClientOnClose
    }

    /** Runs after the old client released its database lease, in the update handler's finally block. */
    private fun onLegacyClientClosed() {
        val recreate = synchronized(legacyClientLock) {
            if (recreateLegacyClientOnClose) {
                recreateLegacyClientOnClose = false
                true
            } else {
                false
            }
        }
        if (!recreate) return
        // Leave the update thread before opening a new TDLib client. The lease was released above, so the
        // recreation is allowed; doing it asynchronously also keeps the old callback from re-entering TDLib.
        authScope.launch {
            try {
                client = createLegacyClient()
                networkMonitor.start()
                networkMonitor.reconnect()
                autoDownloadNetworkMonitor.start()
            } catch (error: Throwable) {
                StructuredEventLog.shared.record(
                    kind = EventKind.TDLIB,
                    severity = Severity.WARNING,
                    code = "tdlib_qr_restart_failed",
                    message = "TDLib не перезапустился после отмены QR-входа",
                    attrs = mapOf("cause" to error.javaClass.simpleName)
                )
                _authState.value = AuthState.Error(UiText.Res(R.string.err_auth_failed))
            }
        }
    }

    // The repository and this callback live for the process lifetime. One client owns the database.
    private val networkMonitor = TelegramNetworkMonitor(context.applicationContext, authScope) { type ->
        if (nativeSecondary != null) {
            // Each secondary TDLib client must follow Android Wi-Fi/cellular/VPN changes.
            // Its route mutex prevents a network callback from reopening a suspended route.
            authScope.launch {
                try {
                    nativeSecondary.onConnectivityChanged(type)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    ConnectionEventLog.shared.record("Secondary SetNetworkType: ${error.javaClass.simpleName}")
                }
            }
        } else {
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
        }
    }.also { it.start() }

    init {
        // Reuse the already-authorized secondary native owner. It retains the
        // only database lease and validated network route. Do NOT instantiate
        // a second TDLib Client, release its lease or replay TDLib parameters.
        nativeSecondary?.let { secondary ->
            try {
            // Establish the already-authorized state BEFORE replaying the
            // pending updates or installing a live observer. Otherwise a new
            // message can be processed before the repository is authorized.
            val restoredReady = secondary.authPhase.value == TdAccountAuthPhase.READY &&
                _authState.value != AuthState.Ready
            if (restoredReady) handleAuthorizationState(TdApi.AuthorizationStateReady())
            // The native dispatch/replay monitor makes the handoff atomic for
            // this one account. Events before attachment are buffered.
            secondary.observeFullUpdates(::handleUpdate)
            } catch (error: Throwable) {
                // If startup replay is corrupt/overflowed, this constructor
                // can throw before an instance is returned to the coordinator.
                // Close all children now rather than leaking half-built scopes.
                disposeSecondaryRepository()
                throw error
            }
        }
    }

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

    override fun requestQrLogin() {
        if (_authState.value == AuthState.Ready) return
        authTimeoutJob?.cancel()
        qrCancelled = false
        // TDLib refreshes the token itself while authorizationStateWaitOtherDeviceConfirmation is active.
        // Calling requestQrCodeAuthentication there is rejected as "unexpected", so an explicit refresh only
        // republishes the newest link and restarts the staleness watchdog.
        if (qrStateActive) {
            val token = qrLinks.current()
            if (token != null && !qrLinks.isRefreshOverdue()) {
                _authState.value = AuthState.WaitingForQr(token.link)
                startQrRefreshWatchdog()
            } else {
                // Re-publishing an overdue token would show an unusable QR again. Restart the unauthorized client and
                // request a fresh token after it reopens; TDLib rejects a second QR request in this state.
                qrRetryAfterReopen = true
                resetLegacyQrClient(cancelled = false)
            }
            return
        }
        qrRequestInFlight = true
        _authState.value = AuthState.Loading
        startAuthTimeout()
        client.send(
            TdApi.RequestQrCodeAuthentication(longArrayOf()),
            qrErrorHandler()
        )
    }

    override fun cancelQrLogin() {
        if (_authState.value == AuthState.Ready) return
        authTimeoutJob?.cancel()
        qrRefreshJob?.cancel()
        qrRefreshJob = null
        val hadQrRequest = qrStateActive || qrRequestInFlight || _authState.value is AuthState.WaitingForQr
        if (!hadQrRequest) {
            _authState.value = AuthState.WaitingForPhone
            return
        }
        // TDLib has no cancel-authentication API. Close the still-unauthorized client instead of logging out:
        // closing never destroys an authorized session. If the other device confirmed the QR at the same moment,
        // the reopened client restores the now-authorized account; otherwise it starts again at WaitPhoneNumber.
        qrRetryAfterReopen = false
        resetLegacyQrClient(cancelled = true)
    }

    private fun resetLegacyQrClient(cancelled: Boolean) {
        authTimeoutJob?.cancel()
        qrRefreshJob?.cancel()
        qrRefreshJob = null
        qrStateActive = false
        qrRequestInFlight = false
        qrLinks.reset()
        qrCancelled = cancelled
        if (!isLegacyAccount) {
            // Secondary clients belong to TdSecondaryNativeLogin; this screen only resets the legacy login.
            _authState.value = AuthState.WaitingForPhone
            return
        }
        // Keep the phone button disabled until the replacement client reaches WaitPhoneNumber (or restores Ready).
        _authState.value = AuthState.Loading
        synchronized(legacyClientLock) { recreateLegacyClientOnClose = true }
        client.send(
            TdApi.Close(),
            Client.ResultHandler { result ->
                if (result is TdApi.Error) {
                    synchronized(legacyClientLock) { recreateLegacyClientOnClose = false }
                    if (_authState.value != AuthState.Ready) {
                        _authState.value = AuthState.Error(authErrorText(result.message))
                    }
                }
            }
        )
    }

    override fun resetError() {
        authTimeoutJob?.cancel()
        // A QR attempt can fail after TDLib has already entered WaitOtherDeviceConfirmation. Leaving that
        // state locally would make the phone form unusable, so route through the real cancel path.
        if (qrStateActive || qrRequestInFlight) {
            cancelQrLogin()
            return
        }
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
    override fun observeDeletedMessages(chatId: Long): StateFlow<Set<Long>> =
        deletedMessages.computeIfAbsent(chatId) { MutableStateFlow(emptySet()) }
    override fun observeForumTopicHistory(chatId: Long, forumTopicId: Long): StateFlow<ChatHistory> =
        topicStore(chatId, forumTopicId).observe(chatId)

    override fun loadForumTopicHistory(chatId: Long, forumTopicId: Long) {
        val store = topicStore(chatId, forumTopicId)
        loadedTopics += chatId to forumTopicId
        nextTopicRequest(chatId, forumTopicId)
        requestForumHistory(chatId, forumTopicId, 0L, store.restartFromLatest(chatId))
    }

    override fun loadOlderForumTopicMessages(chatId: Long, forumTopicId: Long) {
        val store = topicStore(chatId, forumTopicId)
        val oldest = store.observe(chatId).value.messages.firstOrNull()?.id ?: 0L
        store.beginLoad(chatId)?.let { requestForumHistory(chatId, forumTopicId, oldest, it) }
    }

    override suspend fun latestForumTopicPreview(chatId: Long, forumTopicId: Long): Result<String?> =
        conversationActions.latestForumTopicPreview(chatId, forumTopicId)

    override suspend fun loadForumTopicMessagesAround(chatId: Long, forumTopicId: Long, messageId: Long): Result<Boolean> = requestResult {
        val store = topicStore(chatId, forumTopicId)
        loadedTopics += chatId to forumTopicId
        val request = nextTopicRequest(chatId, forumTopicId)
        val result = tdRequests(TdApi.GetForumTopicHistory().apply {
            this.chatId = chatId; this.forumTopicId = forumTopicId.toInt()
            fromMessageId = messageId; offset = -15; limit = 40
        })
        if (topicRequests[chatId to forumTopicId] != request) false else {
            store.replaceWithWindow(chatId, result.messages.orEmpty().filterNotNull().map { it.toChatMessage() }, reachesLatest = false)
            true
        }
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
        // A manual tap takes ownership of a file that was being downloaded automatically.
        autoDownloads.remove(fileId)
        cancelledAutoDownloads.remove(fileId)
        if (!mediaFiles.beginDownload(fileId)) return
        sendDownload(fileId)
    }

    override fun downloadFileInBackground(fileId: Int) {
        // Avatars and preview thumbnails bypass the media policy, but their bytes are automatic traffic.
        cancelledAutoDownloads.remove(fileId)
        if (!mediaFiles.beginAutoDownload(fileId)) return
        sendDownload(fileId)
    }

    override fun autoDownloadFile(request: AutoDownloadRequest) {
        val decision = decideAutoDownload(
            settings = autoDownloadSettingsStore.settings.value,
            network = currentAutoDownloadNetwork(appContext),
            request = request
        )
        if (decision != AutoDownloadDecision.Allowed) return
        if (!mediaFiles.beginAutoDownload(request.fileId)) return
        autoDownloads[request.fileId] = request
        sendDownload(request.fileId)
    }

    private fun sendDownload(fileId: Int) {
        client.send(TdApi.DownloadFile(fileId, 16, 0, 0, false), Client.ResultHandler { result ->
            when (result) {
                is TdApi.File -> {
                    mediaFiles.update(result.toMediaFileState())
                    if (result.local?.isDownloadingCompleted == true) {
                        autoDownloads.remove(fileId)
                        cancelledAutoDownloads.remove(fileId)
                    }
                }
                is TdApi.Error -> {
                    if (cancelledAutoDownloads.remove(fileId)) {
                        mediaFiles.cancel(fileId)
                    } else {
                        autoDownloads.remove(fileId)
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
            }
        })
    }

    /** Cancels automatic downloads that the current network or settings no longer allow. */
    private fun reevaluateAutoDownloads(network: AutoDownloadNetwork) {
        if (autoDownloads.isEmpty()) return
        val settings = autoDownloadSettingsStore.settings.value
        autoDownloads.values.toList().forEach { request ->
            val state = mediaFiles.snapshot(request.fileId)
            if (state?.localPath != null) {
                autoDownloads.remove(request.fileId)
                return@forEach
            }
            val current = if (state != null && state.size > 0L) request.copy(sizeBytes = state.size) else request
            if (decideAutoDownload(settings, network, current) == AutoDownloadDecision.Allowed) return@forEach
            if (autoDownloads.remove(request.fileId) == null) return@forEach
            cancelledAutoDownloads += request.fileId
            mediaFiles.cancel(request.fileId)
            // The transfer may already be active: onlyIfPending=true would leave it consuming data.
            client.send(TdApi.CancelDownloadFile(request.fileId, /* onlyIfPending = */ false), Client.ResultHandler { })
        }
    }

    /** Pushes the local policy to TDLib for its own preloading and applies it to downloads already running. */
    private fun startAutoDownloadSettingsSync() {
        if (autoDownloadSettingsJob?.isActive != true) {
            autoDownloadSettingsJob = authScope.launch {
                autoDownloadSettingsStore.settings.collectLatest { settings ->
                    applyAutoDownloadSettings(settings)
                }
            }
        } else {
            // Re-authorization after logout does not re-emit an unchanged flow value.
            authScope.launch { applyAutoDownloadSettings(autoDownloadSettingsStore.settings.value) }
        }
    }

    private suspend fun applyAutoDownloadSettings(settings: AutoDownloadSettings) {
        reevaluateAutoDownloads(currentAutoDownloadNetwork(appContext))
        if (_authState.value != AuthState.Ready) return
        val presets = try {
            tdRequests(TdApi.GetAutoDownloadSettingsPresets())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val template = presets?.high ?: TdApi.AutoDownloadSettings()
        AutoDownloadTdlibMapping.configuredNetworks.forEach { (network, type) ->
            val mapped = AutoDownloadTdlibMapping.toTdlib(settings, network, template)
            try {
                tdRequests(TdApi.SetAutoDownloadSettings(mapped, type))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed TDLib update does not change the local policy or the manual button.
            }
        }
    }

    override fun openMessageContent(chatId: Long, messageId: Long) {
        conversationEffects.contentOpened(chatId, messageId)
    }

    override fun cancelFileDownload(fileId: Int) {
        client.send(TdApi.CancelDownloadFile(fileId, false), errorHandler())
        mediaFiles.cancel(fileId)
    }

    override suspend fun sendVideoNoteLossTest(media: OutgoingMedia, remoteCopy: File): Result<com.beykus.beygram.domain.model.VideoNoteLossRemoteResult> =
        requestResult { videoNoteLossTransport.sendAndFetchOwnTest(media, remoteCopy) }

    override suspend fun deleteVideoNoteLossTest(messageId: Long): Result<Unit> =
        videoNoteLossTransport.deleteOwnTest(messageId)

    override suspend fun sendMediaAlbum(chatId: Long, media: List<OutgoingMedia>, caption: String, replyToMessageId: Long?, entities: List<MessageTextEntity>, forumTopicId: Long, options: MessageSendOptions): Result<Unit> {
        leaveJumpWindow(chatId)
        return conversationActions.sendMediaAlbum(chatId, media, caption, replyToMessageId, entities, forumTopicId, options)
    }

    override suspend fun sendMedia(chatId: Long, media: OutgoingMedia, caption: String, replyToMessageId: Long?, entities: List<MessageTextEntity>, forumTopicId: Long, options: MessageSendOptions): Result<Unit> {
        leaveJumpWindow(chatId)
        return conversationActions.sendMedia(chatId, media, caption, replyToMessageId, entities, forumTopicId, options)
    }

    override suspend fun sendPoll(chatId: Long, poll: OutgoingPoll, replyToMessageId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> {
        leaveJumpWindow(chatId)
        return conversationActions.sendPoll(chatId, poll, replyToMessageId, forumTopicId, options)
    }

    override suspend fun sendLocation(chatId: Long, point: GeoPoint, live: Boolean, replyToMessageId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> {
        leaveJumpWindow(chatId)
        return conversationActions.sendLocation(chatId, point, live, replyToMessageId, forumTopicId, options)
    }

    override suspend fun sendContactCard(chatId: Long, userId: Long, replyToMessageId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> {
        val card = requestResult { contactBook.shareCard(userId) }.getOrElse { return Result.failure(it) }
        leaveJumpWindow(chatId)
        return conversationActions.sendContact(chatId, card, replyToMessageId, forumTopicId, options)
    }

    override suspend fun sendDice(chatId: Long, emoji: String, replyToMessageId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> {
        leaveJumpWindow(chatId)
        return conversationActions.sendDice(chatId, emoji, replyToMessageId, forumTopicId, options)
    }

    override suspend fun votePoll(chatId: Long, messageId: Long, optionIndices: List<Int>): Result<Unit> =
        conversationActions.votePoll(chatId, messageId, optionIndices)

    override suspend fun stopPoll(chatId: Long, messageId: Long): Result<Unit> =
        conversationActions.stopPoll(chatId, messageId)

    override suspend fun pollVoters(chatId: Long, messageId: Long, optionIndex: Int, offset: Int, limit: Int): Result<PollVotersPage> = requestResult {
        val page = tdRequests(TdApi.GetPollVoters(chatId, messageId, optionIndex, offset.coerceAtLeast(0), limit.coerceIn(1, 100)))
        val voters = page.voters.orEmpty().mapNotNull { it.toPollVoterInfo() }
        PollVotersPage(voters, page.totalCount, pollVotersNextOffset(offset, voters.size, page.totalCount))
    }

    override suspend fun addPollOption(chatId: Long, messageId: Long, text: String): Result<Unit> =
        conversationActions.addPollOption(chatId, messageId, text)

    private fun TdApi.PollVoter.toPollVoterInfo(): PollVoterInfo? {
        val sender = voterId ?: return null
        val userId = (sender as? TdApi.MessageSenderUser)?.userId?.toLong() ?: 0L
        val chatSenderId = (sender as? TdApi.MessageSenderChat)?.chatId ?: 0L
        val senderId = userId.takeIf { it > 0L } ?: chatSenderId
        if (senderId == 0L) return null
        return PollVoterInfo(
            senderId = senderId,
            isUser = userId > 0L,
            name = profiles.senderName(sender).ifBlank { appContext.getString(R.string.chat_poll_voter_unknown) },
            dateSeconds = date.toLong()
        )
    }

    override suspend fun stopLiveLocation(chatId: Long, messageId: Long): Result<Unit> =
        conversationActions.stopLiveLocation(chatId, messageId)

    override suspend fun contacts(): Result<List<ContactPreview>> = requestResult { contactBook.load() }
    override suspend fun sendSticker(chatId: Long, sticker: OutgoingMedia, replyToMessageId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> {
        leaveJumpWindow(chatId)
        return conversationActions.sendSticker(chatId, sticker, replyToMessageId, forumTopicId, options)
    }

    override suspend fun recentStickers(): Result<List<StickerItem>> = stickerCatalog.recentStickers()
    override suspend fun installedStickerSets(): Result<List<StickerSetSummary>> = stickerCatalog.installedStickerSets()
    override suspend fun stickerSet(setId: Long): Result<StickerSetContent> = stickerCatalog.stickerSet(setId)
    override suspend fun searchStickers(query: String, offset: Int): Result<StickerSearchPage> = stickerCatalog.searchStickers(query, offset)
    override suspend fun savedAnimations(): Result<List<AnimationItem>> = stickerCatalog.savedAnimations()
    override suspend fun recentAnimations(offset: String): Result<AnimationPage> = stickerCatalog.recentAnimations(offset)
    override suspend fun searchAnimations(query: String, offset: String): Result<AnimationPage> = stickerCatalog.searchAnimations(query, offset)
    override suspend fun isPremiumAccount(): Result<Boolean> = stickerCatalog.isPremiumAccount()

    override suspend fun scheduledMessages(chatId: Long): Result<List<ChatMessage>> = conversationActions.scheduledMessages(chatId)
    override suspend fun deleteScheduledMessage(chatId: Long, messageId: Long): Result<Unit> =
        conversationActions.deleteScheduledMessage(chatId, messageId)
    override suspend fun rescheduleMessage(chatId: Long, messageId: Long, atSeconds: Long): Result<Unit> =
        conversationActions.rescheduleMessage(chatId, messageId, atSeconds)
    override suspend fun sendScheduledNow(chatId: Long, messageId: Long): Result<Unit> =
        conversationActions.sendScheduledNow(chatId, messageId)
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

    override suspend fun newerMessages(chatId: Long, forumTopicId: Long, afterMessageId: Long): Result<List<ChatMessage>> =
        conversationActions.newerMessages(chatId, forumTopicId, afterMessageId)

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

    override suspend fun forumTopics(chatId: Long, onPage: (List<ForumTopic>) -> Unit): Result<List<ForumTopic>?> =
        conversationActions.forumTopics(chatId, { partial ->
            // A partially visible topic can be opened immediately: initialize its
            // read watermark before any UI callback can navigate into that topic.
            partial.forEach { topic ->
                topicStore(chatId, topic.id).apply {
                    readInbox(chatId, topic.lastReadInboxMessageId)
                    readOutbox(chatId, topic.lastReadOutboxMessageId)
                }
            }
            onPage(partial)
        }).onSuccess { topics ->
            topics?.forEach { topic ->
                topicStore(chatId, topic.id).apply {
                    readInbox(chatId, topic.lastReadInboxMessageId)
                    readOutbox(chatId, topic.lastReadOutboxMessageId)
                }
            }
        }

    override suspend fun sendMessage(chatId: Long, text: String, replyToMessageId: Long?, entities: List<MessageTextEntity>, forumTopicId: Long, clearDraft: Boolean, options: MessageSendOptions): Result<Unit> {
        leaveJumpWindow(chatId)
        conversationEffects.endActivity(chatId, ChatActivity.Typing)
        return conversationActions.sendText(chatId, text, replyToMessageId, entities, forumTopicId, clearDraft, options)
    }

    // =========================================================================
    // Обработка обновлений TDLib
    // =========================================================================

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateMessageIsPinned -> {
                invalidatePins(update.chatId)
                val transform: (ChatMessage) -> ChatMessage = { it.copy(isPinned = update.isPinned) }
                messageHistory.update(update.chatId, update.messageId, transform)
                updateForumMessage(update.chatId, update.messageId, transform)
            }
            is TdApi.UpdateMessageContent -> invalidatePins(update.chatId)
            is TdApi.UpdateDeleteMessages -> if (update.isPermanent) invalidatePins(update.chatId)
        }
        profiles.handle(update)
        stories.handle(update)
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
            is TdApi.UpdateChatIsMarkedAsUnread -> {
                chatCache[update.chatId]?.let { chatCache[update.chatId] = it.copy(isMarkedAsUnread = update.isMarkedAsUnread) }
                publishChat(update.chatId)
            }
            is TdApi.UpdateChatReadOutbox -> {
                messageHistory.readOutbox(update.chatId, update.lastReadOutboxMessageId)
                chatCache[update.chatId]?.let { chatCache[update.chatId] = it.copy(lastReadOutboxMessageId = update.lastReadOutboxMessageId) }
                publishChat(update.chatId)
            }
            is TdApi.UpdateMessageContent -> {
                val secretChat = isSecretChat(update.chatId)
                val attachment = mediaMapper.media(update.newContent)?.let {
                    if (secretChat) it.copy(isSecretChat = true) else it
                }
                val service = serviceMessages.text(update.newContent)
                val special = mediaMapper.special(update.newContent)?.takeIf { attachment == null && service == null }
                val transform: (ChatMessage) -> ChatMessage = { message ->
                    val rendered = (update.newContent as? TdApi.MessageCall)?.let {
                        serviceMessages.callText(it, message.isOutgoing)
                    } ?: service
                    message.withUpdatedContent(update.newContent, attachment, special,
                        contentText(update.newContent, attachment != null),
                        messageFormattedText(update.newContent)?.domainEntities().orEmpty(), rendered,
                        linkPreview = mediaMapper.linkPreview(update.newContent, secretChat))
                }
                messageHistory.update(update.chatId, update.messageId, transform)
                updateForumMessage(update.chatId, update.messageId, transform)
                // TDLib can finalize a call without emitting UpdateChatLastMessage.
                // Re-render only while this message remains the chat's last.
                val preview = chatCache[update.chatId]
                val call = update.newContent as? TdApi.MessageCall
                if (preview?.lastMessageId == update.messageId && call != null) {
                    val rendered = UiText.Raw(serviceMessages.callText(call, preview.lastMessageOutgoing))
                    if (preview.lastMessage != rendered) {
                        chatCache[update.chatId] = preview.copy(lastMessage = rendered)
                        publishChat(update.chatId)
                    }
                }
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
                val replyCount = update.interactionInfo?.replyInfo?.replyCount
                messageHistory.update(update.chatId, update.messageId) {
                    it.copy(reactions = messageReactions(update.interactionInfo), replyCount = replyCount ?: it.replyCount)
                }
                updateForumMessage(update.chatId, update.messageId) {
                    it.copy(reactions = messageReactions(update.interactionInfo), replyCount = replyCount ?: it.replyCount)
                }
            }
            is TdApi.UpdateOption -> conversationActions.option(update.name, update.value)
            is TdApi.UpdateActiveEmojiReactions -> conversationActions.activeEmojiReactions(update.emojis.orEmpty().toList())
            is TdApi.UpdateChatTitle          -> handleChatTitleChange(update.chatId, update.title)
            is TdApi.UpdateSupergroup, is TdApi.UpdateBasicGroup -> chatCache.keys.toList().forEach(::refreshPostingRight)
            is TdApi.UpdateChatPermissions -> refreshPostingRight(update.chatId)
            is TdApi.UpdateChatHasProtectedContent -> {
                chatCache[update.chatId]?.let { chatCache[update.chatId] = it.copy(hasProtectedContent = update.hasProtectedContent) }
                publishChat(update.chatId)
            }
            is TdApi.UpdateSecretChat -> {
                if (_authState.value == AuthState.Ready) {
                    val chatId = chatCache.keys.firstOrNull { profiles.chat(it)?.type?.secretChatIdOrNull() == update.secretChat.id }
                    if (chatId != null) {
                        secretChatDirectory.apply(chatId, update.secretChat, profiles.chat(chatId)?.messageAutoDeleteTime ?: 0)
                    }
                }
            }
            is TdApi.UpdateChatMessageAutoDeleteTime -> {
                if (_authState.value == AuthState.Ready) secretChatDirectory.applyTtl(update.chatId, update.messageAutoDeleteTime)
            }
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
            is TdApi.UpdateFile -> {
                val state = update.file.toMediaFileState()
                mediaFiles.update(state)
                val fileId = update.file.id
                if (!state.isDownloading && state.localPath == null && cancelledAutoDownloads.remove(fileId)) {
                    mediaFiles.cancel(fileId)
                }
                val tracked = autoDownloads[fileId]
                if (tracked != null) {
                    if (state.localPath != null) {
                        autoDownloads.remove(fileId)
                        cancelledAutoDownloads.remove(fileId)
                    } else if (state.size > 0L && state.size != tracked.sizeBytes) {
                        autoDownloads[fileId] = tracked.copy(sizeBytes = state.size)
                        reevaluateAutoDownloads(currentAutoDownloadNetwork(appContext))
                    }
                }
            }
            is TdApi.UpdateMessageSendSucceeded -> {
                videoNoteLossTransport.onSendSucceeded(update)
                VideoNoteSendTracking.tracker.onSucceeded(update.message.chatId, update.oldMessageId)
                reportBotChannel.onSendSucceeded(update)
                cleanupSentUploads(update.message)
                if (update.message.schedulingState == null) {
                    messageHistory.put(update.message.toChatMessage(), update.oldMessageId)
                    putForumMessage(update.message, update.oldMessageId)
                    conversationActions.hydrateReply(update.message)
                }
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
                if (update.message.schedulingState == null) {
                    messageHistory.put(update.message.toChatMessage(), update.oldMessageId)
                    putForumMessage(update.message, update.oldMessageId)
                }
                conversationEffects.uploadFinished(update.message.chatId, update.oldMessageId)
            }
            is TdApi.UpdateDeleteMessages -> {
                update.messageIds.forEach { conversationEffects.uploadFinished(update.chatId, it) }
                if (update.isPermanent) {
                    messageHistory.delete(update.chatId, update.messageIds.toList())
                    topicStores.filterKeys { it.first == update.chatId }.values.forEach { it.delete(update.chatId, update.messageIds.toList()) }
                    if (update.messageIds.isNotEmpty()) {
                        deletedMessages[update.chatId]?.update { it + update.messageIds.toList() }
                    }
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
                    markSentUpload(folder, tdFile.id, accountId)
                    return@runCatching
                }
                if (content is TdApi.MessageVideoNote) {
                    // A session folder can hold segments that still wait to be sent, so only this segment's files and an emptied folder go.
                    file.delete()
                    // The owner mark stays in the session folder until its last segment is sent, so an emptied folder holds only that mark.
                    file.parentFile?.takeIf { it.parentFile == uploads }?.let { removeUploadFolderIfOnlyOwnerMarker(it) }
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
        ConnectionEventLog.shared.record("Вход в аккаунт: ${state.authLogLabel()}")
        StructuredEventLog.shared.record(
            kind = EventKind.TDLIB,
            severity = Severity.INFO,
            code = "tdlib_auth",
            message = state.authLogLabel(),
            attrs = mapOf("state" to state.authWireState())
        )
        if (BuildConfig.DEBUG) android.util.Log.i(TelegramNetworkMonitor.TAG, "TDLib authorization: ${state.javaClass.simpleName}")
        authTimeoutJob?.cancel()
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                if (isLegacyAccount) client.send(buildTdLibParameters(), errorHandler())
                // The secondary native owner itself configures TDLib parameters,
                // proxy and listener while network is forced OFF.
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                if (!isLegacyAccount) secondaryAuthorizationLost = true
                val restartQr = qrRetryAfterReopen
                qrRetryAfterReopen = false
                clearQrLoginState()
                _authState.value = AuthState.WaitingForPhone
                if (restartQr && isLegacyAccount) requestQrLogin()
            }
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                qrRequestInFlight = false
                // A late link may arrive between the cancel action and the client closing.
                // It must not pull the user back from the phone form.
                if (qrCancelled) return
                qrStateActive = true
                when (val update = qrLinks.accept(state.link)) {
                    is QrLoginLinkUpdate.Refreshed -> {
                        _authState.value = qrAuthState(update.token.link)
                        startQrRefreshWatchdog()
                    }
                    QrLoginLinkUpdate.Unchanged -> {
                        // TDLib can repeat the same link; treat it as a refresh so the stale watchdog restarts.
                        qrLinks.current()?.let {
                            _authState.value = qrAuthState(it.link)
                            startQrRefreshWatchdog()
                        }
                    }
                    QrLoginLinkUpdate.Invalid -> {
                        _authState.value = AuthState.Error(UiText.Res(R.string.err_auth_qr_link))
                    }
                }
            }
            is TdApi.AuthorizationStateWaitCode -> {
                clearQrLoginState()
                _authState.value = AuthState.WaitingForCode(submittedPhone)
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                clearQrLoginState()
                _authState.value = AuthState.WaitingForPassword(state.passwordHint.orEmpty())
            }
            is TdApi.AuthorizationStateReady -> {
                clearQrLoginState()
                secondaryAuthorizationLost = false
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
                startAutoDownloadSettingsSync()
                val analyticsGeneration = synchronized(analyticsAccountLock) {
                    analyticsSessionGeneration++
                    analyticsProfileJob?.cancel()
                    analyticsSessionGeneration
                }
                profiles.refreshMe()
                val profileJob = chatListScope.launch {
                    val me = runCatching {
                        withTimeout(60_000) { profiles.me.first { !it.isLoading && it.error == null && it.info != null }.info }
                    }.getOrNull()
                    synchronized(analyticsAccountLock) {
                        // Authorization state can change on the TDLib thread while this suspends.
                        // Serializing with logout means the old ID can never win after an empty list.
                        if (isLegacyAccount && analyticsSessionGeneration == analyticsGeneration &&
                            _authState.value == AuthState.Ready) {
                            AnalyticsSync.onAccountsChanged(listOfNotNull(me?.id?.takeIf { it > 0 }))
                        }
                    }
                }
                synchronized(analyticsAccountLock) {
                    if (analyticsSessionGeneration == analyticsGeneration) analyticsProfileJob = profileJob
                    else profileJob.cancel()
                }
                conversationActions.initialize()
                conversationEffects.authorized()
                notificationFeed.initialize()
                stories.authorized()
                cacheMaintenance.authorized()
                messageDrafts.connectionReady()
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                if (!isLegacyAccount) secondaryAuthorizationLost = true
                autoDownloads.clear()
                cancelledAutoDownloads.clear()
                clearQrLoginState(resetCancellation = false)
                synchronized(analyticsAccountLock) {
                    analyticsSessionGeneration++
                    analyticsProfileJob?.cancel()
                    analyticsProfileJob = null
                    if (isLegacyAccount) AnalyticsSync.onAccountsChanged(emptyList())
                }
                groupRequestGeneration.incrementAndGet()
                groupCreationPrefs.edit().remove("unconfirmed").apply()
                _groupCreation.value = GroupCreationState.Idle
                secretChatSession++
                secretChatDirectory.clear()
                clearFolderState()
                messageDrafts.signedOut()
                stories.signedOut()
                _authState.value = if (isLegacyAccount && isLegacyClientRecreatePending()) {
                    AuthState.Loading
                } else {
                    AuthState.WaitingForPhone
                }
                conversationEffects.signedOut()
                cacheMaintenance.signedOut()
            }
            is TdApi.AuthorizationStateClosed -> {
                val reopeningLegacyClient = isLegacyAccount && isLegacyClientRecreatePending()
                networkMonitor.stop()
                autoDownloadNetworkMonitor.stop()
                autoDownloads.clear()
                cancelledAutoDownloads.clear()
                clearQrLoginState(resetCancellation = false)
                synchronized(analyticsAccountLock) {
                    analyticsSessionGeneration++
                    analyticsProfileJob?.cancel()
                    analyticsProfileJob = null
                    if (isLegacyAccount) AnalyticsSync.onAccountsChanged(emptyList())
                }
                clearFolderState()
                // While the replacement client starts, the phone button must not send SetAuthenticationPhoneNumber
                // to the closed client. WaitTdlibParameters -> WaitPhoneNumber will publish WaitingForPhone again.
                _authState.value = if (reopeningLegacyClient) AuthState.Loading else AuthState.WaitingForPhone
                secretChatSession++
                secretChatDirectory.clear()
                conversationEffects.signedOut()
                cacheMaintenance.signedOut()
                stories.signedOut()
                if (!isLegacyAccount) disposeSecondaryRepository()
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
                val store = ArchiveSettingsStore.get(appContext, accountId)
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

    /** Republishes the chat only when a posting/poll right changed, so unrelated group updates do not wake its observers. */
    private fun refreshPostingRight(chatId: Long) {
        val existing = chatCache[chatId] ?: return
        val chat = profiles.chat(chatId) ?: return
        val canPost = profiles.canPost(chat)
        val canSendPolls = profiles.canSendPolls(chat)
        val isChannelAdmin = profiles.isChannelAdministrator(chat)
        if (canPost == existing.canPost && canSendPolls == existing.canSendPolls && isChannelAdmin == existing.isChannelAdmin) return
        chatCache[chatId] = existing.copy(canPost = canPost, canSendPolls = canSendPolls, isChannelAdmin = isChannelAdmin)
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
        // TDLib keeps scheduled messages in a separate queue; only delivered messages belong to the chat history.
        if (message.schedulingState == null) {
            messageHistory.put(message.toChatMessage())
            putForumMessage(message)
            conversationActions.hydrateReply(message)
        }
        val chat = chatCache[message.chatId]
        // Muted archived chats stay in the archive, as they do in Telegram. Until the chat's notification settings are
        // known, it stays in the archive too: it may be muted.
        if (!message.isOutgoing && chat != null && chat.archiveOrder > 0L && muteState.isKnown(message.chatId) &&
            !muteState.isMuted(message.chatId) && ArchiveSettingsStore.get(appContext, accountId).state.value.returnOnNewMessage
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
        val secretChat = isSecretChat(chatId)
        val event = serviceMessages.event(this) { profiles.senderName(TdApi.MessageSenderUser(it)) }
        val service = event?.text
        val special = mediaMapper.special(content)?.takeIf { attachment == null && service == null && content !is TdApi.MessageText }
        val scheduledAt = (schedulingState as? TdApi.MessageSchedulingStateSendAtDate)?.sendDate?.takeIf { it > 0 }?.toLong()
        if (event != null) hydrateServiceMember(this, event.userId)
        return ChatMessage(
        id = id,
        chatId = chatId,
        text = service ?: contentText(content, attachment != null),
        time = scheduledAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000L)) } ?: toTimeString(),
        isOutgoing = isOutgoing,
        dateSeconds = date.toLong(),
        sendState = sendingState.toSendState(),
        media = if (secretChat) attachment?.copy(isSecretChat = true) else attachment,
        special = special,
        mediaAlbumId = mediaAlbumId,
        senderName = profiles.senderName(senderId),
        senderId = (this.senderId as? TdApi.MessageSenderUser)?.userId?.toLong() ?: 0L,
        isEdited = editDate > 0,
        entities = messageFormattedText(content)?.domainEntities().orEmpty(),
        inlineKeyboard = inlineKeyboard(replyMarkup),
        reactions = messageReactions(interactionInfo),
        serviceText = service,
        isCallEvent = content is TdApi.MessageCall,
        serviceUserId = event?.userId ?: 0L,
        forumTopicId = forumId(this),
        messageThreadId = (topicId as? TdApi.MessageTopicThread)?.messageThreadId ?: 0L,
        isPinned = isPinned,
        isChannelPost = isChannelPost,
        replyCount = interactionInfo?.replyInfo?.replyCount ?: 0,
        linkPreview = if (event == null) mediaMapper.linkPreview(content, secretChat) else null,
        scheduledAtSeconds = scheduledAt,
        effectId = effectId,
        selfDestruct = messageSelfDestructOf(selfDestructType),
        selfDestructInSeconds = selfDestructIn.takeIf { it > 0.0 }?.toLong(),
        autoDeleteInSeconds = autoDeleteIn.takeIf { it > 0.0 }?.toLong(),
        expiredMediaKind = expiredMediaKindOf(content),
        reply = (replyTo as? TdApi.MessageReplyToMessage)?.let { reply ->
            MessageReply(reply.chatId.takeIf { it != 0L } ?: chatId, reply.messageId,
                reply.quote?.text?.text?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: reply.content?.let { replyText(it) } ?: UiText.Res(R.string.message_title))
        }
    )
    }

    /** Export projection avoids registering media files in MediaFileStore, so a huge history adds no UI state. */
    private fun TdApi.Message.toExportMessage(): ChatExportMessage {
        val media = content.toChatExportMedia()
        val event = serviceMessages.event(this) { profiles.senderName(TdApi.MessageSenderUser(it)) }
        return ChatExportMessage(
            id = id,
            dateSeconds = date.toLong(),
            isOutgoing = isOutgoing,
            senderId = when (val sender = senderId) {
                is TdApi.MessageSenderUser -> sender.userId.toLong()
                is TdApi.MessageSenderChat -> sender.chatId
                else -> 0L
            },
            senderName = profiles.senderName(senderId),
            text = event?.text ?: contentText(content, media != null),
            isEdited = editDate > 0,
            serviceText = event?.text,
            replyToMessageId = (replyTo as? TdApi.MessageReplyToMessage)?.messageId ?: 0L,
            canBeSaved = canBeSaved,
            media = media
        )
    }

    /** The chat cache is normally warm; the stored TdApi.Chat covers a message that arrives first. */
    private fun isSecretChat(chatId: Long): Boolean =
        chatCache[chatId]?.isSecret == true || (profiles.chat(chatId)?.type is TdApi.ChatTypeSecret)

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
            isSecret = type.isSecretChatType(),
            hasProtectedContent = hasProtectedContent,
            isSavedMessages = saved,
            isMarkedAsUnread = isMarkedAsUnread,
            isPinned = mainPosition?.isPinned == true,
            order = mainPosition?.order ?: 0L,
            archiveOrder = archivePosition?.order ?: 0L,
            lastMessageId = lastMessage?.id ?: 0L,
            lastMessageOutgoing = lastMessage?.isOutgoing == true,
            lastMessageSender = lastMessage?.let { profiles.senderName(it.senderId) }.orEmpty(),
            lastMessageSendState = lastMessage?.sendingState.toSendState(),
            lastReadOutboxMessageId = lastReadOutboxMessageId,
            canPost = profiles.canPost(this),
            canSendPolls = profiles.canSendPolls(this),
            isChannelAdmin = profiles.isChannelAdministrator(this),
            photo = profiles.photo(photo),
            folderPositions = folderPositions
        )
    }

    private fun contentText(content: TdApi.MessageContent, hasMedia: Boolean): String = when {
        content is TdApi.MessageContact -> content.contact.cardText()
        hasMedia -> mediaMapper.caption(content)
        else -> (content as? TdApi.MessageText)?.text?.text.orEmpty()
    }

    /** Contact messages are rendered as a text bubble: name and (when visible) the phone number. */
    private fun TdApi.Contact.cardText(): String =
        listOf(displayName(), phoneNumber.orEmpty()).filter { it.isNotBlank() }.distinct().joinToString("\n")

    private fun TdApi.Contact.displayName(): String =
        listOf(firstName.orEmpty(), lastName.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { phoneNumber.orEmpty() }

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
            is TdApi.MessageContact   -> c.contact.displayName().takeIf { it.isNotBlank() }?.let { UiText.Raw(it) }
                ?: UiText.Res(R.string.message_title)
            is TdApi.MessagePoll      -> c.poll.question?.text?.takeIf { it.isNotBlank() }
                ?.let { UiText.Res(R.string.chat_preview_poll, listOf(it.take(80))) }
                ?: UiText.Res(R.string.chat_preview_poll_empty)
            is TdApi.MessageLocation  -> UiText.Res(R.string.chat_preview_location)
            is TdApi.MessageLiveLocation -> UiText.Res(R.string.chat_preview_live_location)
            is TdApi.MessageVenue     -> c.venue.title?.takeIf { it.isNotBlank() }
                ?.let { UiText.Res(R.string.chat_preview_venue, listOf(it.take(80))) }
                ?: UiText.Res(R.string.chat_preview_location)
            is TdApi.MessageDice      -> UiText.Res(R.string.chat_preview_dice, listOf(c.emoji.orEmpty()))
            is TdApi.MessagePhoto     -> UiText.Res(R.string.chat_preview_photo)
            is TdApi.MessageVideo     -> UiText.Res(R.string.chat_preview_video)
            is TdApi.MessageAudio     -> UiText.Res(R.string.chat_preview_audio)
            is TdApi.MessageDocument  -> UiText.Res(R.string.chat_preview_document)
            is TdApi.MessageSticker   -> UiText.Res(R.string.chat_preview_sticker, listOf(c.sticker.emoji))
            is TdApi.MessageVoiceNote -> UiText.Res(R.string.chat_preview_voice)
            is TdApi.MessageVideoNote -> UiText.Res(R.string.chat_preview_video_note)
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

    /** QR requests have their own cancellation gate so a late error cannot reopen the QR screen after Cancel. */
    private fun qrErrorHandler(): Client.ResultHandler =
        Client.ResultHandler { result ->
            if (result is TdApi.Error) {
                authTimeoutJob?.cancel()
                qrRequestInFlight = false
                if (!qrCancelled && _authState.value !is AuthState.WaitingForQr) {
                    _authState.value = AuthState.Error(authErrorText(result.message))
                }
            }
        }

    /**
     * Restarts the stale-token watchdog. TDLib refreshes the link automatically; if no update arrives for
     * [QR_LOGIN_REFRESH_TIMEOUT_MS], the QR is no longer trustworthy and the user gets a retry action.
     */
    private fun startQrRefreshWatchdog() {
        qrRefreshJob?.cancel()
        qrRefreshJob = authScope.launch {
            delay(QR_LOGIN_REFRESH_TIMEOUT_MS)
            if (qrStateActive && _authState.value is AuthState.WaitingForQr &&
                qrLinks.isRefreshOverdue(System.currentTimeMillis())) {
                _authState.value = AuthState.Error(UiText.Res(R.string.err_auth_qr_stale))
            }
        }
    }

    private fun clearQrLoginState(resetCancellation: Boolean = true) {
        qrRefreshJob?.cancel()
        qrRefreshJob = null
        qrStateActive = false
        qrRequestInFlight = false
        qrLinks.reset()
        if (resetCancellation) qrCancelled = false
    }

    /** Известные коды TDLib переводим в текст; остальные сообщения TDLib на экран не выводим. */
    private fun authErrorText(message: String?): UiText {
        val text = message.orEmpty()
        return when {
            text.contains("requestQrCodeAuthentication") || text.contains("QR code authentication") ->
                UiText.Res(R.string.err_auth_qr_failed)
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
                instance ?: TdLibTelegramRepository(context.applicationContext, policy).also {
                    instance = it
                    MultiAccountDiagnostics.milestone(
                        code = "legacy_repository",
                        stage = "created",
                        accountId = "legacy"
                    )
                }
            }

        /**
         * A secondary's TDLib Client is already owned by TdAccountNativeClientOwner.
         * Its single repository is cached on TdSecondaryNativeLogin, not globally.
         * This method never opens, logs out, or migrates the original database.
         */
        internal fun forExistingSecondary(
            context: Context,
            native: TdSecondaryNativeLogin,
            policy: InteractionPolicy = InteractionPolicy.Standard
        ): TdLibTelegramRepository {
            require(native.accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID)
            check(native.authPhase.value == TdAccountAuthPhase.READY && !native.routePaused.value)
            return TdLibTelegramRepository(context.applicationContext, policy, native)
        }
    }
}

internal const val GLOBAL_SEARCH_PEOPLE_MAX = 50

/** A chat is already joined when TDLib reports any membership status except left/banned/restricted-without-membership. */
private fun TdApi.ChatMemberStatus?.isJoinedChat(): Boolean = when (this) {
    is TdApi.ChatMemberStatusMember,
    is TdApi.ChatMemberStatusCreator,
    is TdApi.ChatMemberStatusAdministrator -> true
    is TdApi.ChatMemberStatusRestricted -> isMember
    else -> false
}

/** JoinChat errors are user-facing: restricted groups must not look like a generic network failure. */
private fun publicChatJoinError(error: Exception): UiText {
    val text = error.message.orEmpty()
    return when {
        text.contains("FLOOD_WAIT") || text.contains("Too Many Requests") ->
            UiText.Res(R.string.err_tdlib_flood_wait)
        text.contains("CHANNEL_PRIVATE") || text.contains("CHANNEL_INVALID") ||
            text.contains("CHAT_PRIVATE") || text.contains("CHAT_NOT_FOUND") ->
            UiText.Res(R.string.global_search_public_open_failed)
        text.contains("CHAT_RESTRICTED") || text.contains("CHANNEL_JOIN_TOO_MUCH") ||
            text.contains("USERS_TOO_MUCH") || text.contains("CHAT_MEMBER_ADD_FAILED") ->
            UiText.Res(R.string.global_search_public_join_declined)
        else -> UiText.Res(R.string.global_search_public_open_failed)
    }
}

private const val ADMIN_LOG_PAGE_SIZE = 50
private const val TOP_POST_PREVIEW_LENGTH = 200

/** Maps one UI filter to the TDLib switches; unselected groups stay false so no unrelated events are fetched. */
internal fun adminLogFilters(filter: ChannelEventFilter): TdApi.ChatEventLogFilters =
    TdApi.ChatEventLogFilters().apply {
        when (filter) {
            ChannelEventFilter.All -> {
                messageEdits = true; messageDeletions = true; messagePins = true
                memberJoins = true; memberLeaves = true; memberInvites = true
                memberPromotions = true; memberRestrictions = true; memberTagChanges = true
                infoChanges = true; settingChanges = true; inviteLinkChanges = true
                videoChatChanges = true; forumChanges = true; subscriptionExtensions = true
            }
            ChannelEventFilter.Messages -> {
                messageEdits = true; messageDeletions = true; messagePins = true
            }
            ChannelEventFilter.Members -> {
                memberJoins = true; memberLeaves = true; memberInvites = true
                memberPromotions = true; memberRestrictions = true; memberTagChanges = true
            }
            ChannelEventFilter.Settings -> {
                infoChanges = true; settingChanges = true; inviteLinkChanges = true
                videoChatChanges = true; forumChanges = true; subscriptionExtensions = true
            }
        }
    }

/** Human-readable admin-log action. Unknown/new TDLib actions fall back to a neutral settings label. */
private fun TdApi.ChatEventAction.adminText(context: Context, profiles: TdProfiles): String = when (this) {
    is TdApi.ChatEventTitleChanged -> context.getString(R.string.channel_event_title_changed, newTitle)
    is TdApi.ChatEventPhotoChanged -> context.getString(R.string.channel_event_photo_changed)
    is TdApi.ChatEventDescriptionChanged -> context.getString(R.string.channel_event_description_changed)
    is TdApi.ChatEventUsernameChanged -> context.getString(R.string.channel_event_username_changed, newUsername)
    is TdApi.ChatEventMessageDeleted -> context.getString(R.string.channel_event_message_deleted)
    is TdApi.ChatEventMessageEdited -> context.getString(R.string.channel_event_message_edited)
    is TdApi.ChatEventMessagePinned -> context.getString(R.string.channel_event_message_pinned)
    is TdApi.ChatEventMessageUnpinned -> context.getString(R.string.channel_event_message_unpinned)
    is TdApi.ChatEventMemberJoined, is TdApi.ChatEventMemberJoinedByInviteLink,
    is TdApi.ChatEventMemberJoinedByRequest -> context.getString(R.string.channel_event_member_joined)
    is TdApi.ChatEventMemberLeft -> context.getString(R.string.channel_event_member_left)
    is TdApi.ChatEventMemberInvited -> context.getString(
        R.string.channel_event_member_invited,
        profiles.senderName(TdApi.MessageSenderUser(userId))
    )
    is TdApi.ChatEventMemberPromoted -> context.getString(
        R.string.channel_event_member_promoted,
        profiles.senderName(TdApi.MessageSenderUser(userId))
    )
    is TdApi.ChatEventMemberRestricted -> context.getString(
        R.string.channel_event_member_restricted,
        profiles.senderName(memberId)
    )
    else -> context.getString(R.string.channel_event_settings_changed)
}

/** A manual unread marker can be cleared without a message; a plain empty chat has nothing to mark read. */
internal fun canMarkChatRead(lastMessageId: Long?, markedAsUnread: Boolean): Boolean =
    lastMessageId != null || markedAsUnread

/** Отметить чат непрочитанным: счётчик не меняется, Telegram показывает маркер. */
internal fun markChatUnreadRequest(chatId: Long) = TdApi.ToggleChatIsMarkedAsUnread(chatId, true)

/** Снять маркер непрочитанного при открытии/прочтении чата. */
internal fun markChatReadToggleRequest(chatId: Long) = TdApi.ToggleChatIsMarkedAsUnread(chatId, false)

/**
 * Only the fields exposed by the per-chat sheet are read; mute and story settings stay untouched. An inherited preview or
 * mention setting takes the value of the chat's notification [scope], and its use-default flag is kept for saving.
 */
internal fun TdApi.ChatNotificationSettings.toChatNotificationPreferences(scope: TdApi.ScopeNotificationSettings) = ChatNotificationPreferences(
    useDefaultSound = useDefaultSound,
    soundId = soundId,
    showPreview = if (useDefaultShowPreview) scope.showPreview else showPreview,
    useDefaultShowPreview = useDefaultShowPreview,
    disableMentionNotifications = if (useDefaultDisableMentionNotifications) scope.disableMentionNotifications else disableMentionNotifications,
    useDefaultDisableMentionNotifications = useDefaultDisableMentionNotifications
)

/**
 * Writes the fields the sheet owns. A preview or mention setting that is still inherited stays inherited: its explicit
 * value is not written, so changing another setting never turns the inherited one into an override. Mute and story
 * settings are not touched.
 */
internal fun TdApi.ChatNotificationSettings.withChatNotificationPreferences(preferences: ChatNotificationPreferences): TdApi.ChatNotificationSettings = apply {
    useDefaultSound = preferences.useDefaultSound
    soundId = preferences.soundId
    useDefaultShowPreview = preferences.useDefaultShowPreview
    if (!preferences.useDefaultShowPreview) showPreview = preferences.showPreview
    useDefaultDisableMentionNotifications = preferences.useDefaultDisableMentionNotifications
    if (!preferences.useDefaultDisableMentionNotifications) disableMentionNotifications = preferences.disableMentionNotifications
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
