package com.beykus.beygram.data.tdlib

import android.content.Context
import com.beykus.beygram.BeyGramApplication
import com.beykus.beygram.data.vpn.MihomoProxyService
import com.beykus.beygram.domain.model.TelegramRoute
import com.beykus.beygram.notifications.NotificationRuntime
import com.beykus.beygram.notifications.NotificationPreferencesStore
import com.beykus.beygram.notifications.notificationPreferencesName
import com.beykus.beygram.data.archive.archivePreferencesName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        val prefs = NotificationPreferencesStore(app, accountId)
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
            restoreStore.forget(id)
            NotificationRuntime.get(app).detachSecondary(id)
        },
        openNative = { accountId, preference ->
            TdNativeSecondaryAuthSession(launcher.openRegisteredSecondary(accountId, preference))
        }
    )

    private val reconcileMutex = Mutex()
    private val restoreMutex = Mutex()

    /**
     * Can run after process restart: only accounts for which native TDLib
     * previously confirmed READY are eligible. No phone/OTP is replayed.
     * Proxy failure blocks startup; a later listener change can retry.
     */
    suspend fun restoreRemembered() = restoreMutex.withLock {
        coordinator.refresh()
        val registered = registry.loadOrCreate()
        for ((accountId, preference) in restoreStore.candidates(registered)) {
            if (backgroundRestoreOnly && !allowBackgroundRestore(accountId)) continue
            val row = coordinator.snapshot.value.accounts.firstOrNull { it.accountId == accountId }
            if (row?.connectedInThisProcess == true) continue
            // If the last generation is still closing, connect refuses to
            // reopen its database. Never force-unlock TDLib in that case.
            runCatching { coordinator.connect(accountId, preference) }
        }
    }

    init {
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
        // The coordinator invokes real TDLib LogOut, awaits the lease-retired
        // callback, revokes restore policy, then atomically unregisters the ID.
        coordinator.logoutSecondary(accountId)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID &&
                accountId.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
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
            listOf(
                tdDraftPreferencesName(accountId),
                archivePreferencesName(accountId),
                notificationPreferencesName(accountId),
                "pending_group_creation_$accountId",
                "mihomo_tdlib_proxy_$accountId"
            ).forEach { prefName -> app.deleteSharedPreferences(prefName) }
        }
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
            coordinator.reconnectWithRoute(accountId, preference) { route ->
                val decision = preflight.decide(primaryRoute(), route)
                check(decision !is TdSecondaryRouteDecision.Blocked) {
                    "Connection route is currently unavailable"
                }
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
