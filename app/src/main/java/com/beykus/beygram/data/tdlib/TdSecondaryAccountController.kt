package com.beykus.beygram.data.tdlib

import android.content.Context
import com.beykus.beygram.BeyGramApplication
import com.beykus.beygram.data.vpn.MihomoProxyService
import com.beykus.beygram.diagnostics.MultiAccountDiagnostics
import com.beykus.beygram.domain.model.TelegramRoute
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.notifications.NotificationRuntime
import com.beykus.beygram.notifications.NotificationPreferencesStore
import com.beykus.beygram.notifications.notificationPreferencesName
import com.beykus.beygram.data.archive.archivePreferencesName
import com.beykus.beygram.data.settings.chatOverridePreferencesName
import com.beykus.beygram.data.settings.translationPreferencesName
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * User-actionable failure from selecting or restoring an account. The shared UI
 * maps these categories to messages and never displays a raw TDLib exception.
 */
internal sealed class TdAccountSwitchFailure(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause) {
    class RouteUnavailable(val reason: TdSecondaryRouteBlock) :
        TdAccountSwitchFailure("Secondary connection route is unavailable: $reason")
    class RoutePaused : TdAccountSwitchFailure("Secondary connection is paused by route policy")
    class SessionLimit : TdAccountSwitchFailure("Two secondary sessions already run")
    class Timeout : TdAccountSwitchFailure("Telegram did not respond in time")
    class NotAuthorized : TdAccountSwitchFailure("The account has not completed Telegram sign-in")
    class Disconnected : TdAccountSwitchFailure("The TDLib session is not running")
    class AccountLimit : TdAccountSwitchFailure("The account registry has no free slot")
}

private val AUTH_WAITING_PHASES = setOf(
    TdAccountAuthPhase.WAIT_PHONE,
    TdAccountAuthPhase.WAIT_CODE,
    TdAccountAuthPhase.WAIT_PASSWORD
)

/**
 * Process-owned and intentionally lazy. Opening Settings does NOT open a
 * second TDLib client; only an explicit user action may do so.
 *
 * Do not put this into an Activity-scoped remember/state holder: losing the
 * handle on screen navigation would orphan a live TDLib client and its lease.
 */
