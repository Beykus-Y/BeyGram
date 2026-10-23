package com.beykus.beygram.data.tdlib

import android.content.Context
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
        val waitParameters = CompletableDeferred<Unit>()
        val bound = owner.create(accountId,
            onUpdate = { event ->
                // Never dispatch secondary updates through the legacy chat
                // repository, global notification feed or shared media cache.
                chatStore.receive(event)
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
                        is TdApi.AuthorizationStateLoggingOut -> phases.value = TdAccountAuthPhase.LOGGING_OUT
                        is TdApi.AuthorizationStateClosed -> {
                            phases.value = TdAccountAuthPhase.CLOSED
                            chatStore.clear()
                            waitParameters.cancel()
                        }
                        else -> phases.value = TdAccountAuthPhase.OTHER
                    }
                }
            },
            clientFactory = { config, handler ->
                val client = createNativeClient(handler)
                TdSecondaryNativeLogin(accountId, config, client, phases, planned, chatStore)
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
    private val client: Client,
    private val mutablePhase: MutableStateFlow<TdAccountAuthPhase>,
    internal val plannedRoute: TdSecondaryRouteDecision,
    val chatStore: TdSecondaryChatStore
) {
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
    private val routeMutex = Mutex()
    private val _routePaused = MutableStateFlow(false)
    val routePaused: StateFlow<Boolean> = _routePaused.asStateFlow()

    /**
     * A FOLLOW_PRIMARY account may have originally started DIRECT. If the
     * primary route changes later, it must go offline too; pausing only
     * shared-Mihomo sessions would leak traffic onto an outdated route.
     */
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
