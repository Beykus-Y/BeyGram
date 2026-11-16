package com.beykus.beygram.media.bgwave

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the process in the foreground while a listener's wave runs, so the audio and the connection survive a locked
 * screen or an app in the background. It holds no wave state: [BgWaveHub] owns the wave and [BgWaveListenerKeepAlive]
 * starts and stops this service. The notification names the wave and has a leave action.
 *
 * The promotion to foreground happens in onStartCommand, not onCreate. Every start, including one that arrives while the
 * service already runs, must be answered with startForeground, and onStartCommand is the one place all starts reach.
 */
class BgWaveListenerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var watching = false
    private var lastStartId = 0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.context(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_STOP) {
            // NonCancellable, and not a child of [scope]: the cancel in onDestroy cannot cut the leave short. stopSelf()
            // comes only after the peer has been told, so the service stays in the foreground until then.
            scope.launch(NonCancellable) {
                try {
                    BgWaveHub.stop()
                } catch (error: Exception) {
                    BgWaveLog.w("listener.service.leave", error)
                } finally {
                    // With this start's id: a wave started meanwhile has a newer start and keeps the service.
                    stopSelf(startId)
                }
            }
            return START_NOT_STICKY
        }
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        watchWave()
        return START_NOT_STICKY
    }

    /**
     * The service stops itself once the wave no longer needs it, and only after startForeground: a stopService() from
     * outside that lands before the promotion would crash the app with a missed foreground start.
     */
    private fun watchWave() {
        if (watching) return
        watching = true
        scope.launch(Dispatchers.Main.immediate) {
            BgWaveHub.controller.state.first { !BgWaveListenerKeepAlivePolicy.shouldKeepAlive(it) }
            watching = false
            // Both this and onStartCommand run on the main thread, so a start that comes later has a newer id and keeps
            // the service (with a fresh watcher) instead of being stopped here.
            stopSelf(lastStartId)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock.releaseIfHeld()
        wifiLock.releaseIfHeld()
        stopForeground(STOP_FOREGROUND_REMOVE)
        BgWaveLog.i("listener.service.stop")
        super.onDestroy()
    }

    /** Promotes the service. Returns false when the system refuses, and the caller then stops the service. */
    private fun enterForeground(): Boolean = try {
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        BgWaveLog.i("listener.service.start")
        true
    } catch (error: RuntimeException) {
        BgWaveLog.w("listener.service.foreground", error)
        false
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.bgwave_listener_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
    }

    private fun buildNotification(): Notification {
        val leave = PendingIntent.getService(
            this,
            1,
            Intent(this, BgWaveListenerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.bgwave_listener_notification_title))
            .setContentText(getString(R.string.bgwave_listener_notification_text))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .addAction(0, getString(R.string.bgwave_action_stop_wave), leave)
        // Tapping the notification opens the app; without a launch intent the notification simply has no tap action.
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setContentIntent(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        }
        return builder.build()
    }

    /** A refused lock or a missing permission must not stop the wave; the service simply runs without that lock. */
    private fun acquireLocks() {
        wakeLock = runCatching {
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                .apply {
                    setReferenceCounted(false)
                    acquire(WAKE_LOCK_TIMEOUT_MILLIS)
                }
        }.onFailure { BgWaveLog.w("listener.service.wake_lock", it) }.getOrNull()
        wifiLock = runCatching {
            getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, WAKE_LOCK_TAG)
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }.onFailure { BgWaveLog.w("listener.service.wifi_lock", it) }.getOrNull()
    }

    private fun PowerManager.WakeLock?.releaseIfHeld() {
        val lock = this ?: return
        if (lock.isHeld) runCatching { lock.release() }
    }

    private fun WifiManager.WifiLock?.releaseIfHeld() {
        val lock = this ?: return
        if (lock.isHeld) runCatching { lock.release() }
    }

    companion object {
        const val ACTION_STOP = "com.beykus.beygram.bgwave_listener.STOP"

        /** Distinct from the app's other notification ids: see AndroidNotificationRenderer.SERVICE_ID and MihomoProxyService. */
        private const val NOTIFICATION_ID = 7013
        private const val CHANNEL_ID = "bgwave_listener"
        private const val WAKE_LOCK_TAG = "BeyGram:BgWaveListener"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 6L * 60 * 60 * 1000
    }
}
