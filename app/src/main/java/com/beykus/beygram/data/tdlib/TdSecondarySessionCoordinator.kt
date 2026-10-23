package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * This is an account *authorization* coordinator, NOT a chat repository or
 * account switcher. The current UI continues to read from the legacy repo.
 */
internal interface TdSecondaryAuthSession {
    val authPhase: StateFlow<TdAccountAuthPhase>
    suspend fun submitPhoneNumber(phone: String)
    suspend fun submitCode(code: String)
    suspend fun submitPassword(password: String)
    suspend fun resendCode()
    fun close()
    suspend fun logOutAndAwaitClosed() { error("Native logout is unavailable") }
    suspend fun awaitClosedLease() {}

    /**
     * Only the native session that owns the database lease may provide its
     * full TelegramRepository. Test fakes can deliberately return null.
     */
    fun fullRepository(context: android.content.Context): com.beykus.beygram.domain.repository.TelegramRepository? = null

    /** Read-only secondary chats. Null for a test/fake auth session. */
    val chatStore: TdSecondaryChatStore? get() = null
    suspend fun loadChats() { error("Secondary chat store unavailable") }
    suspend fun loadHistory(key: TdAccountChatKey) { error("Secondary chat store unavailable") }
    suspend fun closeHistory(key: TdAccountChatKey) { error("Secondary chat store unavailable") }
    suspend fun sendText(key: TdAccountChatKey, text: String) {
        error("Secondary chat messaging unavailable")
    }
    suspend fun markRead(key: TdAccountChatKey, lastMessageId: Long) {
        error("Secondary chat read unavailable")
    }

    val plannedRoute: TdSecondaryRouteDecision get() = TdSecondaryRouteDecision.Direct
    val networkSuspended: Boolean get() = false
    suspend fun pauseNetwork() {}
    suspend fun resumeNetwork(verified: Boolean) {}
}

internal class TdNativeSecondaryAuthSession(
    private val login: TdSecondaryNativeLogin
) : TdSecondaryAuthSession {
    override val authPhase: StateFlow<TdAccountAuthPhase> = login.authPhase
    override suspend fun submitPhoneNumber(phone: String) = login.submitPhoneNumber(phone)
    override suspend fun submitCode(code: String) = login.submitCode(code)
    override suspend fun submitPassword(password: String) = login.submitPassword(password)
    override suspend fun resendCode() = login.resendCode()
    override fun close() = login.close()
    override suspend fun logOutAndAwaitClosed() = login.logOutAndAwaitClosed()
    override suspend fun awaitClosedLease() = login.awaitNativeClosed()
    override fun fullRepository(context: android.content.Context): com.beykus.beygram.domain.repository.TelegramRepository =
        login.unifiedRepository(context)
    override val chatStore: TdSecondaryChatStore get() = login.chatStore
    override suspend fun loadChats() = login.loadSecondaryChats()
    override suspend fun loadHistory(key: TdAccountChatKey) = login.openSecondaryHistory(key)
    override suspend fun closeHistory(key: TdAccountChatKey) = login.closeSecondaryHistory(key)
    override suspend fun sendText(key: TdAccountChatKey, text: String) =
        login.sendSecondaryText(key, text)
    override suspend fun markRead(key: TdAccountChatKey, lastMessageId: Long) =
        login.readSecondaryChat(key, lastMessageId)
    override val plannedRoute: TdSecondaryRouteDecision get() = login.plannedRoute
    override val networkSuspended: Boolean get() = login.routePaused.value
    override suspend fun pauseNetwork() = login.pauseNetwork()
    override suspend fun resumeNetwork(verified: Boolean) = login.resumeNetwork(verified)
}

internal data class TdSecondaryAccountRow(
    val accountId: String,
    val phase: TdAccountAuthPhase?,
    val connectedInThisProcess: Boolean,
    val networkSuspended: Boolean = false,
    val routePreference: TdSecondaryRoutePreference? = null
)

internal data class TdSecondaryAccountsSnapshot(
    val accounts: List<TdSecondaryAccountRow> = emptyList(),
    val busy: Boolean = false
)

/**
 * Owns strong references to secondary native clients for the process lifetime.
 * Without this owner, navigation from an add-account screen loses the handle
 * to the TDLib session. No login or network access is started by construction.
 *
 * The registry stores only opaque IDs, not phone numbers, codes or passwords.
 * Existing sessions are never closed when switching UI screens. Stop closes
 * only the chosen secondary client (TDLib Close, never LogOut).
 */
