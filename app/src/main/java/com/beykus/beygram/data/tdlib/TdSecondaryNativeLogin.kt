package com.beykus.beygram.data.tdlib

import android.content.Context
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.data.vpn.LoopbackProxyCredentials
import com.beykus.beygram.domain.model.TelegramRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Explicit secondary-account TDLib *native* login entry point. This is not
 * installed in the application singleton and never starts on boot.
 *
 * Callers must provide the live primary route, then explicitly open a
 * previously registered secondary ID; all TDLib callbacks remain account-bound.
 * The first session continues to be owned by TdLibTelegramRepository.
 */
internal class TdSecondaryNativeLoginLauncher(
    private val owner: TdAccountNativeClientOwner,
    private val preflight: TdSecondaryAccountRoutePreflight,
    private val primaryRoute: suspend () -> TelegramRoute?,
    private val localCredentials: () -> Pair<String, String>,
    private val createNativeClient: (Client.ResultHandler) -> Client = {
        Client.create(it, null, null)
    }
) {
    companion object {
        fun forApplication(
            context: Context,
            primaryRoute: suspend () -> TelegramRoute?
        ): TdSecondaryNativeLoginLauncher {
            val app = context.applicationContext
            return TdSecondaryNativeLoginLauncher(
                TdAccountNativeClientOwner(app),
                TdSecondaryAccountRoutePreflight.forApplication(app),
                primaryRoute,
                {
                    val credentials = LoopbackProxyCredentials.get(app)
                    credentials.username to credentials.password
                }
            )
        }
    }

    /**
     * Open is a suspend operation, never invoked by Application.onCreate.
     * The secondary database remains untouched if the route preflight blocks.
     * Any failure AFTER the native client exists sends TDLib Close (not LogOut)
     * and lets AuthorizationStateClosed retire its DB lease.
     */
    suspend fun openRegisteredSecondary(
        accountId: String,
        preference: TdSecondaryRoutePreference = TdSecondaryRoutePreference.FOLLOW_PRIMARY
    ): TdSecondaryNativeLogin {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID) {
            "The legacy account is already owned by TdLibTelegramRepository"
        }
        val planned = preflight.decide(primaryRoute(), preference)
        check(planned !is TdSecondaryRouteDecision.Blocked) {
            "Secondary account route is unavailable: ${(planned as TdSecondaryRouteDecision.Blocked).reason}"
        }

        val phases = MutableStateFlow(TdAccountAuthPhase.STARTING)
        val chatStore = TdSecondaryChatStore(accountId)
        val fullRepositoryObservers = CopyOnWriteArrayList<(TdApi.Object) -> Unit>()
        val startupReplay = TdSecondaryStartupReplay()
        val overflowCloseRequested = java.util.concurrent.atomic.AtomicBoolean()
        val nativeToClose = java.util.concurrent.atomic.AtomicReference<TdSecondaryNativeLogin?>()
        val waitParameters = CompletableDeferred<Unit>()
        val nativeLeaseClosed = CompletableDeferred<Unit>()
        val bound = owner.create(accountId,
            onClosed = { nativeLeaseClosed.complete(Unit) },
            onUpdate = { event ->
                // Never dispatch secondary updates through the legacy chat
                // repository, global notification feed or shared media cache.
                // Legacy secondary-preview cache is needed only until the
                // full account repository attaches. After that, double-writing
                // every chat and message would waste RAM (especially on M21).
                if (fullRepositoryObservers.isEmpty()) chatStore.receive(event)
                // The same native client can feed the full TelegramRepository;
                // never create a second Client for these database directories.
                synchronized(fullRepositoryObservers) {
                    startupReplay.record(event.update, fullRepositoryObservers.isNotEmpty())
                    if (startupReplay.isOverflowed() && overflowCloseRequested.compareAndSet(false, true)) {
                        recordAppEvent(EventKind.NOTIFICATION,
                            "secondary_startup_overflow",
                            "Снимок вторичного аккаунта переполнен; перезапуск без выхода",
                            Severity.WARNING, mapOf("account" to accountId))
                        nativeToClose.get()?.requestCloseWithoutLogout()
                    }
                    fullRepositoryObservers.forEach { observer ->
                        runCatching { observer(event.update) }.onFailure { error ->
                            recordAppEvent(
                                EventKind.NOTIFICATION,
                                "secondary_tdlib_observer_failure",
                                "Обновление вторичного аккаунта не обработано",
                                Severity.WARNING,
                                mapOf("account" to accountId, "cause" to error.javaClass.simpleName)
                            )
                        }
                    }
                }
                if (event.update is TdApi.UpdateAuthorizationState) {
                    when (event.update.authorizationState) {
                        is TdApi.AuthorizationStateWaitTdlibParameters -> {
                            phases.value = TdAccountAuthPhase.WAIT_PARAMETERS
                            waitParameters.complete(Unit)
                        }
                        is TdApi.AuthorizationStateWaitPhoneNumber -> phases.value = TdAccountAuthPhase.WAIT_PHONE
                        is TdApi.AuthorizationStateWaitCode -> phases.value = TdAccountAuthPhase.WAIT_CODE
                        is TdApi.AuthorizationStateWaitPassword -> phases.value = TdAccountAuthPhase.WAIT_PASSWORD
                        is TdApi.AuthorizationStateReady -> phases.value = TdAccountAuthPhase.READY
                        is TdApi.AuthorizationStateLoggingOut -> {
                            phases.value = TdAccountAuthPhase.LOGGING_OUT
                            synchronized(fullRepositoryObservers) { startupReplay.clear() }
                        }
                        is TdApi.AuthorizationStateClosed -> {
                            phases.value = TdAccountAuthPhase.CLOSED
                            chatStore.clear()
                            synchronized(fullRepositoryObservers) {
                                fullRepositoryObservers.clear()
                                startupReplay.clear()
                            }
                            waitParameters.cancel()
                        }
                        else -> phases.value = TdAccountAuthPhase.OTHER
                    }
                }
            },
            clientFactory = { config, handler ->
                val client = createNativeClient(handler)
                TdSecondaryNativeLogin(accountId, config, client, phases, planned, chatStore,
                    fullRepositoryObservers, startupReplay, nativeLeaseClosed)
                    .also { nativeToClose.set(it) }
            }
        )
        try {
            val transport = TdSecondaryNativeTransport(
                request = { function -> bound.request(function) },
                awaitParameters = { withTimeout(20_000) { waitParameters.await() } },
                revalidate = { preflight.decide(primaryRoute(), preference) },
                proxyEndpoint = {
                    val (username, password) = localCredentials()
                    TdSecondaryNativeTransport.localMihomoProxy(username, password)
                }
            )
            transport.initialize(bound.config, planned)
            check(bound.authPhase.value != TdAccountAuthPhase.CLOSED) { "TDLib client closed during startup" }
            return bound
        } catch (failure: Throwable) {
            bound.requestCloseWithoutLogout()
            throw failure
        }
    }
}

