package com.beykus.beygram.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.data.tdlib.TdAccountRegistry
import com.beykus.beygram.data.tdlib.TdSecondaryRestoreStore
import com.beykus.beygram.data.vpn.MihomoProxyClient
import com.beykus.beygram.data.tdlib.TdSecondaryRoutePreference
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.TelegramConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Cold-start route policy. Without the legacy process owner only an explicit
 * DIRECT secondary session can be restored without a misleading FGS.
 * Other routes remain saved, ready for an explicit foreground reconnection.
 */
internal fun secondaryColdStartRouteEligible(
    route: TdSecondaryRoutePreference?,
    legacyCanStart: Boolean,
    sharedListenerActive: Boolean = false
): Boolean = when (route) {
    TdSecondaryRoutePreference.EXPLICIT_DIRECT -> true
    TdSecondaryRoutePreference.FOLLOW_PRIMARY -> legacyCanStart
    // A legacy account using DIRECT cannot bootstrap a listener merely by
    // being authorized. Require a listener already owned by this process;
    // native route preflight independently validates SOCKS credentials.
    TdSecondaryRoutePreference.SHARED_MIHOMO -> sharedListenerActive
    null -> false
}

/** Pure policy used by cold-start eligibility and regression tests. */
internal fun secondaryBackgroundEligible(
    routeConfirmed: Boolean,
    authorized: Boolean,
    enabled: Boolean,
    background: Boolean
): Boolean = routeConfirmed && authorized && enabled && background

class TelegramNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var runtime: NotificationRuntime
    // Bounded: released at the first Connected state, or by its own timeout if the connection never gets there.
    private var startupWakeLock: PowerManager.WakeLock? = null
    // Held only while TDLib reports pending deliveries; it also expires by itself.
    private var deliveryWakeLock: PowerManager.WakeLock? = null
    private var createdAt = 0L
    // A Connected state can arrive before onStartCommand records the start; its report waits for that start event.
    private var connectionReady = false
    private var connectedReported = false
    private var startReported = false
    // Reason recorded when the service stops; stays "system" when the platform stops it.
    private var stopReason = "system"
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.context(newBase))
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        createdAt = SystemClock.elapsedRealtime()
        runtime = NotificationRuntime.get(this)
        AndroidNotificationRenderer.channels(this)
        val initial = statusNotification(getString(R.string.notif_service_connecting))
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(AndroidNotificationRenderer.SERVICE_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(AndroidNotificationRenderer.SERVICE_ID, initial)
        } catch (_: RuntimeException) { stopReason = "foreground_denied"; runtime.serviceState(false, UiText.Res(R.string.err_notif_background_denied)); stopSelf(); return }
        if (!shouldRun(this)) { stopReason = "not_allowed"; stopSelf(); return }
        runtime.serviceState(true)
        startupWakeLock = newWakeLock().apply { acquire(STARTUP_WAKE_LOCK_MILLIS) }
        deliveryWakeLock = newWakeLock()
        val legacyAllowed = shouldRunLegacy(this)
        val repository = if (legacyAllowed) runtime.attachRealClient() else null
        if (legacyAllowed && repository == null) {
            runtime.serviceState(false, UiText.Res(R.string.err_notif_telegram_start_failed))
            stopSelf(); return
        }
        scope.launch {
            runtime.secondaryConnected.collect { connected ->
                if (connected) {
                    connectionReady = true
                    startupWakeLock.releaseIfHeld()
                    reportConnectedOnce()
                }
                if (repository == null) {
                    val text = if (connected) getString(R.string.notif_service_receiving)
                        else getString(R.string.notif_service_connecting)
                    getSystemService(android.app.NotificationManager::class.java)
                        .notify(AndroidNotificationRenderer.SERVICE_ID, statusNotification(text))
                }
            }
        }
        if (repository != null) {
            scope.launch {
                combine(repository.connectionState, repository.authState, runtime.preferences.state) { connection, auth, config ->
                    if (connection == TelegramConnectionState.Connected) {
                        connectionReady = true; startupWakeLock.releaseIfHeld(); reportConnectedOnce()
                    }
                    if (!config.enabled || !config.background || auth == AuthState.WaitingForPhone) {
                        scope.launch {
                            if (!withContext(Dispatchers.IO) {
                                shouldRun(this@TelegramNotificationService)
                            }) {
                                stopReason = if (!config.enabled || !config.background) "disabled" else "logged_out"
                                stopSelf()
                            }
                        }
                        return@combine
                    }
                    val text = when (connection) {
                        TelegramConnectionState.Connected -> getString(R.string.notif_service_receiving)
                        TelegramConnectionState.WaitingForNetwork -> getString(R.string.notif_service_waiting_network)
                        else -> getString(R.string.notif_service_connecting)
                    }
                    getSystemService(android.app.NotificationManager::class.java)
                        .notify(AndroidNotificationRenderer.SERVICE_ID, statusNotification(text))
                }.collect { }
            }
        }
        // Account authorization, manual stop and route eligibility may change
        // outside the legacy repository collector. A secondary-only foreground
        // service must terminate rather than advertise perpetual "Connecting…".
        scope.launch {
            while (isActive) {
                delay(15_000L)
                val permitted = withContext(Dispatchers.IO) {
                    shouldRun(this@TelegramNotificationService)
                }
                if (!permitted) {
                    stopReason = "not_allowed"
                    stopSelf()
                    break
                }
            }
        }
        scope.launch {
            val pending = if (repository == null) runtime.secondaryPending
                else combine(repository.notifications, runtime.secondaryPending) { legacy, secondary ->
                    legacy.hasPending || secondary
                }
            pending.collect { hasPending ->
                if (hasPending) deliveryWakeLock?.let { if (!it.isHeld) it.acquire(DELIVERY_WAKE_LOCK_MILLIS) }
                else deliveryWakeLock.releaseIfHeld()
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startReported) {
            startReported = true
            val source = intent?.getStringExtra(EXTRA_SOURCE) ?: "system"
            recordAppEvent(EventKind.NOTIFICATION, "service_started", "Фоновая служба запущена: ${startSourceText(source)}", attrs = mapOf("source" to source))
            // The connection may already be ready; its readiness is journaled only after this start event.
            reportConnectedOnce()
        }
        if (intent?.action == STOP) {
            stopReason = "user"
            runtime.preferences.update(runtime.preferences.state.value.copy(background = false))
            for (id in registeredSecondaryAccounts(this)) {
                val prefs = runtime.preferencesForAccount(id)
                prefs.update(prefs.state.value.copy(background = false))
            }
            stopSelf(); return START_NOT_STICKY
        }
        if (!shouldRun(this)) { stopReason = "not_allowed"; stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }
    override fun onDestroy() {
        scope.cancel()
        startupWakeLock.releaseIfHeld()
        deliveryWakeLock.releaseIfHeld()
        externalStopReason?.let { stopReason = it; externalStopReason = null }
        if (startReported) recordAppEvent(EventKind.NOTIFICATION, "service_stopped", "Фоновая служба остановлена: ${stopReasonText(stopReason)}", attrs = mapOf("reason" to stopReason))
        if (::runtime.isInitialized) runtime.serviceState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { stopReason = "timeout"; stopSelf() }
    // Journaled only after the start event, so readiness never precedes the start whose latency it measures.
    private fun reportConnectedOnce() {
        if (connectedReported || !connectionReady || !startReported) return
        connectedReported = true
        val elapsed = SystemClock.elapsedRealtime() - createdAt
        recordAppEvent(EventKind.NOTIFICATION, "startup_connected", "Фоновое соединение готово за $elapsed мс", attrs = mapOf("elapsed_ms" to elapsed.toString()))
    }
    private fun newWakeLock(): PowerManager.WakeLock = getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:notification_delivery").apply { setReferenceCounted(false) }
    private fun PowerManager.WakeLock?.releaseIfHeld() {
        if (this != null && isHeld) release()
    }
    private fun statusNotification(text: String): android.app.Notification {
        val stopIntent = android.app.PendingIntent.getService(this, 1, Intent(this, TelegramNotificationService::class.java).setAction(STOP), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, AndroidNotificationRenderer.CONNECTION).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("BeyGram").setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).setContentIntent(NotificationIntents.settings(this)).addAction(0, getString(R.string.notif_action_stop), stopIntent).build()
    }
    companion object {
        private const val STOP = "com.beykus.beygram.STOP_BACKGROUND_NOTIFICATIONS"
        private const val EXTRA_SOURCE = "source"
        private const val STARTUP_WAKE_LOCK_MILLIS = 15_000L
        private const val DELIVERY_WAKE_LOCK_MILLIS = 60_000L
        // Set when reconciliation stops a running service, so its onDestroy records that cause instead of a platform stop.
        @Volatile private var externalStopReason: String? = null
        private fun registeredSecondaryAccounts(context: Context): List<String> =
            runCatching {
                TdAccountRegistry(context.filesDir).loadOrCreate().accountIds
                    .filter { it != TdAccountStorageLayout.LEGACY_ACCOUNT_ID }
            }.getOrDefault(emptyList())

        internal fun secondaryBackgroundRouteReady(
            route: TdSecondaryRoutePreference?,
            legacyCanStart: Boolean
        ): Boolean = secondaryColdStartRouteEligible(
            route, legacyCanStart, MihomoProxyClient.isRunning()
        )

        fun shouldRunLegacy(context: Context): Boolean =
            NotificationRuntime.get(context).preferences.let {
                it.authorized && it.state.value.enabled && it.state.value.background &&
                    AndroidNotificationRenderer.canNotify(context)
            }

        fun shouldRunSecondary(context: Context): Boolean {
            if (!AndroidNotificationRenderer.canNotify(context)) return false
            val restore = TdSecondaryRestoreStore(context)
            val runtime = NotificationRuntime.get(context)
            return registeredSecondaryAccounts(context).any { id ->
                val route = restore.confirmedRoute(id)
                runtime.preferencesForAccount(id).let { prefs ->
                    secondaryBackgroundEligible(
                        // Without an authorized background legacy route owner we
                        // cannot resolve FOLLOW_PRIMARY or start shared Mihomo.
                        // Fail closed instead of an endless foreground service.
                        routeConfirmed = secondaryColdStartRouteEligible(
                            route, shouldRunLegacy(context),
                            MihomoProxyClient.isRunning()),
                        authorized = prefs.authorized,
                        enabled = prefs.state.value.enabled,
                        background = prefs.state.value.background
                    )
                    }
            }
        }

        fun shouldRun(context: Context): Boolean = shouldRunLegacy(context) || shouldRunSecondary(context)
        /** Call only from a resumed UI or an allowed system boot/package replacement receiver; [source] goes to diagnostics. */
        fun reconcileFromAllowedContext(context: Context, source: String) {
            if (!shouldRun(context)) {
                if (NotificationRuntime.get(context).serviceRunning.value) externalStopReason = blockReason(context)
                context.stopService(Intent(context, TelegramNotificationService::class.java))
                return
            }
            try { ContextCompat.startForegroundService(context, Intent(context, TelegramNotificationService::class.java).putExtra(EXTRA_SOURCE, source)) }
            catch (_: RuntimeException) { NotificationRuntime.get(context).serviceState(false, UiText.Res(R.string.err_notif_background_start_failed)) }
        }
        private fun blockReason(context: Context): String = when {
            !NotificationRuntime.get(context).preferences.authorized && !shouldRunSecondary(context) -> "logged_out"
            !AndroidNotificationRenderer.canNotify(context) -> "permission"
            else -> "disabled"
        }
        private fun startSourceText(source: String): String = when (source) {
            "ui" -> "из приложения"
            "boot" -> "после перезагрузки телефона"
            "package_replaced" -> "после обновления приложения"
            else -> "перезапуск системой"
        }
        private fun stopReasonText(reason: String): String = when (reason) {
            "user" -> "кнопка «Остановить»"
            "disabled" -> "выключено в настройках"
            "logged_out" -> "аккаунт не авторизован"
            "permission" -> "нет разрешения на уведомления"
            "foreground_denied" -> "система не разрешила фоновую работу"
            "timeout" -> "лимит времени системы"
            "not_allowed" -> "условия работы не выполнены"
            else -> "остановлена системой"
        }
    }
}
