package com.beykus.beygram.data.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.beykus.beygram.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ProxyRuntimeStatus { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

data class ProxyRuntimeState(val status: ProxyRuntimeStatus, val message: String? = null)

/** Keeps Mihomo's localhost SOCKS listener alive; it does not create an Android VPN. */
class MihomoProxyService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var core: MihomoCoreBridge? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProxy()
            ACTION_STOP -> stopProxy()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        // Explicit stop closes the listener before stopSelf(); process death also tears down the native core.
        core = null
        serviceScope.cancel()
        _runtimeState.value = ProxyRuntimeState(ProxyRuntimeStatus.STOPPED)
        super.onDestroy()
    }

    private fun startProxy() {
        if (_runtimeState.value.status == ProxyRuntimeStatus.RUNNING || _runtimeState.value.status == ProxyRuntimeStatus.STARTING) return
        _runtimeState.value = ProxyRuntimeState(ProxyRuntimeStatus.STARTING)
        MihomoRuntimeDiagnostics.event("service", "start requested; profileExists=${MihomoCoreBridge(applicationContext).profileExists()}")
        startForeground(NOTIFICATION_ID, buildNotification())
        serviceScope.launch {
            try {
                val bridge = MihomoCoreBridge(applicationContext)
                if (!bridge.profileExists()) throw IllegalStateException("Сначала загрузите подписку Mihomo")
                bridge.ensureInitialized()
                MihomoRuntimeDiagnostics.event("service", "profile initialized")
                core = bridge
                bridge.startProxyListener()
                _runtimeState.value = ProxyRuntimeState(ProxyRuntimeStatus.RUNNING)
                MihomoRuntimeDiagnostics.event("service", "state=RUNNING")
            } catch (error: Exception) {
                runCatching { core?.stopProxyListener() }
                core = null
                MihomoRuntimeDiagnostics.failure("service start", error)
                withContext(Dispatchers.Main) {
                    _runtimeState.value = ProxyRuntimeState(ProxyRuntimeStatus.FAILED, error.message ?: "Не удалось запустить прокси")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun stopProxy() {
        val current = _runtimeState.value.status
        if (current == ProxyRuntimeStatus.STOPPED || current == ProxyRuntimeStatus.STOPPING) {
            stopSelf()
            return
        }
        _runtimeState.value = ProxyRuntimeState(ProxyRuntimeStatus.STOPPING)
        MihomoRuntimeDiagnostics.event("service", "stop requested")
        serviceScope.launch {
            runCatching { core?.stopProxyListener() }
            core = null
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                _runtimeState.value = ProxyRuntimeState(ProxyRuntimeStatus.STOPPED)
                MihomoRuntimeDiagnostics.event("service", "state=STOPPED")
                stopSelf()
            }
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("BeyGram proxy (dev)")
        .setContentText("Локальный SOCKS5: 127.0.0.1:${MihomoProxyClient.PORT}")
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "BeyGram proxy (dev)", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val ACTION_START = "com.beykus.beygram.mihomo_proxy.START"
        const val ACTION_STOP = "com.beykus.beygram.mihomo_proxy.STOP"
        private const val CHANNEL_ID = "beygram-mihomo-proxy-dev"
        private const val NOTIFICATION_ID = 7012
        private val _runtimeState = MutableStateFlow(ProxyRuntimeState(ProxyRuntimeStatus.STOPPED))
        val runtimeState: StateFlow<ProxyRuntimeState> = _runtimeState.asStateFlow()
    }
}
