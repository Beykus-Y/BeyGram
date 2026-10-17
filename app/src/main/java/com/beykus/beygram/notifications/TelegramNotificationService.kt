package com.beykus.beygram.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.TelegramConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TelegramNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var runtime: NotificationRuntime
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        runtime = NotificationRuntime.get(this)
        AndroidNotificationRenderer.channels(this)
        val initial = statusNotification(getString(R.string.notif_service_connecting))
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(AndroidNotificationRenderer.SERVICE_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(AndroidNotificationRenderer.SERVICE_ID, initial)
        } catch (_: RuntimeException) { runtime.serviceState(false, UiText.Res(R.string.err_notif_background_denied)); stopSelf(); return }
        if (!shouldRun(this)) { stopSelf(); return }
        runtime.serviceState(true)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:notification_delivery").apply { setReferenceCounted(false); acquire(15_000) }
        val repository = runtime.attachRealClient()
        if (repository == null) { runtime.serviceState(false, UiText.Res(R.string.err_notif_telegram_start_failed)); stopSelf(); return }
        scope.launch {
            combine(repository.connectionState, repository.authState, runtime.preferences.state) { connection, auth, config ->
                if (!config.enabled || !config.background || auth == AuthState.WaitingForPhone) { stopSelf(); return@combine }
                val text = when (connection) {
                    TelegramConnectionState.Connected -> getString(R.string.notif_service_receiving)
                    TelegramConnectionState.WaitingForNetwork -> getString(R.string.notif_service_waiting_network)
                    else -> getString(R.string.notif_service_connecting)
                }
                getSystemService(android.app.NotificationManager::class.java).notify(AndroidNotificationRenderer.SERVICE_ID, statusNotification(text))
            }.collect { }
        }
        scope.launch { repository.notifications.collect { state ->
            if (state.hasPending) wakeLock?.let { if (!it.isHeld) it.acquire(60_000) }
            else wakeLock?.let { if (it.isHeld) it.release() }
        } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            runtime.preferences.update(runtime.preferences.state.value.copy(background = false))
            stopSelf(); return START_NOT_STICKY
        }
        if (!shouldRun(this)) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }
    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        if (::runtime.isInitialized) runtime.serviceState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }
    private fun statusNotification(text: String): android.app.Notification {
        val stopIntent = android.app.PendingIntent.getService(this, 1, Intent(this, TelegramNotificationService::class.java).setAction(STOP), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, AndroidNotificationRenderer.CONNECTION).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("BeyGram").setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).setContentIntent(NotificationIntents.settings(this)).addAction(0, getString(R.string.notif_action_stop), stopIntent).build()
    }
    companion object {
        private const val STOP = "com.beykus.beygram.STOP_BACKGROUND_NOTIFICATIONS"
        fun shouldRun(context: Context): Boolean = NotificationRuntime.get(context).preferences.let { it.authorized && it.state.value.enabled && it.state.value.background && AndroidNotificationRenderer.canNotify(context) }
        /** Call only from a resumed UI or an allowed system boot/package replacement receiver. */
        fun reconcileFromAllowedContext(context: Context) {
            if (!shouldRun(context)) { context.stopService(Intent(context, TelegramNotificationService::class.java)); return }
            try { ContextCompat.startForegroundService(context, Intent(context, TelegramNotificationService::class.java)) }
            catch (_: RuntimeException) { NotificationRuntime.get(context).serviceState(false, UiText.Res(R.string.err_notif_background_start_failed)) }
        }
    }
}
