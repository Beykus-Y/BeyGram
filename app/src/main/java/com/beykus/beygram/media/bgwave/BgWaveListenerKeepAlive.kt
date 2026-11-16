package com.beykus.beygram.media.bgwave

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Which wave states need [BgWaveListenerService]. Only the listener keeps a foreground service: the host plays through
 * the music player, which already runs its own.
 *
 * Connecting, Live and Reconnecting are the states in which a listener's wave is still running, so the process must
 * survive a locked screen. A Failed wave that lost its connection ([BgWaveErrors.NO_CONNECTION]) is kept for
 * [RETRY_GRACE_MILLIS] too: [BgWaveNetworkWatcher] retries it by itself when the network comes back, possibly with the
 * screen off, and a foreground start from the background is refused on Android 12+, so the retry has to happen under the
 * service that is already running. After the grace the service stops, so a wave nobody returns to does not hold it forever.
 * Other failures (rejected, send failed) cannot be cured by a network change and do not keep it. Ended and no session mean
 * the wave is over. Offering and AwaitingAnswer exist only for the host, so a listener never reaches them. The `when` has
 * no else branch, so a new phase must be decided here.
 */
internal object BgWaveListenerKeepAlivePolicy {
    const val RETRY_GRACE_MILLIS = 2L * 60 * 1000

    enum class Need { Running, RetryPending, None }

    fun need(state: BgWaveSessionState?): Need {
        if (state == null || state.role != BgWaveRole.Listener) return Need.None
        return when (state.phase) {
            BgWavePhase.Connecting, BgWavePhase.Live, BgWavePhase.Reconnecting -> Need.Running
            BgWavePhase.Failed -> if (state.errorKey == BgWaveErrors.NO_CONNECTION) Need.RetryPending else Need.None
            BgWavePhase.Offering, BgWavePhase.AwaitingAnswer, BgWavePhase.Ended -> Need.None
        }
    }

    /** True while the service is wanted, ignoring the grace timer of a failed wave. */
    fun shouldKeepAlive(state: BgWaveSessionState?): Boolean = need(state) != Need.None

    /** [shouldKeepAlive] over time: a failed wave keeps the service for [RETRY_GRACE_MILLIS], then it is no longer wanted. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun keepAlive(state: Flow<BgWaveSessionState?>): Flow<Boolean> =
        state.map { need(it) }
            .distinctUntilChanged()
            .transformLatest { need ->
                when (need) {
                    Need.Running -> emit(true)
                    Need.RetryPending -> {
                        emit(true)
                        delay(RETRY_GRACE_MILLIS)
                        emit(false)
                    }
                    Need.None -> emit(false)
                }
            }
}

/**
 * Starts [BgWaveListenerService] when a listener's wave starts running. The service stops itself when the wave no longer
 * needs it, so a quick start-then-stop can never stop it before it has entered the foreground.
 * Installed once from the application, so it watches the wave for the whole life of the process.
 */
object BgWaveListenerKeepAlive {
    private val installed = AtomicBoolean(false)

    /** Only the first call starts the observer; later calls do nothing. */
    fun install(context: Context, scope: CoroutineScope) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            BgWaveListenerKeepAlivePolicy.keepAlive(BgWaveHub.controller.state)
                .distinctUntilChanged()
                .collect { keepAlive -> if (keepAlive) start(appContext) }
        }
    }

    // Android 12+ refuses a foreground start from the background. The refusal is logged, never thrown into the wave.
    private fun start(context: Context) {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, BgWaveListenerService::class.java)) }
            .onFailure { BgWaveLog.w("listener.keepalive.start", it) }
    }
}
