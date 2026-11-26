package com.beykus.beygram.ui.screens.playback

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.media.bgbeat.BgBeatManager
import com.beykus.beygram.media.bgbeat.BgBeatUiState
import com.beykus.beygram.media.bgrhythm.BgRhythmEvaluator
import com.beykus.beygram.media.bgrhythm.BgRhythmFrame
import com.beykus.beygram.media.bgrhythm.BgRhythmMode
import com.beykus.beygram.media.bgrhythm.BgRhythmSettingsStore
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.ui.accessibility.rememberSystemReducedMotion
import com.beykus.beygram.ui.motion.LocalMotionSettings
import com.beykus.beygram.ui.motion.MotionCategory

/**
 * Mutable, zero-allocation holder of live BG Rhythm values updated on animation frames.
 * Read fields only inside draw / graphicsLayer lambdas so Compose never recomposes per frame.
 */
@Stable
internal class BgRhythmSignal {
    var beatPulse: Float by mutableFloatStateOf(0f)
    var downbeatPulse: Float by mutableFloatStateOf(0f)
    var phraseEnergy: Float by mutableFloatStateOf(0f)
    var waveformLevel: Float by mutableFloatStateOf(0f)
    var isActive: Boolean by mutableStateOf(false)

    fun apply(frame: BgRhythmFrame) {
        if (beatPulse != frame.beatPulse) beatPulse = frame.beatPulse
        if (downbeatPulse != frame.downbeatPulse) downbeatPulse = frame.downbeatPulse
        if (phraseEnergy != frame.phraseEnergy) phraseEnergy = frame.phraseEnergy
        if (waveformLevel != frame.waveformLevel) waveformLevel = frame.waveformLevel
        if (isActive != frame.isActive) isActive = frame.isActive
    }

    fun resetIdle() = apply(BgRhythmFrame.IDLE)
}

/**
 * Evaluates BG Rhythm from the shared [BgBeatManager] analysis and [PlaybackEngine] clock
 * using a single `withFrameNanos` loop when [active], [playing], resumed, analyzed, and enabled.
 * Zero frame loops or subscriptions run when [BgRhythmMode.Off], paused, backgrounded, battery saver, or off-panel.
 */
@Composable
internal fun rememberBgRhythmSignal(
    item: PlaybackItem,
    playing: Boolean,
    speed: Float,
    fallbackDurationMs: Long,
    active: Boolean,
): BgRhythmSignal {
    val context = LocalContext.current
    val modeFlow = remember(context) { BgRhythmSettingsStore.flow(context) }
    val mode by modeFlow.collectAsStateWithLifecycle()
    val reducedMotion = rememberSystemReducedMotion()
    val motion = LocalMotionSettings.current

    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val powerSave = rememberPowerSaveMode(resumed)

    val signal = remember(item.key) { BgRhythmSignal() }

    val canRun = active &&
        resumed &&
        !powerSave &&
        mode != BgRhythmMode.Off &&
        playing &&
        !reducedMotion &&
        motion.enabled &&
        MotionCategory.Media !in motion.disabledCategories

    if (!canRun) {
        signal.resetIdle()
        return signal
    }

    val beatManager = remember(context) { BgBeatManager.getInstance(context) }
    val beatUiState by beatManager.uiState.collectAsStateWithLifecycle()
    val analysis = (beatUiState as? BgBeatUiState.Ready)
        ?.takeIf { it.itemKey == item.key && it.accountId == item.accountId }
        ?.analysis

    if (analysis == null) {
        signal.resetIdle()
        return signal
    }

    LaunchedEffect(item.key, analysis, mode, speed) {
        var anchorPosMs = PlaybackEngine.state.value.positionMs
        var anchorUptimeMs = SystemClock.uptimeMillis()
        while (true) {
            withFrameNanos {
                val engineState = PlaybackEngine.state.value
                if (engineState.item?.key != item.key || !engineState.playing) {
                    signal.resetIdle()
                    return@withFrameNanos
                }
                val nowUptime = SystemClock.uptimeMillis()
                if (engineState.positionMs != anchorPosMs) {
                    anchorPosMs = engineState.positionMs
                    anchorUptimeMs = nowUptime
                }
                val elapsedMs = ((nowUptime - anchorUptimeMs).coerceAtLeast(0L) * speed).toLong()
                val duration = engineState.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
                val effectivePosMs = if (duration > 0L) {
                    (anchorPosMs + elapsedMs).coerceIn(0L, duration)
                } else {
                    anchorPosMs + elapsedMs
                }
                signal.apply(
                    BgRhythmEvaluator.evaluate(
                        analysis = analysis,
                        positionMs = effectivePosMs,
                        playing = true,
                        mode = mode,
                        reducedMotion = false,
                    )
                )
            }
        }
    }

    return signal
}
