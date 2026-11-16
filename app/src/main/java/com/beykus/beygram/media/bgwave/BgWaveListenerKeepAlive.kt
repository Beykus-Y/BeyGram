package com.beykus.beygram.media.bgwave

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Which wave states need [BgWaveListenerService]. Only the listener keeps a foreground service: the host plays through
 * the music player, which already runs its own.
 *
 * Connecting, Live and Reconnecting are the states in which a listener's wave is still running, so the process must
 * survive a locked screen. Failed is left out: nothing plays and the ticker has stopped. Retry moves the wave back to
 * Reconnecting, and that start comes from the UI, which is in the foreground. Ended and no session mean the wave is over.
 * Offering and AwaitingAnswer exist only for the host, so a listener never reaches them. The `when` has no else branch,
 * so a new phase must be decided here.
 */
internal object BgWaveListenerKeepAlivePolicy {
    fun shouldKeepAlive(state: BgWaveSessionState?): Boolean {
        if (state == null || state.role != BgWaveRole.Listener) return false
        return when (state.phase) {
            BgWavePhase.Connecting, BgWavePhase.Live, BgWavePhase.Reconnecting -> true
            BgWavePhase.Offering, BgWavePhase.AwaitingAnswer, BgWavePhase.Failed, BgWavePhase.Ended -> false
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
            BgWaveHub.controller.state
                .map { BgWaveListenerKeepAlivePolicy.shouldKeepAlive(it) }
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