/** Real TDLib client for one *secondary* account; no legacy/UI cache is shared. */
internal class TdSecondaryNativeLogin internal constructor(
    val accountId: String,
    internal val config: TdLibConfig,
    internal val client: Client,
    private val mutablePhase: MutableStateFlow<TdAccountAuthPhase>,
    internal val plannedRoute: TdSecondaryRouteDecision,
    val chatStore: TdSecondaryChatStore,
    private val fullRepositoryObservers: CopyOnWriteArrayList<(TdApi.Object) -> Unit>,
    private val startupReplay: TdSecondaryStartupReplay,
    private val nativeLeaseClosed: CompletableDeferred<Unit>
) {
    private val fullRepositoryLock = Any()
    private var fullRepository: TdLibTelegramRepository? = null

    /** One full repository per native client. Reuses the existing leased DB. */
    internal fun unifiedRepository(context: Context): TdLibTelegramRepository = synchronized(fullRepositoryLock) {
        check(authPhase.value == TdAccountAuthPhase.READY && !routePaused.value) {
            "Secondary TDLib client is not ready for UI"
        }
        fullRepository ?: runCatching {
            TdLibTelegramRepository.forExistingSecondary(context, this)
        }.onFailure { error ->
            // READY without a valid full repository must not get stuck forever:
            // preserve Telegram authorization and trigger a fresh native owner.
            recordAppEvent(EventKind.NOTIFICATION,
                "secondary_repository_attach_failed",
                "Не удалось подключить вторичный репозиторий",
                Severity.WARNING,
                mapOf("account" to accountId, "cause" to error.javaClass.simpleName))
            requestCloseWithoutLogout()
        }.getOrThrow().also { fullRepository = it }
    }

    internal fun observeFullUpdates(observer: (TdApi.Object) -> Unit) {
        synchronized(fullRepositoryObservers) {
            check(authPhase.value != TdAccountAuthPhase.CLOSED)
            // Replay before subscribing to live events under the same dispatch lock.
            startupReplay.replay(observer)
            fullRepositoryObservers.add(observer)
        }
    }

    val authPhase: StateFlow<TdAccountAuthPhase> = mutablePhase.asStateFlow()

    /** No legacy chat/cache reuse. These methods are only for this native client. */
    suspend fun loadSecondaryChats() {
        check(authPhase.value == TdAccountAuthPhase.READY) { "Secondary account is not authorized" }
        val response = request(TdApi.LoadChats(TdApi.ChatListMain(), 100))
        if (response is TdApi.Error && response.code != 404) {
            throw IllegalStateException("Unable to load secondary chat list (code ${response.code})")
        }
    }

    /**
     * TDLib requires explicit OpenChat/CloseChat for complete live updates
     * in channels and supergroups. The UI owns pairing across navigation.
     */
    suspend fun openSecondaryHistory(chat: TdAccountChatKey) {
        require(chat.accountId == accountId && chat.chatId != 0L) { "Foreign or invalid chat key" }
        check(authPhase.value == TdAccountAuthPhase.READY) { "Secondary account is not authorized" }
        val opened = request(TdApi.OpenChat(chat.chatId))
        if (opened is TdApi.Error) throw IllegalStateException(
            "Unable to open secondary chat (code ${opened.code})"
        )
        loadSecondaryHistory(chat)
    }

    suspend fun closeSecondaryHistory(chat: TdAccountChatKey) {
        require(chat.accountId == accountId) { "Foreign account chat close blocked" }
        try {
            if (authPhase.value == TdAccountAuthPhase.CLOSED) return
            val result = request(TdApi.CloseChat(chat.chatId))
            if (result is TdApi.Error) throw IllegalStateException(
                "Unable to close secondary chat (code ${result.code})"
            )
        } finally {
            chatStore.discardHistory(chat)
        }
    }

    suspend fun loadSecondaryHistory(chat: TdAccountChatKey) {
        require(chat.accountId == accountId) { "Foreign account chat access blocked" }
        check(authPhase.value == TdAccountAuthPhase.READY) { "Secondary account is not authorized" }
        val response = request(TdApi.GetChatHistory(chat.chatId, 0L, 0, 50, false))
        when (response) {
            is TdApi.Messages -> chatStore.replaceHistory(
                chat, chatStore.convertHistory(chat, response.messages.orEmpty())
            )
            is TdApi.Error -> throw IllegalStateException(
                "Unable to load secondary history (code ${response.code})"
            )
            else -> error("Unexpected secondary history result")
        }
    }
    /**
     * Account-keyed, real TDLib messaging: no legacy repository or active-UI
     * numeric chat fallback. A caller must resolve THIS native session first.
     */
    suspend fun sendSecondaryText(chat: TdAccountChatKey, text: String) {
        require(chat.accountId == accountId && chat.chatId != 0L) {
            "Cannot send Telegram message to another account"
        }
        check(authPhase.value == TdAccountAuthPhase.READY && !routePaused.value) {
            "Secondary account is not connected and ready"
        }
        val bounded = text
        require(bounded.isNotBlank() && bounded.codePointCount(0, bounded.length) <= 4096) {
            "Invalid Telegram message length"
        }
        val content = TdApi.InputMessageText().apply {
            this.text = TdApi.FormattedText(bounded, emptyArray())
            clearDraft = true
        }
        val sendFunction = TdApi.SendMessage().apply {
            chatId = chat.chatId
            inputMessageContent = content
        }
        val response = request(sendFunction)
        when (response) {
            is TdApi.Message -> {
                check(response.chatId == chat.chatId) { "TDLib returned a different chat" }
                chatStore.appendSentMessage(response)
            }
            is TdApi.Error -> throw IllegalStateException(
                "Secondary message rejected by Telegram (code ${response.code})"
            )
            else -> error("Unexpected TDLib secondary message response")
        }
    }

    suspend fun readSecondaryChat(chat: TdAccountChatKey, messageId: Long) {
        require(chat.accountId == accountId && chat.chatId != 0L && messageId > 0L) {
            "Foreign or invalid chat read target"
        }
        check(authPhase.value == TdAccountAuthPhase.READY && !routePaused.value)
        val response = request(TdApi.ViewMessages(
            chat.chatId, longArrayOf(messageId), TdApi.MessageSourceChatHistory(), true
        ))
        if (response is TdApi.Error) throw IllegalStateException(
            "Unable to mark secondary message as read (code ${response.code})"
        )
    }

    private val routeMutex = Mutex()
    private val _routePaused = MutableStateFlow(false)
    val routePaused: StateFlow<Boolean> = _routePaused.asStateFlow()

    /**
     * A FOLLOW_PRIMARY account may have originally started DIRECT. If the
     * primary route changes later, it must go offline too; pausing only
     * shared-Mihomo sessions would leak traffic onto an outdated route.
     */
    /** Forward Android connectivity transitions without overriding a suspended route policy. */
    internal suspend fun onConnectivityChanged(type: TdApi.NetworkType) = routeMutex.withLock {
        if (authPhase.value == TdAccountAuthPhase.CLOSED) return@withLock
        val applied = if (_routePaused.value) TdApi.NetworkTypeNone() else type
        val response = request(TdApi.SetNetworkType(applied))
        if (response is TdApi.Error) error("TDLib secondary network update failed (code ${response.code})")
    }

    suspend fun pauseNetwork() = routeMutex.withLock {
        if (authPhase.value == TdAccountAuthPhase.CLOSED || _routePaused.value) return@withLock
        val result = request(TdApi.SetNetworkType(TdApi.NetworkTypeNone()))
        if (result is TdApi.Error) error("TDLib failed to pause secondary network")
        _routePaused.value = true
    }

    /** Re-enable only if the newly verified policy STILL matches the original. */
    suspend fun resumeNetwork(verified: Boolean) = routeMutex.withLock {
        if (authPhase.value == TdAccountAuthPhase.CLOSED ||
            !_routePaused.value || !verified) return@withLock
        val result = request(TdApi.SetNetworkType(TdApi.NetworkTypeOther()))
        if (result is TdApi.Error) error("TDLib failed to resume secondary network")
        _routePaused.value = false
    }

    suspend fun submitPhoneNumber(phone: String) {
        check(authPhase.value == TdAccountAuthPhase.WAIT_PHONE) { "TDLib isn't waiting for a phone number" }
        require(phone.trim().isNotEmpty()) { "Phone number is empty" }
        expectOk(TdApi.SetAuthenticationPhoneNumber(phone.trim(), null))
    }

    suspend fun submitCode(code: String) {
        check(authPhase.value == TdAccountAuthPhase.WAIT_CODE) { "TDLib isn't waiting for a code" }
        require(code.isNotBlank()) { "Code is empty" }
        expectOk(TdApi.CheckAuthenticationCode(code.trim()))
    }

    suspend fun submitPassword(password: String) {
        check(authPhase.value == TdAccountAuthPhase.WAIT_PASSWORD) { "TDLib isn't waiting for a password" }
        require(password.isNotEmpty()) { "Password is empty" }
        expectOk(TdApi.CheckAuthenticationPassword(password))
    }

    suspend fun resendCode() {
        check(authPhase.value == TdAccountAuthPhase.WAIT_CODE) { "TDLib isn't waiting for a code" }
        expectOk(TdApi.ResendAuthenticationCode(null))
    }

    /** Signals AFTER native owner has finalized the TDLib database lease. */
    suspend fun awaitNativeClosed() {
        withTimeout(45_000) { nativeLeaseClosed.await() }
    }

    /** LogOut invalidates this secondary Telegram authorization. Database ownership
     * remains with native TDLib until the CLOSED event releases its lease. */
    suspend fun logOutAndAwaitClosed() {
        check(authPhase.value == TdAccountAuthPhase.READY) {
            "Account must be authorized before logout"
        }
        expectOk(TdApi.LogOut())
        // CLOSED is published before the owner's finally{} releases its lease.
        awaitNativeClosed()
    }

    /** Close keeps the TDLib database and user login. Do not call LogOut here. */
    fun close() = requestCloseWithoutLogout()

    internal fun requestCloseWithoutLogout() {
        if (authPhase.value != TdAccountAuthPhase.CLOSED) {
            // Close is asynchronous; the owner releases its lease on
            // AuthorizationStateClosed, never when this call is dispatched.
            runCatching { client.send(TdApi.Close(), Client.ResultHandler { }) }
        }
    }

    internal suspend fun request(function: TdApi.Function<*>): TdApi.Object =
        withTimeout(20_000) {
            suspendCancellableCoroutine { continuation ->
                client.send(function, Client.ResultHandler { response ->
                    if (continuation.isActive) continuation.resume(response)
                })
            }
        }

    private suspend fun expectOk(function: TdApi.Function<*>) {
        when (val response = request(function)) {
            is TdApi.Ok -> Unit
            is TdApi.Error -> throw IllegalStateException(
                "TDLib account authorization failed (code ${response.code})"
            )
            else -> error("Unexpected TDLib authorization response")
        }
    }
}