internal class TdSecondaryAccountController private constructor(
    context: Context,
    initialPrimaryRoute: suspend () -> TelegramRoute?,
    backgroundOnly: Boolean
) {
    @Volatile private var primaryRoute: suspend () -> TelegramRoute? = initialPrimaryRoute
    @Volatile private var backgroundRestoreOnly: Boolean = backgroundOnly

    private fun allowBackgroundRestore(accountId: String): Boolean {
        val prefs = NotificationRuntime.get(app).preferencesForAccount(accountId)
        return prefs.authorized && prefs.state.value.enabled && prefs.state.value.background
    }

    /** An Activity can safely upgrade the startup policy and its primary route. */
    private fun updateProvider(provider: suspend () -> TelegramRoute?, backgroundOnly: Boolean) {
        primaryRoute = provider
        if (!backgroundOnly && backgroundRestoreOnly) {
            backgroundRestoreOnly = false
            app.applicationScope.launch { restoreRemembered() }
        }
    }
    private val app = context.applicationContext as BeyGramApplication
    private val preflight = TdSecondaryAccountRoutePreflight.forApplication(app)
    private val launcher = TdSecondaryNativeLoginLauncher.forApplication(app) { primaryRoute() }
    private val registry = TdAccountRegistry(app.filesDir)
    private val restoreStore = TdSecondaryRestoreStore(app)
    val coordinator = TdSecondarySessionCoordinator(
        registry = registry,
        scope = app.applicationScope,
        onAuthorized = { id, preference -> restoreStore.recordNativeReady(id, preference) },
        onManualStop = restoreStore::pauseAfterUserStop,
        onLogoutConfirmed = { id ->
            try { restoreStore.forget(id) } finally {
                NotificationRuntime.get(app).detachSecondary(id)
            }
        },
        isAuthorized = restoreStore::isAuthorized,
        isManuallyStopped = restoreStore::isManuallyStopped,
        openNative = { accountId, preference ->
            TdNativeSecondaryAuthSession(launcher.openRegisteredSecondary(accountId, preference))
        }
    )

    private val reconcileMutex = Mutex()
    private val restoreMutex = Mutex()

    fun isAuthorized(accountId: String): Boolean = restoreStore.isAuthorized(accountId)

    fun isManuallyStopped(accountId: String): Boolean = restoreStore.isManuallyStopped(accountId)

    /** Explicit route chosen for the next sign-in, or the route of the last READY session. */
    fun preferredRoute(accountId: String): TdSecondaryRoutePreference =
        restoreStore.storedRoute(accountId) ?: TdSecondaryRoutePreference.FOLLOW_PRIMARY

    fun setPreferredRoute(accountId: String, preference: TdSecondaryRoutePreference) =
        restoreStore.setStoredRoute(accountId, preference)

    /**
     * Attaches and proves the account's complete TelegramRepository. Account
     * selection must use this instead of treating native READY as UI-ready.
     */
    suspend fun readyRepository(accountId: String, context: Context): TelegramRepository {
        requireSecondary(accountId)
        val operation = MultiAccountDiagnostics.begin("repository_attach", accountId)
        operation.trace("full_repository_request")
        return try {
            val repository = coordinator.fullRepository(accountId, context)
            operation.success(
                "repository_ready",
                attrs = mapOf("phase" to (rowOf(accountId)?.phase?.name ?: "none"))
            )
            repository
        } catch (failure: Throwable) {
            operation.failure(
                "full_repository",
                failure,
                attrs = mapOf("phase" to (rowOf(accountId)?.phase?.name ?: "none"))
            )
            throw failure
        }
    }

    /**
     * One-tap selection of a known account: reopen a stopped/restored session
     * when needed and wait until its full repository is usable. Never switches
     * to legacy data on failure.
     */
    suspend fun ensureReady(accountId: String) = restoreMutex.withLock {
        requireSecondary(accountId)
        val operation = MultiAccountDiagnostics.begin("ensure_ready", accountId)
        try {
            operation.trace("snapshot")
            var row = rowOf(accountId) ?: throw TdAccountSwitchFailure.Disconnected()
            operation.trace(
                "snapshot_state",
                attrs = mapOf(
                    "phase" to (row.phase?.name ?: "none"),
                    "previous_phase" to if (row.connectedInThisProcess) "connected" else "closed"
                )
            )
            if (row.networkSuspended && row.connectedInThisProcess &&
                row.phase != TdAccountAuthPhase.CLOSED
            ) {
                throw TdAccountSwitchFailure.RoutePaused()
            }
            if (!row.connectedInThisProcess || row.phase == TdAccountAuthPhase.CLOSED) {
                if (!restoreStore.isAuthorized(accountId)) {
                    throw TdAccountSwitchFailure.NotAuthorized()
                }
                operation.trace("reopen_required")
                openSecondary(accountId, preferredRoute(accountId))
            }
            row = awaitPhase(accountId, operation) ?: throw TdAccountSwitchFailure.Disconnected()
            when {
                row.phase == TdAccountAuthPhase.READY && !row.networkSuspended -> {
                    operation.success("ready")
                    Unit
                }
                row.phase?.let { it in AUTH_WAITING_PHASES } == true ->
                    throw TdAccountSwitchFailure.NotAuthorized()
                row.networkSuspended -> throw TdAccountSwitchFailure.RoutePaused()
                else -> throw TdAccountSwitchFailure.Disconnected()
            }
        } catch (failure: Throwable) {
            operation.failure("ensure_ready", failure)
            throw failure
        }
    }

    /**
     * Start or resume Telegram sign-in for a reserved slot. No chat repository
     * is exposed before TDLib reaches READY.
     */
    suspend fun connectForSignIn(
        accountId: String,
        preference: TdSecondaryRoutePreference = preferredRoute(accountId)
    ) = restoreMutex.withLock {
        requireSecondary(accountId)
        val operation = MultiAccountDiagnostics.begin(
            "sign_in_connect",
            accountId,
            mapOf("preference" to preference.name)
        )
        restoreStore.setStoredRoute(accountId, preference)
        val row = rowOf(accountId)
        if (row?.connectedInThisProcess == true && row.phase != TdAccountAuthPhase.CLOSED) {
            operation.success("already_connected", attrs = mapOf("phase" to (row.phase?.name ?: "none")))
            return@withLock
        }
        try {
            openSecondary(accountId, preference)
            operation.success("sign_in_session_started")
        } catch (failure: Throwable) {
            operation.failure("sign_in_connect", failure)
            throw failure
        }
    }

    /**
     * Apply an explicit route. A live session (READY or still waiting for
     * credentials) is closed without logout and reopened after the route
     * preflight, so a route-policy pause can be recovered during sign-in. A
     * stopped or not-yet-open account only remembers the preference; its next
     * explicit sign-in/reconnect uses it, so changing a route never clears a
     * manual stop by itself.
     */
    suspend fun useRoute(accountId: String, preference: TdSecondaryRoutePreference) =
        restoreMutex.withLock {
            requireSecondary(accountId)
            val operation = MultiAccountDiagnostics.begin(
                "route_apply",
                accountId,
                mapOf("preference" to preference.name)
            )
            restoreStore.setStoredRoute(accountId, preference)
            val row = rowOf(accountId)
            try {
                when {
                    row != null && row.connectedInThisProcess &&
                        row.phase != TdAccountAuthPhase.CLOSED -> {
                        operation.trace("live_reconnect")
                        reconnectLocked(accountId, preference)
                    }
                    else -> {
                        operation.trace(
                            "route_stored_only",
                            attrs = mapOf("phase" to (row?.phase?.name ?: "none"))
                        )
                    }
                }
                operation.success(
                    "route_applied",
                    attrs = mapOf("phase" to (rowOf(accountId)?.phase?.name ?: "none"))
                )
            } catch (failure: Throwable) {
                operation.failure("route_apply", failure)
                throw failure
            }
        }

    /** Removes only a slot whose native login never reached READY. */
    suspend fun discardIncompleteAccount(accountId: String) {
        requireSecondary(accountId)
        val operation = MultiAccountDiagnostics.begin("discard_data", accountId)
        if (restoreStore.isAuthorized(accountId)) {
            operation.failure("discard_precheck", TdAccountSwitchFailure.NotAuthorized())
            throw TdAccountSwitchFailure.NotAuthorized()
        }
        restoreStore.markDeletionPending(accountId)
        try {
            coordinator.discardIncompleteAccount(accountId)
        } catch (cancelled: CancellationException) {
            // Cancellation before durable unregister must not leave a restart
            // tombstone on a still-registered account.
            kotlinx.coroutines.withContext(
                kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO
            ) {
                runCatching {
                    if (accountId in registry.loadOrCreate().accountIds) {
                        restoreStore.clearDeletionPending(accountId)
                    }
                }
            }
            operation.failure("discard_cancelled", cancelled)
            throw cancelled
        } catch (failure: Throwable) {
            if (accountId in registry.loadOrCreate().accountIds) {
                restoreStore.clearDeletionPending(accountId)
                operation.failure("discard", failure)
                throw failure
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                cleanupUnregisteredAccount(accountId)
            }
            operation.success("discard_completed")
            return
        }
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                operation.trace("file_cleanup_start")
                cleanupUnregisteredAccount(accountId)
            }
            operation.success("discard_completed")
        } catch (failure: Throwable) {
            operation.failure("file_cleanup", failure)
            throw failure
        }
    }

    private fun requireSecondary(accountId: String) {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID) {
            "The primary account is owned by the legacy repository"
        }
    }

    private fun rowOf(accountId: String): TdSecondaryAccountRow? =
        coordinator.snapshot.value.accounts.firstOrNull { it.accountId == accountId }

    private suspend fun openSecondary(
        accountId: String,
        preference: TdSecondaryRoutePreference
    ) {
        val operation = MultiAccountDiagnostics.begin(
            "tdlib_open",
            accountId,
            mapOf("preference" to preference.name)
        )
        operation.trace("connect_start")
        try {
            withTimeout(60_000) { coordinator.connect(accountId, preference) }
            operation.success(
                "tdlib_opened",
                attrs = mapOf("phase" to (rowOf(accountId)?.phase?.name ?: "none"))
            )
        } catch (timeout: TimeoutCancellationException) {
            operation.failure("tdlib_open", timeout)
            throw TdAccountSwitchFailure.Timeout()
        } catch (failure: TdSecondaryRouteUnavailableException) {
            operation.failure("tdlib_open", failure)
            throw TdAccountSwitchFailure.RouteUnavailable(failure.reason)
        } catch (limit: TdSecondarySessionLimitException) {
            operation.failure("tdlib_open", limit)
            throw TdAccountSwitchFailure.SessionLimit()
        } catch (limit: TdAccountLimitException) {
            operation.failure("tdlib_open", limit)
            throw TdAccountSwitchFailure.AccountLimit()
        } catch (cancelled: CancellationException) {
            operation.failure("tdlib_open", cancelled)
            throw cancelled
        }
    }

    /** Wait until TDLib either becomes ready, closes, or asks for sign-in data. */
    private suspend fun awaitPhase(
        accountId: String,
        operation: MultiAccountDiagnostics.Operation? = null
    ): TdSecondaryAccountRow? {
        operation?.trace("phase_wait")
        val row = withTimeoutOrNull(45_000) {
            coordinator.snapshot
                .mapNotNull { snapshot -> snapshot.accounts.firstOrNull { it.accountId == accountId } }
                .first { current ->
                    current.phase == TdAccountAuthPhase.READY ||
                        current.phase == TdAccountAuthPhase.CLOSED ||
                        current.phase?.let { it in AUTH_WAITING_PHASES } == true
                }
        } ?: throw TdAccountSwitchFailure.Timeout()
        operation?.trace(
            "phase_reached",
            attrs = mapOf("phase" to (row.phase?.name ?: "none"))
        )
        return row
    }

    /**
     * Can run after process restart: only accounts for which native TDLib
     * previously confirmed READY are eligible. No phone/OTP is replayed.
     * Proxy failure blocks startup; a later listener change can retry.
     */
    suspend fun restoreRemembered() = restoreMutex.withLock {
        val operation = MultiAccountDiagnostics.begin("restore_all")
        coordinator.refresh()
        val registered = registry.loadOrCreate()
        var eligible = 0
        for ((accountId, preference) in restoreStore.candidates(registered)) {
            if (restoreStore.deletionPending(accountId) ||
                backgroundRestoreOnly && !allowBackgroundRestore(accountId)) {
                operation.trace(
                    "restore_skipped",
                    attrs = mapOf(
                        "account" to accountId,
                        "reason" to if (restoreStore.deletionPending(accountId)) "deletion" else "background_policy"
                    )
                )
                continue
            }
            val row = coordinator.snapshot.value.accounts.firstOrNull { it.accountId == accountId }
            if (row?.connectedInThisProcess == true) continue
            eligible++
            val accountOperation = MultiAccountDiagnostics.begin(
                "restore_account",
                accountId,
                mapOf("preference" to preference.name)
            )
            // If the last generation is still closing, connect refuses to
            // reopen its database. Never force-unlock TDLib in that case.
            runCatching { coordinator.connect(accountId, preference) }
                .onSuccess { accountOperation.milestone("restore_connect_started") }
                .onFailure { failure ->
                    accountOperation.failure("restore_connect", failure)
                }
        }
        operation.success("restore_completed", attrs = mapOf("count" to eligible.toString()))
    }

    init {
        app.applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // A crash after native CLOSED+unregister but before file deletion
            // leaves a durable tombstone. Resume only if the slot is unregistered.
            for (id in restoreStore.pendingDeletionIds()) {
                if (id !in registry.loadOrCreate().accountIds) {
                    runCatching { cleanupUnregisteredAccount(id) }
                }
            }
        }
        app.applicationScope.launch {
            // Attempt once when the process is first brought up. The route
            // policy may still block until legacy/Mihomo are ready.
            restoreRemembered()
        }
        app.applicationScope.launch {
            // The same full repository drives normal Compose screens and
            // secondary Android alerts. Installing this subscription when
            // native auth becomes READY allows notifications after a process
            // restart even before the user opens that account's chat list.
            coordinator.snapshot.map { snapshot ->
                snapshot.accounts.filter {
                    it.connectedInThisProcess &&
                        it.phase == TdAccountAuthPhase.READY &&
                        !it.networkSuspended
                }.map { it.accountId }.toSet()
            }.distinctUntilChanged().collectLatest { accountIds ->
                for (id in accountIds) {
                    runCatching {
                        val full = coordinator.fullRepository(id, app)
                        NotificationRuntime.get(app).attachSecondary(id, full)
                    }
                }
            }
        }
        app.applicationScope.launch {
            // Overflow or failed full-repository attachment closes native TDLib
            // WITHOUT Telegram logout. Retry only a bounded number of times.
            // User stop writes manual_stop; logout writes a deletion tombstone.
            val attempts = mutableMapOf<String, Int>()
            coordinator.snapshot.map { state ->
                state.accounts.filter { it.phase == TdAccountAuthPhase.CLOSED }
                    .map { it.accountId }.toSet()
            }.distinctUntilChanged().collectLatest { closed ->
                val candidates = closed.filter { id ->
                    restoreStore.confirmedRoute(id) != null &&
                        !restoreStore.deletionPending(id) &&
                        (!backgroundRestoreOnly || allowBackgroundRestore(id)) &&
                        (attempts[id] ?: 0) < 2
                }
                if (candidates.isNotEmpty()) {
                    candidates.forEach { id -> attempts[id] = (attempts[id] ?: 0) + 1 }
                    delay(1_500)
                    restoreRemembered()
                }
            }
        }
        app.applicationScope.launch {
            // React immediately to every listener status transition, including
            // STARTING/STOPPED. Initial DIRECT and shared SOCKS both need to be
            // checked against the current primary account route.
            MihomoProxyService.runtimeState.map { it.status }.distinctUntilChanged().collect {
                checkRoutesNow()
                restoreRemembered()
            }
        }
        app.applicationScope.launch {
            // The legacy TDLib repository doesn't currently expose a StateFlow
            // for manual proxy changes. Poll ONLY while a secondary is active:
            // a FOLLOW_PRIMARY client originally DIRECT must not keep sending
            // direct traffic indefinitely after the primary route changes.
            coordinator.snapshot
                .map { state -> state.accounts.any { it.connectedInThisProcess } }
                .distinctUntilChanged()
                .collectLatest { active ->
                    while (active) {
                        delay(15_000)
                        checkRoutesNow()
                    }
                }
        }
    }

    /** Run only after explicit user confirmation. TDLib owns deletion of its
     * authorization; account-root files are removed only after CLOSED and
     * the registry no longer references this ID. Symlinks are never followed. */
    suspend fun logoutAndDelete(accountId: String) {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID &&
            TdAccountStorageLayout.isValidAccountId(accountId))
        val operation = MultiAccountDiagnostics.begin("logout_delete", accountId)
        restoreStore.markDeletionPending(accountId)
        // A native logout failure must not unregister or delete an active DB.
        try {
            operation.trace("coordinator_logout_start")
            coordinator.logoutSecondary(accountId)
            operation.trace("coordinator_logout_done")
        } catch (error: Throwable) {
            if (accountId in registry.loadOrCreate().accountIds) {
                restoreStore.clearDeletionPending(accountId)
                operation.failure("logout", error)
                throw error
            }
            // The native session was already committed out of the registry,
            // but secondary preference cleanup failed. Finish the durable
            // tombstone now instead of orphaning a deleted account.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                operation.trace("file_cleanup_start")
                cleanupUnregisteredAccount(accountId)
            }
            operation.success("logout_completed")
            return
        }
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                operation.trace("file_cleanup_start")
                cleanupUnregisteredAccount(accountId)
            }
            operation.success("logout_completed")
        } catch (failure: Throwable) {
            operation.failure("file_cleanup", failure)
            throw failure
        }
    }

    private fun cleanupUnregisteredAccount(accountId: String) {
        check(accountId !in registry.loadOrCreate().accountIds) {
            "Never erase a registered TDLib account's files"
        }
        deleteAccountTreeSafely(
            java.io.File(app.filesDir, "tdlib-accounts"),
            java.io.File(app.filesDir, "tdlib-accounts/$accountId")
        )
        deleteAccountTreeSafely(
            java.io.File(app.noBackupFilesDir, "reply-outbox-secondary"),
            java.io.File(app.noBackupFilesDir, "reply-outbox-secondary/$accountId")
        )
        deleteAccountTreeSafely(
            app.noBackupFilesDir,
            java.io.File(app.noBackupFilesDir, "video-note-loss-$accountId")
        )
        // Global sent-upload staging is shared. Only folders explicitly
        // tagged with this native account may be removed.
        val uploads = java.io.File(app.filesDir, "uploads")
        uploads.listFiles().orEmpty().filter {
            it.isDirectory && uploadFolderBelongsToAccount(it, accountId)
        }.forEach { deleteAccountTreeSafely(uploads, it) }
        listOf(
            tdDraftPreferencesName(accountId),
            archivePreferencesName(accountId),
            notificationPreferencesName(accountId),
            translationPreferencesName(accountId),
            chatOverridePreferencesName(accountId),
            "pending_group_creation_$accountId",
            "mihomo_tdlib_proxy_$accountId"
        ).forEach { prefName -> app.deleteSharedPreferences(prefName) }
        com.beykus.beygram.data.archive.ArchiveSettingsStore.releaseAccount(accountId)
        restoreStore.forget(accountId)
        restoreStore.clearDeletionPending(accountId)
    }

    /** Deletion never traverses symlinks or a path outside its exact parent. */
    private fun deleteAccountTreeSafely(parent: java.io.File, target: java.io.File) {
        val root = parent.canonicalFile
        val expected = java.io.File(parent.parentFile.canonicalFile, parent.name)
        require(root == expected &&
            target.canonicalFile.parentFile == root &&
            !java.nio.file.Files.isSymbolicLink(parent.toPath()) &&
            !java.nio.file.Files.isSymbolicLink(target.toPath())) {
            "Account data path is not safe to delete"
        }
        if (!target.exists()) return
        java.nio.file.Files.walkFileTree(target.toPath(),
            object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                    java.nio.file.Files.delete(file)
                    return java.nio.file.FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: java.nio.file.Path, error: java.io.IOException?): java.nio.file.FileVisitResult {
                    if (error != null) throw error
                    java.nio.file.Files.delete(dir)
                    return java.nio.file.FileVisitResult.CONTINUE
                }
            })
    }

    /** Coordinates manual route switches against automatic account restore. */
    suspend fun reconnectWithRoute(accountId: String, preference: TdSecondaryRoutePreference) =
        restoreMutex.withLock {
            requireSecondary(accountId)
            restoreStore.setStoredRoute(accountId, preference)
            reconnectLocked(accountId, preference)
        }

    private suspend fun reconnectLocked(
        accountId: String,
        preference: TdSecondaryRoutePreference
    ) {
        val operation = MultiAccountDiagnostics.begin(
            "route_reconnect",
            accountId,
            mapOf("preference" to preference.name)
        )
        operation.trace("coordinator_reconnect")
        try {
            withTimeout(120_000) {
                coordinator.reconnectWithRoute(accountId, preference) { route ->
                    val decision = preflight.decide(primaryRoute(), route)
                    MultiAccountDiagnostics.milestone(
                        code = "route_decision",
                        stage = if (decision is TdSecondaryRouteDecision.Blocked) "blocked" else "allowed",
                        accountId = accountId,
                        attrs = mapOf(
                            "preference" to route.name,
                            "decision" to when (decision) {
                                TdSecondaryRouteDecision.Direct -> "direct"
                                TdSecondaryRouteDecision.SharedMihomo -> "shared_mihomo"
                                is TdSecondaryRouteDecision.Blocked -> "blocked"
                            },
                            "route_reason" to ((decision as? TdSecondaryRouteDecision.Blocked)?.reason?.name ?: "none")
                        )
                    )
                    if (decision is TdSecondaryRouteDecision.Blocked) {
                        throw TdSecondaryRouteUnavailableException(decision.reason)
                    }
                }
            }
            operation.success("reconnected")
        } catch (timeout: TimeoutCancellationException) {
            operation.failure("reconnect_timeout", timeout)
            throw TdAccountSwitchFailure.Timeout()
        } catch (failure: TdSecondaryRouteUnavailableException) {
            operation.failure("reconnect_route", failure)
            throw TdAccountSwitchFailure.RouteUnavailable(failure.reason)
        } catch (cancelled: CancellationException) {
            operation.failure("reconnect_cancelled", cancelled)
            throw cancelled
        } catch (failure: Throwable) {
            operation.failure("reconnect", failure)
            throw failure
        }
    }

    /** Also called when entering the account screen; serializes all route probes. */
    suspend fun checkRoutesNow() = reconcileMutex.withLock {
        coordinator.reconcileRoutes { preference ->
            preflight.decide(primaryRoute(), preference)
        }
    }

    companion object {
        @Volatile private var instance: TdSecondaryAccountController? = null

        fun get(context: Context, primaryRoute: suspend () -> TelegramRoute?, backgroundOnly: Boolean = false): TdSecondaryAccountController =
            synchronized(this) {
                (instance ?: TdSecondaryAccountController(context.applicationContext, primaryRoute, backgroundOnly)
                    .also { instance = it }).also { it.updateProvider(primaryRoute, backgroundOnly) }
            }
    }
}
