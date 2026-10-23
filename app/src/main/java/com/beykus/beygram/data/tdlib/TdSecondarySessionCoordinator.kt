package com.beykus.beygram.data.tdlib

import com.beykus.beygram.diagnostics.MultiAccountDiagnostics
import com.beykus.beygram.diagnostics.report.Severity
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
    val routePreference: TdSecondaryRoutePreference? = null,
    /** Native TDLib previously confirmed READY for this ID (durable restore policy). */
    val authorized: Boolean = false,
    /** The user explicitly stopped the session; it must not auto-reconnect. */
    val manualStop: Boolean = false
)

internal data class TdSecondaryAccountsSnapshot(
    val accounts: List<TdSecondaryAccountRow> = emptyList(),
    val busy: Boolean = false
)

/** The process already owns the maximum supported number of secondary clients. */
internal class TdSecondarySessionLimitException :
    IllegalStateException("Maximum two additional TDLib sessions on this device")

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
    private val isAuthorized: (String) -> Boolean = { false },
    private val isManuallyStopped: (String) -> Boolean = { false },
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
    private val loggingOutAccounts = mutableSetOf<String>()
    private val _snapshot = MutableStateFlow(TdSecondaryAccountsSnapshot())
    val snapshot: StateFlow<TdSecondaryAccountsSnapshot> = _snapshot.asStateFlow()

    suspend fun refresh() = mutex.withLock { publish() }

    /** Adds only a new storage reservation; the selected route must be chosen separately. */
    suspend fun reserveAccount(): String {
        val operation = MultiAccountDiagnostics.begin("account_slot_create")
        operation.trace("lock_wait")
        return try {
            mutex.withLock {
                operation.trace("registry_reserve")
                val id = registry.prepareSecondaryAccount().second
                publish()
                operation.success(
                    "slot_created",
                    attrs = mapOf("account_count" to _snapshot.value.accounts.size.toString())
                )
                id
            }
        } catch (failure: Throwable) {
            operation.failure("registry_reserve", failure)
            throw failure
        }
    }

    suspend fun connect(
        accountId: String, preference: TdSecondaryRoutePreference, fromRouteSwitch: Boolean = false
    ) {
        val operation = MultiAccountDiagnostics.begin(
            code = "tdlib_connect",
            accountId = accountId,
            attrs = mapOf(
                "preference" to preference.name,
                "source" to if (fromRouteSwitch) "route_switch" else "user"
            )
        )
        operation.trace("lock_wait")
        try {
            mutex.withLock {
                operation.trace("lock_acquired")
                check(accountId !in switchingAccounts || fromRouteSwitch) {
                    "Account route is being changed"
                }
                check(accountId !in loggingOutAccounts) { "Account is logging out" }
                require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID) {
                    "Legacy TDLib is already running"
                }
                require(accountId in registry.loadOrCreate().accountIds) { "Account not registered" }
                if (running[accountId]?.handle?.authPhase?.value == TdAccountAuthPhase.CLOSED) {
                    operation.trace("retire_closed_lease")
                    running[accountId]?.handle?.awaitClosedLease()
                    running.remove(accountId)?.subscription?.cancel()
                }
                check(accountId !in running) { "Secondary account is already active" }
                // Bounded resource budget: at most TWO secondary clients alongside
                // legacy. Never start all reserved slots on a low-memory device.
                if (running.values.count {
                        it.handle.authPhase.value != TdAccountAuthPhase.CLOSED
                    } >= 2
                ) {
                    throw TdSecondarySessionLimitException()
                }
                _snapshot.value = _snapshot.value.copy(busy = true)
                try {
                    operation.trace("native_open_start")
                    val handle = openNative(accountId, preference)
                    operation.trace("native_open_done")
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
                    operation.success(
                        "session_started",
                        attrs = mapOf(
                            "phase" to (handle.authPhase.value.name),
                            "sessions" to running.values.count {
                                it.handle.authPhase.value != TdAccountAuthPhase.CLOSED
                            }.toString()
                        )
                    )
                } finally {
                    publish()
                }
            }
        } catch (cancelled: CancellationException) {
            operation.failure("connect_cancelled", cancelled)
            throw cancelled
        } catch (failure: Throwable) {
            operation.failure("connect", failure)
            throw failure
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
        check(accountId !in loggingOutAccounts) { "Account is logging out" }
        check(handle.authPhase.value == TdAccountAuthPhase.READY) {
            "Secondary Telegram account is not authorized"
        }
        check(!handle.networkSuspended) { "Secondary network route is paused" }
        handle
    }

    /** A confirmed logout is different from Close. TDLib must close and release
     * its lease BEFORE its slot is removed from durable registry metadata. */
    suspend fun logoutSecondary(accountId: String) {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID)
        val operation = MultiAccountDiagnostics.begin("logout", accountId)
        val active = try {
            mutex.withLock {
                operation.trace("lock_wait")
                check(loggingOutAccounts.add(accountId)) { "Logout already running" }
                try {
                    check(accountId !in switchingAccounts)
                    (running[accountId] ?: error("Secondary account must be connected to log out")).also {
                        check(it.handle.authPhase.value == TdAccountAuthPhase.READY)
                    }
                } catch (failure: Throwable) {
                    loggingOutAccounts.remove(accountId)
                    throw failure
                }
            }
        } catch (failure: Throwable) {
            operation.failure("logout_prepare", failure)
            throw failure
        }
        try {
            // Native Close can take tens of seconds. Other accounts' chats,
            // picker and TDLib session updates must remain responsive.
            operation.trace("native_logout_start")
            active.handle.logOutAndAwaitClosed()
            operation.trace("native_logout_closed")
            mutex.withLock {
                check(running[accountId] === active)
                // Durable removal is the commit point. A failed subsequent
                // cache/notification cleanup remains recoverable from tombstone.
                registry.unregisterSecondaryAccount(accountId)
                running.remove(accountId)?.subscription?.cancel()
                publish()
            }
            operation.trace("registry_unregistered")
            onLogoutConfirmed(accountId)
            operation.success("logout_completed")
        } catch (failure: Throwable) {
            operation.failure("logout", failure)
            throw failure
        } finally {
            mutex.withLock { loggingOutAccounts.remove(accountId) }
        }
    }

    /**
     * Discard a slot whose Telegram sign-in never completed. Unlike Logout this
     * must never touch an authorized account: its TDLib database and Telegram
     * authorization are kept until the user explicitly chooses "Выйти и удалить".
     */
    suspend fun discardIncompleteAccount(accountId: String) {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID)
        val operation = MultiAccountDiagnostics.begin("discard_unfinished", accountId)
        check(!isAuthorized(accountId)) {
            "An authorized account must be logged out, not discarded"
        }
        val active = try {
            mutex.withLock {
                operation.trace("lock_wait")
                check(loggingOutAccounts.add(accountId)) { "Account operation already running" }
                try {
                    check(accountId !in switchingAccounts)
                    running[accountId]?.also {
                        check(it.handle.authPhase.value != TdAccountAuthPhase.READY) {
                            "An authorized session cannot be discarded"
                        }
                    }
                } catch (failure: Throwable) {
                    loggingOutAccounts.remove(accountId)
                    throw failure
                }
            }
        } catch (failure: Throwable) {
            operation.failure("discard_prepare", failure)
            throw failure
        }
        try {
            if (active != null) {
                operation.trace("native_close_start")
                active.handle.close()
                if (active.handle.authPhase.value != TdAccountAuthPhase.CLOSED) {
                    withTimeout(45_000) {
                        active.handle.authPhase.first { it == TdAccountAuthPhase.CLOSED }
                    }
                }
                operation.trace("native_closed")
                active.handle.awaitClosedLease()
                operation.trace("lease_retired")
            }
            mutex.withLock {
                check(!isAuthorized(accountId)) {
                    "Refusing to remove an account that became authorized"
                }
                if (active != null) check(running[accountId] === active)
                // Durable removal only after the native lease is safely retired.
                registry.unregisterSecondaryAccount(accountId)
                running.remove(accountId)?.subscription?.cancel()
                publish()
            }
            operation.success("discard_completed")
        } catch (failure: Throwable) {
            operation.failure("discard", failure)
            throw failure
        } finally {
            mutex.withLock { loggingOutAccounts.remove(accountId) }
        }
    }

    /** Explicit route edit: Close the same session without logout, wait for the
     * database lease to retire, then reopen after the new route preflight.
     * Works for READY and unfinished credential-waiting sessions; closing an
     * unfinished session keeps its database and does not log out. */
    suspend fun reconnectWithRoute(
        accountId: String,
        preference: TdSecondaryRoutePreference,
        verifyNewRoute: suspend (TdSecondaryRoutePreference) -> Unit = {}
    ) {
        val operation = MultiAccountDiagnostics.begin(
            "route_change",
            accountId,
            mapOf("preference" to preference.name)
        )
        val current = try {
            mutex.withLock {
                operation.trace("lock_wait")
                check(switchingAccounts.add(accountId)) { "Route transition already active" }
                try {
                    val session = running[accountId]?.handle
                        ?: error("Secondary session is not running")
                    check(session.authPhase.value != TdAccountAuthPhase.CLOSED) {
                        "Secondary session is already closed"
                    }
                    session
                } catch (error: Throwable) {
                    switchingAccounts.remove(accountId)
                    throw error
                }
            }
        } catch (failure: Throwable) {
            operation.failure("route_prepare", failure)
            throw failure
        }
        try {
            // Check proxy ownership BEFORE closing the working native session.
            // No manual-stop marker is written: a failed route transition must
            // retain the previous background restore policy.
            operation.trace("route_preflight")
            verifyNewRoute(preference)
            operation.trace("route_preflight_passed")
            mutex.withLock {
                check(running[accountId]?.handle === current)
                current.close()
                operation.trace("native_close_requested")
                publish()
            }
            withTimeout(45_000) { current.authPhase.first { it == TdAccountAuthPhase.CLOSED } }
            operation.trace("native_closed")
            current.awaitClosedLease()
            operation.trace("lease_retired")
            connect(accountId, preference, fromRouteSwitch = true)
            operation.success("route_applied")
        } catch (failure: Throwable) {
            operation.failure("route_apply", failure)
            throw failure
        } finally {
            mutex.withLock { switchingAccounts.remove(accountId) }
        }
    }

    /** Leaves TDLib authorization and the files on disk unchanged. */
    suspend fun disconnect(accountId: String) {
        val operation = MultiAccountDiagnostics.begin("session_stop", accountId)
        try {
            mutex.withLock {
                val active = running[accountId]
                if (active == null) {
                    operation.milestone("already_closed")
                    return@withLock
                }
                check(accountId !in loggingOutAccounts && accountId !in switchingAccounts)
                // Record user intent BEFORE asynchronous native Close.
                onManualStop(accountId)
                active.handle.close()
                operation.success(
                    "close_requested",
                    attrs = mapOf("phase" to active.handle.authPhase.value.name)
                )
                // Keep strong ownership and the native database lease until CLOSED.
                // The subscription will publish that terminal state; it does not
                // remove the entry while TDLib might still own the database.
                publish()
            }
        } catch (failure: Throwable) {
            operation.failure("session_stop", failure)
            throw failure
        }
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
        val active = mutex.withLock { running.entries.map { it.key to it.value } }
        for ((accountId, entry) in active) {
            if (entry.handle.authPhase.value == TdAccountAuthPhase.CLOSED) continue
            val decision = try { decide(entry.preference) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.PRIMARY_ROUTE_UNKNOWN)
            }
            try {
                if (decision == entry.handle.plannedRoute) {
                    entry.handle.resumeNetwork(true)
                    MultiAccountDiagnostics.milestone(
                        "route_reconciled",
                        "network_resumed",
                        accountId,
                        attrs = mapOf("preference" to entry.preference.name)
                    )
                } else {
                    entry.handle.pauseNetwork()
                    MultiAccountDiagnostics.milestone(
                        "route_reconciled",
                        "network_paused",
                        accountId,
                        severity = Severity.WARNING,
                        attrs = mapOf(
                            "preference" to entry.preference.name,
                            "route_reason" to (decision as? TdSecondaryRouteDecision.Blocked)?.reason?.name
                                .orEmpty()
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // If the network cannot be disabled when policy diverges,
                // fail closed by closing this native client (NOT LogOut).
                // The TDLib database/authorization remain intact.
                if (decision != entry.handle.plannedRoute) entry.handle.close()
                MultiAccountDiagnostics.milestone(
                    "route_reconciled",
                    "network_policy_failed",
                    accountId,
                    severity = Severity.WARNING,
                    attrs = mapOf("preference" to entry.preference.name)
                )
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
                TdSecondaryAccountRow(
                    id, phase, running[id] != null &&
                        phase != TdAccountAuthPhase.CLOSED,
                    networkSuspended = running[id]?.handle?.networkSuspended == true,
                    routePreference = running[id]?.preference,
                    authorized = isAuthorized(id),
                    manualStop = isManuallyStopped(id)
                )
            },
            busy = false
        )
    }


}