internal class TdSecondarySessionCoordinator(
    private val registry: TdAccountRegistry,
    private val scope: CoroutineScope,
    private val onAuthorized: (String, TdSecondaryRoutePreference) -> Unit = { _, _ -> },
    private val onManualStop: (String) -> Unit = {},
    private val onLogoutConfirmed: (String) -> Unit = {},
    private val openNative: suspend (String, TdSecondaryRoutePreference) -> TdSecondaryAuthSession
) {
    private data class Active(
        val handle: TdSecondaryAuthSession,
        val preference: TdSecondaryRoutePreference,
        val subscription: Job
    )

    private val mutex = Mutex()
    private val running = mutableMapOf<String, Active>()
    /** Prevent background restore and user connect from racing route transitions. */
    private val switchingAccounts = mutableSetOf<String>()
    private val _snapshot = MutableStateFlow(TdSecondaryAccountsSnapshot())
    val snapshot: StateFlow<TdSecondaryAccountsSnapshot> = _snapshot.asStateFlow()

    suspend fun refresh() = mutex.withLock { publish() }

    /** Adds only a new storage reservation; the selected route must be chosen separately. */
    suspend fun reserveAccount(): String = mutex.withLock {
        val id = registry.prepareSecondaryAccount().second
        publish()
        id
    }

    suspend fun connect(
        accountId: String, preference: TdSecondaryRoutePreference, fromRouteSwitch: Boolean = false
    ) = mutex.withLock {
        check(accountId !in switchingAccounts || fromRouteSwitch) {
            "Account route is being changed"
        }
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID) { "Legacy TDLib is already running" }
        require(accountId in registry.loadOrCreate().accountIds) { "Account not registered" }
        if (running[accountId]?.handle?.authPhase?.value == TdAccountAuthPhase.CLOSED) {
            running[accountId]?.handle?.awaitClosedLease()
            running.remove(accountId)?.subscription?.cancel()
        }
        check(accountId !in running) { "Secondary account is already active" }
        // Bounded resource budget: at most TWO secondary clients alongside
        // legacy. Never start all reserved slots on a low-memory device.
        check(running.values.count { it.handle.authPhase.value != TdAccountAuthPhase.CLOSED } < 2) {
            "Maximum two additional TDLib sessions on this device"
        }
        _snapshot.value = _snapshot.value.copy(busy = true)
        try {
            val handle = openNative(accountId, preference)
            val job = scope.launch {
                handle.authPhase.collect { phase ->
                    if (phase == TdAccountAuthPhase.READY) {
                        // Persist eligibility only after the native client is READY.
                        // Failure to write restore metadata never kills the client.
                        runCatching { onAuthorized(accountId, preference) }
                    }
                    mutex.withLock { publish() }
                }
            }
            running[accountId] = Active(handle, preference, job)
        } finally {
            publish()
        }
    }

    suspend fun submitPhone(accountId: String, phone: String) =
        session(accountId).submitPhoneNumber(phone)

    suspend fun submitCode(accountId: String, code: String) =
        session(accountId).submitCode(code)

    suspend fun submitPassword(accountId: String, password: String) =
        session(accountId).submitPassword(password)

    suspend fun resendCode(accountId: String) =
        session(accountId).resendCode()

    /**
     * The standard UI receives the SAME complete TelegramRepository contract
     * regardless of which native session owns the selected account.
     * The original legacy singleton is never touched by this lookup.
     */
    suspend fun fullRepository(
        accountId: String,
        context: android.content.Context
    ): com.beykus.beygram.domain.repository.TelegramRepository {
        val session = chatSession(accountId)
        return session.fullRepository(context)
            ?: error("Native account cannot provide the complete Telegram repository")
    }

    /** Return the exact account-bound session, never an active-UI-account proxy. */
    suspend fun chatSession(accountId: String): TdSecondaryAuthSession = mutex.withLock {
        val handle = running[accountId]?.handle ?: error("Secondary TDLib session is not running")
        check(handle.authPhase.value == TdAccountAuthPhase.READY) {
            "Secondary Telegram account is not authorized"
        }
        check(!handle.networkSuspended) { "Secondary network route is paused" }
        check(handle.chatStore?.accountId == accountId) { "Secondary chat store identity mismatch" }
        handle
    }

    /** A confirmed logout is different from Close. TDLib must close and release
     * its lease BEFORE its slot is removed from durable registry metadata. */
    suspend fun logoutSecondary(accountId: String) = mutex.withLock {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID)
        val active = running[accountId] ?: error("Secondary account must be connected to log out")
        check(active.handle.authPhase.value == TdAccountAuthPhase.READY)
        active.handle.logOutAndAwaitClosed()
        // Never allow the old credentials to be restored from disk after logout.
        onLogoutConfirmed(accountId)
        running.remove(accountId)?.subscription?.cancel()
        registry.unregisterSecondaryAccount(accountId)
        publish()
    }

    /** Explicit route edit: Close the same session without logout, wait for the
     * database lease to retire, then reopen after the new route preflight. */
    suspend fun reconnectWithRoute(
        accountId: String,
        preference: TdSecondaryRoutePreference,
        verifyNewRoute: suspend (TdSecondaryRoutePreference) -> Unit = {}
    ) {
        val current = mutex.withLock {
            check(switchingAccounts.add(accountId)) { "Route transition already active" }
            try {
                val session = running[accountId]?.handle
                    ?: error("Secondary session is not running")
                check(session.authPhase.value == TdAccountAuthPhase.READY)
                session
            } catch (error: Throwable) {
                switchingAccounts.remove(accountId)
                throw error
            }
        }
        try {
            // Check proxy ownership BEFORE closing the working native session.
            // No manual-stop marker is written: a failed route transition must
            // retain the previous background restore policy.
            verifyNewRoute(preference)
            mutex.withLock {
                check(running[accountId]?.handle === current)
                current.close()
                publish()
            }
            withTimeout(45_000) { current.authPhase.first { it == TdAccountAuthPhase.CLOSED } }
            current.awaitClosedLease()
            connect(accountId, preference, fromRouteSwitch = true)
        } finally {
            mutex.withLock { switchingAccounts.remove(accountId) }
        }
    }

    /** Leaves TDLib authorization and the files on disk unchanged. */
    suspend fun disconnect(accountId: String) = mutex.withLock {
        val active = running[accountId] ?: return@withLock
        // Record user intent BEFORE asynchronous native Close.
        onManualStop(accountId)
        active.handle.close()
        // Keep strong ownership and the native database lease until CLOSED.
        // The subscription will publish that terminal state; it does not
        // remove the entry while TDLib might still own the database.
        publish()
    }

    /**
     * Reconcile EVERY secondary session against the route chosen at startup,
     * not just shared-Mihomo sessions. A FOLLOW_PRIMARY session originally
     * started DIRECT must be taken offline if the primary later enables Mihomo.
     *
     * A mismatched route is never silently adopted. The user must explicitly
     * disconnect/reconnect to choose a different route configuration.
     */
    suspend fun reconcileRoutes(decide: suspend (TdSecondaryRoutePreference) -> TdSecondaryRouteDecision) {
        val active = mutex.withLock { running.values.toList() }
        for (entry in active) {
            if (entry.handle.authPhase.value == TdAccountAuthPhase.CLOSED) continue
            val decision = try { decide(entry.preference) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.PRIMARY_ROUTE_UNKNOWN)
            }
            try {
                if (decision == entry.handle.plannedRoute) entry.handle.resumeNetwork(true)
                else entry.handle.pauseNetwork()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // If the network cannot be disabled when policy diverges,
                // fail closed by closing this native client (NOT LogOut).
                // The TDLib database/authorization remain intact.
                if (decision != entry.handle.plannedRoute) entry.handle.close()
            }
        }
        mutex.withLock { publish() }
    }

    /** Do not return a handle for an account which has never been opened. */
    private suspend fun session(accountId: String): TdSecondaryAuthSession =
        mutex.withLock { running[accountId]?.handle ?: error("Secondary TDLib session is not running") }

    private fun publish() {
        val ids = registry.loadOrCreate().accountIds.filterNot {
            it == TdAccountStorageLayout.LEGACY_ACCOUNT_ID
        }
        _snapshot.value = TdSecondaryAccountsSnapshot(
            accounts = ids.map { id ->
                val phase = running[id]?.handle?.authPhase?.value
                TdSecondaryAccountRow(id, phase, running[id] != null &&
                    phase != TdAccountAuthPhase.CLOSED,
                    networkSuspended = running[id]?.handle?.networkSuspended == true,
                    routePreference = running[id]?.preference)
            },
            busy = false
        )
    }


}
