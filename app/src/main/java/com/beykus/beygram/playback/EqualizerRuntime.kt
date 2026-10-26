package com.beykus.beygram.playback

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class EqualizerConnection {
    /** No music session is active; settings are kept and will be applied when music starts. */
    Idle,
    Attached,
    Unsupported,
    Failed
}

data class EqualizerRuntimeState(
    val connection: EqualizerConnection = EqualizerConnection.Idle,
    val capabilities: EqualizerCapabilities? = null,
    val audioSessionId: Int? = null
)

/**
 * Session policy for the equalizer. A session is attached exactly once, reattached when the player reports a new
 * session, and released for voice/video notes (the engine reports no music session for them), stop and ENDED.
 *
 * A platform effect can fail temporarily (vendor service restart, busy audio HAL, transient binder error). A failed
 * session is therefore retried after a capped exponential backoff instead of becoming a permanent no-op until the
 * next audio session. [nowMs] is injectable so the retry policy has deterministic JVM tests.
 */
internal class EqualizerSessionController(
    private val effects: EqualizerEffectFactory,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private val controller = EqualizerController(effects)
    private var targetSessionId: Int? = null
    private var attachedSessionId: Int? = null
    private var lastCapabilities: EqualizerCapabilities? = null
    private var retryAttempt = 0
    private var nextRetryAtMs = 0L

    var state: EqualizerRuntimeState = EqualizerRuntimeState()
        private set

    fun onSession(audioSessionId: Int?, settings: EqualizerSettings) {
        if (audioSessionId == null) {
            if (targetSessionId != null) {
                controller.release()
                targetSessionId = null
                attachedSessionId = null
            }
            resetRetry()
            // Keep the last band layout while music is merely paused or interrupted. The screen can still show the
            // real controls and explain that the curve will be applied when music returns.
            state = EqualizerRuntimeState(EqualizerConnection.Idle, lastCapabilities, null)
            return
        }

        val sessionChanged = targetSessionId != audioSessionId
        if (sessionChanged) {
            targetSessionId = audioSessionId
            attachedSessionId = null
            resetRetry()
        }

        if (attachedSessionId != audioSessionId) {
            // Unsupported is a stable device capability: retrying the same session would only hammer the platform.
            // Failed is transient, so honour the backoff and try again on the next settings event or timer tick.
            if (!sessionChanged) {
                if (state.connection == EqualizerConnection.Unsupported) return
                if (state.connection == EqualizerConnection.Failed && nowMs() < nextRetryAtMs) return
            }
            when (val result = controller.attach(audioSessionId)) {
                is EqualizerAttachResult.Attached -> {
                    attachedSessionId = audioSessionId
                    lastCapabilities = result.capabilities
                }
                EqualizerAttachResult.Unsupported -> {
                    lastCapabilities = null
                    state = EqualizerRuntimeState(EqualizerConnection.Unsupported, null, null)
                    return
                }
                EqualizerAttachResult.Failed -> {
                    failAndScheduleRetry()
                    return
                }
            }
        }

        if (attachedSessionId != audioSessionId) return
        if (!controller.apply(settings)) {
            controller.release()
            attachedSessionId = null
            lastCapabilities = null
            failAndScheduleRetry()
            return
        }
        resetRetry()
        state = EqualizerRuntimeState(
            connection = EqualizerConnection.Attached,
            capabilities = controller.capabilities,
            audioSessionId = audioSessionId
        )
    }

    /** Remaining delay before the same session may be retried, or null when no retry is pending. */
    fun nextRetryDelayMs(): Long? {
        if (state.connection != EqualizerConnection.Failed || targetSessionId == null) return null
        return (nextRetryAtMs - nowMs()).coerceAtLeast(0L)
    }

    fun close() {
        controller.release()
        targetSessionId = null
        attachedSessionId = null
        lastCapabilities = null
        resetRetry()
        state = EqualizerRuntimeState()
    }

    private fun failAndScheduleRetry() {
        lastCapabilities = null
        retryAttempt = (retryAttempt + 1).coerceAtMost(MAX_RETRY_ATTEMPT)
        nextRetryAtMs = nowMs() + retryDelayMs(retryAttempt)
        state = EqualizerRuntimeState(EqualizerConnection.Failed, null, null)
    }

    private fun resetRetry() {
        retryAttempt = 0
        nextRetryAtMs = 0L
    }
}

/** 1 s, 2 s, 4 s ... capped at 30 s. A platform service that keeps failing is not retried on every slider frame. */
internal fun retryDelayMs(attempt: Int): Long {
    val shift = (attempt - 1).coerceIn(0, MAX_RETRY_ATTEMPT - 1)
    return (RETRY_BASE_DELAY_MS * (1L shl shift)).coerceAtMost(RETRY_MAX_DELAY_MS)
}

private const val RETRY_BASE_DELAY_MS = 1_000L
private const val RETRY_MAX_DELAY_MS = 30_000L
private const val MAX_RETRY_ATTEMPT = 6

/**
 * Process-wide bridge between the player's music session and the equalizer effect. [start] is called by
 * [PlaybackService] while the foreground session exists; [stop] releases the native effect when that service dies.
 */
object EqualizerRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(EqualizerRuntimeState())
    val state: StateFlow<EqualizerRuntimeState> = _state.asStateFlow()

    private var job: Job? = null
    private var retryJob: Job? = null
    private var sessionController: EqualizerSessionController? = null

    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true) return
        job?.cancel()
        retryJob?.cancel()
        retryJob = null
        sessionController?.close()
        val store = EqualizerStore.get(context.applicationContext)
        val controller = EqualizerSessionController(PlatformEqualizerEffectFactory)
        sessionController = controller
        job = scope.launch {
            combine(PlaybackEngine.audioSessionId, store.state) { sessionId, settings -> sessionId to settings }
                .collect { (sessionId, settings) ->
                    controller.onSession(sessionId, settings)
                    publish(controller, store)
                }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        retryJob?.cancel()
        retryJob = null
        sessionController?.close()
        sessionController = null
        _state.value = EqualizerRuntimeState()
    }

    private fun publish(controller: EqualizerSessionController, store: EqualizerStore) {
        _state.value = controller.state
        scheduleRetry(controller, store)
    }

    /** A transient failed effect is retried even without another settings emission, with the controller's backoff. */
    private fun scheduleRetry(controller: EqualizerSessionController, store: EqualizerStore) {
        retryJob?.cancel()
        retryJob = null
        val delayMs = controller.nextRetryDelayMs() ?: return
        retryJob = scope.launch {
            delay(delayMs.coerceAtLeast(1L))
            controller.onSession(PlaybackEngine.audioSessionId.value, store.state.value)
            publish(controller, store)
        }
    }
}
