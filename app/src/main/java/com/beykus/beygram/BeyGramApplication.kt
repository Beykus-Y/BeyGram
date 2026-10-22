package com.beykus.beygram

import android.app.Application
import com.beykus.beygram.analytics.AnalyticsSync
import com.beykus.beygram.data.tdlib.TdLibBootstrap
import com.beykus.beygram.data.tdlib.TdLibTelegramRepository
import com.beykus.beygram.data.vpn.MihomoProxyClient
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.notifications.TelegramNotificationService
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.share.DirectShareShortcuts
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
        MihomoProxyClient.install(this)
        // Analytics runs on its own store and HTTPS client, so it neither waits for Telegram nor starts TDLib.
        AnalyticsSync.install(this, applicationScope)
        // Background notifications are the only work that needs TDLib without a screen. Starting the client only when they
        // run keeps a disabled feature from connecting after boot; the backend restores the local proxy for that client.
        if (TelegramNotificationService.shouldRun(this) && TdLibBootstrap(this).readiness().canCreateClient) {
            val repository = TdLibTelegramRepository.getInstance(this)
            startDirectShare(repository)
            VpnBackend.getInstance(
                context = this,
                connectionState = repository.connectionState,
                scope = applicationScope,
                setTdLibProxy = repository::setMihomoProxyEnabled,
                isTdLibProxyEnabled = repository::isMihomoProxyEnabled
            )
        }
    }
}