/**
 * One-shot state sent before a restored session's complete repository attaches.
 * Never buffer transient messages, which are loaded through the normal TDLib APIs.
 */
internal object TdSecondaryStartupState {
    fun key(update: TdApi.Object): String? = when (update) {
        is TdApi.UpdateChatFolders -> "folders"
        is TdApi.UpdateActiveEmojiReactions -> "reactions"
        is TdApi.UpdateConnectionState -> "connection"
        is TdApi.UpdateActiveNotifications -> "active_notifications"
        is TdApi.UpdateOption -> "option:${update.name}"
        else -> null
    }
}

/**
 * A short-lived replay between native READY and the shared repository observer.
 * All methods run under the native fullRepositoryObservers monitor.
 * Replaying the snapshot alone loses notification deltas emitted in that gap.
 */
internal class TdSecondaryStartupReplay {
    private val snapshots = LinkedHashMap<String, TdApi.Object>()
    private val deltas = ArrayList<TdApi.Object>()
    private var overflowed = false
    fun isOverflowed(): Boolean = overflowed

    fun record(update: TdApi.Object, hasObservers: Boolean) {
        TdSecondaryStartupState.key(update)?.let { snapshots[it] = update }
        if (update is TdApi.UpdateActiveNotifications) {
            // The authoritative snapshot already includes earlier notification
            // deltas but must not discard unrelated chat and user updates.
            deltas.removeAll {
                it is TdApi.UpdateNotificationGroup ||
                    it is TdApi.UpdateNotification ||
                    it is TdApi.UpdateHavePendingNotifications
            }
        } else if (!hasObservers &&
            update !is TdApi.UpdateAuthorizationState &&
            update !is TdApi.UpdateChatAction &&
            update !is TdApi.UpdateUserStatus &&
            TdSecondaryStartupState.key(update) == null) {
            // TDLib may send these only once before the full repository
            // attaches: new chats, user/group metadata, positions, read counts,
            // new messages and per-chat notification settings. Buffer ALL
            // such updates rather than a fragile notification-only allowlist.
            if (deltas.size < MAX_STARTUP_DELTAS) deltas.add(update)
            else overflowed = true
        }
    }

    fun replay(observer: (TdApi.Object) -> Unit) {
        check(!overflowed) {
            "Secondary TDLib startup replay exceeded resource budget; refusing incomplete repository"
        }
        snapshots.values.forEach(observer)
        deltas.forEach(observer)
        deltas.clear()
    }

    fun clear() {
        snapshots.clear()
        deltas.clear()
        overflowed = false
    }

    private companion object {
        /** Fail closed rather than retain unbounded native updates on low-RAM phones. */
        const val MAX_STARTUP_DELTAS = 16_384
    }
}
