package com.beykus.beygram.data.tdlib

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
    val sharedMihomo: Boolean get() = false
    suspend fun pauseSharedNetwork() {}
    suspend fun resumeSharedNetwork(verified: Boolean) {}
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
    override val sharedMihomo: Boolean get() = login.plannedRoute == TdSecondaryRouteDecision.SharedMihomo
    override suspend fun pauseSharedNetwork() = login.pauseSharedNetwork()
    override suspend fun resumeSharedNetwork(verified: Boolean) = login.resumeSharedNetwork(verified)
}

internal data class TdSecondaryAccountRow(
    val accountId: String,
    val phase: TdAccountAuthPhase?,
    val connectedInThisProcess: Boolean
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
     * Reconciles all shared Mihomo sessions whenever the process listener
     * changes state (or on an explicit foreground refresh). The caller must
     * provide a fresh cryptographic SOCKS ownership/preference check; never
     * substitute a bare open TCP port or a RUNNING flag for verification.
     */
    suspend fun reconcileShared(networkAllowed: suspend (TdSecondaryRoutePreference) -> Boolean) {
        val active = mutex.withLock { running.values.filter { it.handle.sharedMihomo }.toList() }
        for (entry in active) {
            if (entry.handle.authPhase.value == TdAccountAuthPhase.CLOSED) continue
            val allowed = try { networkAllowed(entry.preference) } catch (_: Exception) { false }
            try {
                if (allowed) entry.handle.resumeSharedNetwork(true)
                else entry.handle.pauseSharedNetwork()
            } catch (_: Exception) {
                // Never try direct on proxy failure. Next listener transition or
                // screen resume will retry; keep the session's TDLib route intact.
            }
        }
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
                    phase != TdAccountAuthPhase.CLOSED)
            },
            busy = false
        )
    }


}
