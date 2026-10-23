package com.beykus.beygram

import android.app.Application
import android.util.Log
import com.beykus.beygram.analytics.AnalyticsSync
import com.beykus.beygram.data.tdlib.TdAccountRegistry
import com.beykus.beygram.data.tdlib.TdLibBootstrap
import com.beykus.beygram.data.tdlib.TdLibTelegramRepository
import com.beykus.beygram.data.tdlib.TdSecondaryAccountController
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.data.vpn.MihomoProxyClient
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.notifications.TelegramNotificationService
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.share.DirectShareShortcuts
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BeyGramApplication : Application() {
    /** Lives as long as the process, so the proxy and its automatic selection do not depend on an Activity. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var directShareRepository: TelegramRepository? = null
    private var directShareJob: Job? = null

    @Synchronized
    fun startDirectShare(repository: TelegramRepository) {
        if (directShareRepository === repository && directShareJob?.isActive == true) return
        directShareJob?.cancel()
        directShareRepository = repository
        directShareJob = DirectShareShortcuts.observe(this, repository, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        // Stage A of multi-account: metadata-only zero-copy migration. The existing
        // TDLib client continues to use filesDir/tdlib exactly as before. If
        // disk/metadata is corrupt, leave TDLib untouched and report the failure;
        // never overwrite a potentially recoverable account registry.
        runCatching { TdAccountRegistry(filesDir).loadOrCreate() }
            .onFailure { Log.e("BeyGramAccounts", "Account registry initialization failed", it) }
        MihomoProxyClient.install(this)
        // Analytics runs on its own store and HTTPS client, so it neither waits for Telegram nor starts TDLib.
        AnalyticsSync.install(this, applicationScope)
        // Background notifications are the only work that needs TDLib without a screen. Starting the client only when they
        // run keeps a disabled feature from connecting after boot; the backend restores the local proxy for that client.
        if (TelegramNotificationService.shouldRun(this) && TdLibBootstrap(this).readiness().canCreateClient) {
            // A secondary-only background service MUST NOT create legacy TDLib:
            // the first account can explicitly have background delivery disabled.
            val legacy = if (TelegramNotificationService.shouldRunLegacy(this))
                TdLibTelegramRepository.getInstance(this) else null
            if (legacy != null) {
                startDirectShare(legacy)
                VpnBackend.getInstance(
                    context = this,
                    connectionState = legacy.connectionState,
                    scope = applicationScope,
                    setTdLibProxy = legacy::setMihomoProxyEnabled,
                    isTdLibProxyEnabled = legacy::isMihomoProxyEnabled
                )
            }
            // The controller only resumes notification-enabled secondary slots on
            // background startup. Unknown primary/proxy policy blocks network access.
            val secondary = TdSecondaryAccountController.get(
                this, { legacy?.currentRoute()?.getOrNull() }, backgroundOnly = true
            )
            if (legacy != null) {
                applicationScope.launch {
                    legacy.authState.collect { state ->
                        if (state == AuthState.Ready) secondary.restoreRemembered()
                    }
                }
            }
        }
    }
}
