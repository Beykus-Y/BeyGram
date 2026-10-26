package com.beykus.beygram.playback

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
 */
internal class EqualizerSessionController(private val effects: EqualizerEffectFactory) {
    private val controller = EqualizerController(effects)
    private var targetSessionId: Int? = null
    private var attachedSessionId: Int? = null
    private var lastCapabilities: EqualizerCapabilities? = null

    var state: EqualizerRuntimeState = EqualizerRuntimeState()
        private set

    fun onSession(audioSessionId: Int?, settings: EqualizerSettings) {
        if (audioSessionId == null) {
            if (targetSessionId != null) {
                controller.release()
                targetSessionId = null
                attachedSessionId = null
            }
            // Keep the last band layout while music is merely paused or interrupted. The screen can still show the
            // real controls and explain that the curve will be applied when music returns.
            state = EqualizerRuntimeState(EqualizerConnection.Idle, lastCapabilities, null)
            return
        }

        if (targetSessionId != audioSessionId) {
            targetSessionId = audioSessionId
            attachedSessionId = null
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
                    lastCapabilities = null
                    state = EqualizerRuntimeState(EqualizerConnection.Failed, null, null)
                    return
                }
            }
        }

        if (attachedSessionId != audioSessionId) return
        if (!controller.apply(settings)) {
            controller.release()
            attachedSessionId = null
            lastCapabilities = null
            state = EqualizerRuntimeState(EqualizerConnection.Failed, null, null)
            return
        }
        state = EqualizerRuntimeState(
            connection = EqualizerConnection.Attached,
            capabilities = controller.capabilities,
            audioSessionId = audioSessionId
        )
    }

    fun close() {
        controller.release()
        targetSessionId = null
        attachedSessionId = null
        lastCapabilities = null
        state = EqualizerRuntimeState()
    }
}

/**
 * Process-wide bridge between the player's music session and the equalizer effect. [start] is called by
 * [PlaybackService] while the foreground session exists; [stop] releases the native effect when that service dies.
 */
object EqualizerRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(EqualizerRuntimeState())
    val state: StateFlow<EqualizerRuntimeState> = _state.asStateFlow()

    private var job: Job? = null
    private var sessionController: EqualizerSessionController? = null

    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true) return
        job?.cancel()
        sessionController?.close()
        val store = EqualizerStore.get(context.applicationContext)
        val controller = EqualizerSessionController(PlatformEqualizerEffectFactory)
        sessionController = controller
        job = scope.launch {
            combine(PlaybackEngine.audioSessionId, store.state) { sessionId, settings -> sessionId to settings }
                .collect { (sessionId, settings) ->
                    controller.onSession(sessionId, settings)
                    _state.value = controller.state
                }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        sessionController?.close()
        sessionController = null
        _state.value = EqualizerRuntimeState()
    }
}
