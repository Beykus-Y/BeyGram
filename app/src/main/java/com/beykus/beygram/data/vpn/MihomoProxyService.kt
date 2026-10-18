package com.beykus.beygram.data.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class ProxyRuntimeStatus { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

data class ProxyRuntimeState(val status: ProxyRuntimeStatus, val message: UiText? = null)

/**
 * Keeps Mihomo's localhost SOCKS listener alive; it does not create an Android VPN.
 *
 * Start and stop requests run one after another: each waits until the request before it has finished. A stop also
 * cancels a start that is still running, so no listener outlives a stop. The listener belongs to the process rather
 * than to a service instance, so the queue and the core are process-wide too.
 */
class MihomoProxyService : Service() {
    // Newest start id received, so a failed start can stop the service once every command so far is answered.
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_START -> startProxy()
            ACTION_STOP -> enqueueStop(startId)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        // The system can destroy the service while the process, and the native listener, keep running.
        stopForeground(STOP_FOREGROUND_REMOVE)
        enqueueStop(startId = null)
        super.onDestroy()
    }

    private fun startProxy() {
        val status = _runtimeState.value.status
        // Every start intent gets startForeground, including one that finds the listener already on its way.
        startForeground(NOTIFICATION_ID, buildNotification(textFor(status)))
        if (status == ProxyRuntimeStatus.RUNNING || status == ProxyRuntimeStatus.STARTING) return
        _runtimeState.value = ProxyRuntimeState(ProxyRuntimeStatus.STARTING)
        MihomoRuntimeDiagnostics.event("service", "start requested; profileExists=${MihomoCoreBridge(applicationContext).profileExists()}")
        startJob = enqueue { ticket -> runStart(ticket) }
    }

    private suspend fun runStart(ticket: Int) {
        try {
            coroutineContext.ensureActive()
            val bridge = MihomoCoreBridge(applicationContext)
            if (!bridge.profileExists()) throw UserMessageException(UiText.Res(R.string.err_vpn_no_profile), IllegalStateException("Сначала загрузите подписку"))
            bridge.ensureInitialized()
            MihomoRuntimeDiagnostics.event("service", "profile initialized")
            coroutineContext.ensureActive()
            // Stored before the listener starts, so a stop that arrives during the start can still close it.
            core = bridge
            bridge.startProxyListener()
            coroutineContext.ensureActive()
            withContext(Dispatchers.Main) {
                if (ticket == latestTicket) {
                    _runtimeState.value = ProxyRuntimeState(ProxyRuntimeStatus.RUNNING)
                    MihomoRuntimeDiagnostics.event("service", "state=RUNNING")
                    // "Working" is shown only after the local listener has been checked; it says nothing about upstream reachability.
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(textFor(ProxyRuntimeStatus.RUNNING)))
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // Native loading can fail with an Error, not an Exception. Leave STARTING in every case.
            runCatching { core?.stopProxyListener() }
            core = null
            MihomoRuntimeDiagnostics.failure("service start", error)
            withContext(Dispatchers.Main) {
                if (ticket == latestTicket) {
                    _runtimeState.value = ProxyRuntimeState(
                        ProxyRuntimeStatus.FAILED,
                        (error as? UserMessageException)?.text ?: UiText.Res(R.string.err_vpn_start_failed)
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    // A start that arrived meanwhile got this failure as its answer too, so stop once every command so far is answered.
                    stopSelf(lastStartId)
                }
            }
        }
    }

    /**
     * Closes the listener once the requests before this one have finished. A start that is still running is cancelled
     * first, so it cannot publish RUNNING after the stop. [startId] is null when the system destroys the service.
     */
    private fun enqueueStop(startId: Int?) {
        val status = _runtimeState.value.status
        // Set before the stop runs, so a start that arrives meanwhile sees the listener as going down and queues behind this stop.
        if (startId != null || status == ProxyRuntimeStatus.RUNNING || status == ProxyRuntimeStatus.STARTING) {
            _runtimeState.value = ProxyRuntimeState(ProxyRuntimeStatus.STOPPING)
        }
        if (startId != null) MihomoRuntimeDiagnostics.event("service", "stop requested")
        startJob?.cancel()
        enqueue { ticket ->
            runCatching { core?.stopProxyListener() }
            core = null
            withContext(Dispatchers.Main) {
                // A newer request owns the state and the foreground notification now.
                if (ticket != latestTicket) return@withContext
                // A failure stays visible until the next request, because a start caller may still be waiting for it.
                if (startId != null || _runtimeState.value.status != ProxyRuntimeStatus.FAILED) {
                    _runtimeState.value = ProxyRuntimeState(ProxyRuntimeStatus.STOPPED)
                    MihomoRuntimeDiagnostics.event("service", "state=STOPPED")
                }
                if (startId != null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        }
    }

    /** Queues [block] behind the previous request. Only the newest request, identified by its ticket, may publish state. */
    private fun enqueue(block: suspend (ticket: Int) -> Unit): Job {
        // Called on the main thread only, so the ticket and the last request need no lock.
        val ticket = ++latestTicket
        val previous = lastRequest
        return lifecycleScope.launch {
            previous?.join()
            block(ticket)
        }.also { lastRequest = it }
    }

    private fun buildNotification(@StringRes text: Int): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.proxy_notification_title))
        .setContentText(getString(text))
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    @StringRes
    private fun textFor(status: ProxyRuntimeStatus): Int =
        if (status == ProxyRuntimeStatus.RUNNING) R.string.proxy_notification_text else R.string.proxy_notification_starting

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.proxy_notification_title), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val ACTION_START = "com.beykus.beygram.mihomo_proxy.START"
        const val ACTION_STOP = "com.beykus.beygram.mihomo_proxy.STOP"
        private const val CHANNEL_ID = "beygram-mihomo-proxy"
        private const val NOTIFICATION_ID = 7012
        private val _runtimeState = MutableStateFlow(ProxyRuntimeState(ProxyRuntimeStatus.STOPPED))
        val runtimeState: StateFlow<ProxyRuntimeState> = _runtimeState.asStateFlow()

        /**
         * Forgets the failure of an earlier start. A start caller waits for RUNNING or FAILED, so an old FAILED would answer
         * it before the service has read the new intent. The caller clears it before it sends the intent.
         */
        internal fun clearFailure() {
            _runtimeState.update { state ->
                if (state.status == ProxyRuntimeStatus.FAILED) ProxyRuntimeState(ProxyRuntimeStatus.STOPPED) else state
            }
        }

        // Process-wide, like the native listener it controls. Requests are enqueued from the main thread only.
        private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var lastRequest: Job? = null
        private var latestTicket = 0
        private var startJob: Job? = null
        private var core: MihomoCoreBridge? = null
    }
}
