package com.beykus.beygram.ui.screens.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
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
 * Produces a [BgRhythmFrame] state driven by the shared [BgBeatManager] analysis and [PlaybackEngine] clock.
 * When [BgRhythmMode.Off], paused, reduced motion, or unanalyzed, no frame loop runs (zero CPU/GPU overhead).
 * Callers must read the returned [State.value] only inside draw/graphicsLayer lambdas to avoid recomposition.
 */
@Composable
internal fun rememberBgRhythmFrame(
    item: PlaybackItem,
    playing: Boolean,
    speed: Float,
    fallbackDurationMs: Long,
): State<BgRhythmFrame> {
    val context = LocalContext.current
    val modeFlow = remember(context) { BgRhythmSettingsStore.flow(context) }
    val mode by modeFlow.collectAsStateWithLifecycle()
    val reducedMotion = rememberSystemReducedMotion()
    val motion = LocalMotionSettings.current
    val canAnimate = mode != BgRhythmMode.Off &&
        playing &&
        !reducedMotion &&
        motion.enabled &&
        MotionCategory.Media !in motion.disabledCategories

    val frameState = remember(item.key) { mutableStateOf(BgRhythmFrame.IDLE) }

    if (!canAnimate) {
        if (frameState.value != BgRhythmFrame.IDLE) {
            frameState.value = BgRhythmFrame.IDLE
        }
        return frameState
    }

    val beatManager = remember(context) { BgBeatManager.getInstance(context) }
    val beatUiState by beatManager.uiState.collectAsStateWithLifecycle()
    val analysis = (beatUiState as? BgBeatUiState.Ready)
        ?.takeIf { it.itemKey == item.key && it.accountId == item.accountId }
        ?.analysis

    if (analysis == null) {
        if (frameState.value != BgRhythmFrame.IDLE) {
            frameState.value = BgRhythmFrame.IDLE
        }
        return frameState
    }

    val pos = rememberPlaybackPositionUi()
    val duration = pos.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
    val smoothPos = rememberSmoothPositionMs(pos.positionMs, duration, playing, speed)

    LaunchedEffect(item.key, analysis, mode) {
        while (true) {
            withFrameNanos {
                frameState.value = BgRhythmEvaluator.evaluate(
                    analysis = analysis,
                    positionMs = smoothPos.value,
                    playing = true,
                    mode = mode,
                    reducedMotion = false,
                )
            }
        }
    }

    return frameState
}
