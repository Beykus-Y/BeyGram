package com.beykus.beygram

import android.app.Application
import com.beykus.beygram.data.tdlib.TdLibBootstrap
import com.beykus.beygram.data.tdlib.TdLibTelegramRepository
import com.beykus.beygram.data.vpn.MihomoProxyClient
import com.beykus.beygram.data.vpn.VpnBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BeyGramApplication : Application() {
    /** Lives as long as the process, so the proxy and its automatic selection do not depend on an Activity. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        MihomoProxyClient.install(this)
        // Boot and notification services can start the process without an Activity. TDLib may still use the local
        // proxy saved in its database, so the backend, which restores that proxy, must also exist for those starts.
        if (TdLibBootstrap(this).readiness().canCreateClient) {
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
