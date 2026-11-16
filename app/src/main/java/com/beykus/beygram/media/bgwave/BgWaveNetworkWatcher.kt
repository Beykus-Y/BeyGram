package com.beykus.beygram.media.bgwave

import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Pure decision of when a default-network change is answered with an automatic reconnect. */
internal object BgWaveNetworkRetryPolicy {
    const val DEBOUNCE_MILLIS = 2_000L
    const val WINDOW_MILLIS = 30_000L

    /** Phases in which the watcher is registered: a session exists and may still need to survive a network change. */
    fun isWatched(state: BgWaveSessionState?): Boolean = when (state?.phase) {
        BgWavePhase.Connecting, BgWavePhase.Live, BgWavePhase.Reconnecting, BgWavePhase.Failed -> true
        else -> false
    }

    /**
     * True when the session should be retried now: a network change happened at [changeAtMillis], the debounce has passed,
     * the change is still recent, it was not retried yet and the session is failed or already reconnecting.
     * A Live or Connecting session is left alone: it may survive the switch by itself.
     */
    fun shouldRetry(
        phase: BgWavePhase?,
        changeAtMillis: Long?,
        retriedForChange: Boolean,
        nowMillis: Long,
    ): Boolean {
        if (changeAtMillis == null || retriedForChange) return false
        if (phase != BgWavePhase.Failed && phase != BgWavePhase.Reconnecting) return false
        val elapsed = nowMillis - changeAtMillis
        return elapsed >= DEBOUNCE_MILLIS && elapsed <= WINDOW_MILLIS
    }

    /** True when [nextId] is a different default network than [previousId], or the network returned after being lost. */
    fun isChange(previousId: Long?, nextId: Long, wasLost: Boolean): Boolean =
        (previousId != null && previousId != nextId) || (previousId == null && wasLost)
}

/**
 * While a wave session exists, listens for the default network switching (Wi-Fi to mobile and back) and, if the session
 * is failed or reconnecting after the switch, calls [retry] once per change. [retry] is the same path as the user's
 * "Повторить" button, so it only works for what the native session's reconnect supports (the host cannot send a new
 * OFFER from here; if reconnect is refused the session stays Failed and the user retries by hand).
 */
internal class BgWaveNetworkWatcher(
    private val manager: ConnectivityManager,
    private val scope: CoroutineScope,
    private val state: StateFlow<BgWaveSessionState?>,
    private val retry: () -> Boolean,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private var registered = false
    private var lastNetwork: Network? = null
    private var lost = false
    private var retryJob: Job? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val changed = synchronized(lock) {
                val result = BgWaveNetworkRetryPolicy.isChange(
                    lastNetwork?.networkHandle,
                    network.networkHandle,
                    lost,
                )
                lastNetwork = network
                lost = false
                result
            }
            if (changed) onNetworkChanged()
        }

        override fun onLost(network: Network) {
            synchronized(lock) {
                if (lastNetwork == network) {
                    lastNetwork = null
                    lost = true
                }
            }
        }
    }

    fun start() {
        scope.launch {
            state.collectLatest { current -> setRegistered(BgWaveNetworkRetryPolicy.isWatched(current)) }
        }
    }

    private fun setRegistered(want: Boolean) {
        synchronized(lock) {
            if (want == registered) return
            if (want) {
                try {
                    lastNetwork = null
                    lost = false
                    manager.registerDefaultNetworkCallback(callback)
                    registered = true
                } catch (e: RuntimeException) {
                    BgWaveLog.w("network.watch.register", e)
                }
            } else {
                try {
                    manager.unregisterNetworkCallback(callback)
                } catch (_: RuntimeException) {
                    // Already unregistered.
                }
                registered = false
                retryJob?.cancel()
                retryJob = null
            }
        }
    }

    private fun onNetworkChanged() {
        BgWaveLog.i("network.changed")
        val changeAt = clock()
        synchronized(lock) {
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(BgWaveNetworkRetryPolicy.DEBOUNCE_MILLIS)
                while (true) {
                    val now = clock()
                    if (BgWaveNetworkRetryPolicy.shouldRetry(state.value?.phase, changeAt, false, now)) {
                        BgWaveLog.i("network.retry")
                        retry()
                        return@launch
                    }
                    if (now - changeAt > BgWaveNetworkRetryPolicy.WINDOW_MILLIS) return@launch
                    delay(POLL_MILLIS)
                }
            }
        }
    }

    private companion object {
        const val POLL_MILLIS = 500L
    }
}
