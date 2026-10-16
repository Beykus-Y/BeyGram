package com.beykus.beygram

import android.app.Application
import com.beykus.beygram.data.tdlib.TdLibBootstrap
import com.beykus.beygram.data.tdlib.TdLibTelegramRepository
import com.beykus.beygram.data.vpn.MihomoProxyClient
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.notifications.TelegramNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BeyGramApplication : Application() {
    /** Lives as long as the process, so the proxy and its automatic selection do not depend on an Activity. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        MihomoProxyClient.install(this)
        // Background notifications are the only work that needs TDLib without a screen. Starting the client only when they
        // run keeps a disabled feature from connecting after boot; the backend restores the local proxy for that client.
        if (TelegramNotificationService.shouldRun(this) && TdLibBootstrap(this).readiness().canCreateClient) {
            val repository = TdLibTelegramRepository.getInstance(this)
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
