package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    override val plannedRoute: TdSecondaryRouteDecision get() = login.plannedRoute
    override val networkSuspended: Boolean get() = login.routePaused.value
    override suspend fun pauseNetwork() = login.pauseNetwork()
    override suspend fun resumeNetwork(verified: Boolean) = login.resumeNetwork(verified)
}

internal data class TdSecondaryAccountRow(
    val accountId: String,
    val phase: TdAccountAuthPhase?,
    val connectedInThisProcess: Boolean,
    val networkSuspended: Boolean = false
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
    private val openNative: suspend (String, TdSecondaryRoutePreference) -> TdSecondaryAuthSession
) {
    private data class Active(
        val handle: TdSecondaryAuthSession,
        val preference: TdSecondaryRoutePreference,
        val subscription: Job
    )

    private val mutex = Mutex()
    private val running = mutableMapOf<String, Active>()
    private val _snapshot = MutableStateFlow(TdSecondaryAccountsSnapshot())
    val snapshot: StateFlow<TdSecondaryAccountsSnapshot> = _snapshot.asStateFlow()

    suspend fun refresh() = mutex.withLock { publish() }

    /** Adds only a new storage reservation; the selected route must be chosen separately. */
    suspend fun reserveAccount(): String = mutex.withLock {
        val id = registry.prepareSecondaryAccount().second
        publish()
        id
    }

    suspend fun connect(accountId: String, preference: TdSecondaryRoutePreference) = mutex.withLock {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID) { "Legacy TDLib is already running" }
        require(accountId in registry.loadOrCreate().accountIds) { "Account not registered" }
        if (running[accountId]?.handle?.authPhase?.value == TdAccountAuthPhase.CLOSED) {
            running.remove(accountId)?.subscription?.cancel()
        }
        check(accountId !in running) { "Secondary account is already active" }
        // Beta resource budget: legacy TDLib + at most ONE secondary native client.
        // More account slots can be reserved, but do not start uncontrolled
        // background clients before RAM/battery and notification ownership QA.
        check(running.values.none { it.handle.authPhase.value != TdAccountAuthPhase.CLOSED }) {
            "Only one secondary TDLib session can run during beta"
        }
        _snapshot.value = _snapshot.value.copy(busy = true)
        try {
            val handle = openNative(accountId, preference)
            val job = scope.launch {
                handle.authPhase.collect { mutex.withLock { publish() } }
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

    /** Leaves TDLib authorization and the files on disk unchanged. */
    suspend fun disconnect(accountId: String) = mutex.withLock {
        val active = running[accountId] ?: return@withLock
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
                    networkSuspended = running[id]?.handle?.networkSuspended == true)
            },
            busy = false
        )
    }


}
