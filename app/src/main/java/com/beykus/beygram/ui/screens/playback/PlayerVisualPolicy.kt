package com.beykus.beygram.ui.screens.playback

import com.beykus.beygram.data.canvas.BgCanvasMode
import com.beykus.beygram.data.canvas.CanvasLoadState

/**
 * Actual rendering state of a [BgCanvasOverlay] decoder/surface.
 * [CanvasLoadState.Ready] only means the local MP4 file is ready; [DisplayingFirstFrame] is
 * reported only after `onRenderedFirstFrame` fires.
 */
enum class CanvasVisualState {
    Inactive,
    Preparing,
    DisplayingFirstFrame,
    Failed,
}

data class PlayerBackdropPolicy(
    val fullscreenCanvasVisible: Boolean,
    val fullscreenCanvasDisplaying: Boolean,
    val fullscreenScrimAlpha: Float,
    val waveAlpha: Float,
    val waveAnimate: Boolean,
    val rhythmGlowScale: Float,
)

/**
 * Single source of truth coordinating fullscreen/card BG Canvas, decorative Wave Visuals 2.0,
 * and BG Rhythm backdrop glow so competing visual layers never stack simultaneously.
 */
internal fun resolvePlayerBackdropPolicy(
    waveEligible: Boolean,
    canvasMode: BgCanvasMode,
    canvasLoadState: CanvasLoadState,
    canvasVisualState: CanvasVisualState,
    panel: PlayerPanel,
    playing: Boolean,
    resumed: Boolean,
    reducedMotion: Boolean,
    mediaMotionAllowed: Boolean,
    powerSave: Boolean,
): PlayerBackdropPolicy {
    val onPlayerPanel = panel == PlayerPanel.Player
    val fullscreenCanvasVisible =
        onPlayerPanel &&
            canvasMode == BgCanvasMode.Fullscreen &&
            canvasLoadState is CanvasLoadState.Ready &&
            canvasVisualState != CanvasVisualState.Failed

    val fullscreenCanvasDisplaying =
        fullscreenCanvasVisible && canvasVisualState == CanvasVisualState.DisplayingFirstFrame

    val fullscreenScrimAlpha = if (fullscreenCanvasDisplaying) 0.48f else 0f

    val cardCanvasDisplaying =
        onPlayerPanel &&
            canvasMode == BgCanvasMode.Card &&
            canvasLoadState is CanvasLoadState.Ready &&
            canvasVisualState == CanvasVisualState.DisplayingFirstFrame

    val waveAlpha = when {
        !waveEligible || !onPlayerPanel || fullscreenCanvasDisplaying -> 0f
        cardCanvasDisplaying -> 0.32f
        else -> 0.78f
    }

    val waveAnimate =
        waveAlpha > 0f &&
            playing &&
            resumed &&
            !reducedMotion &&
            mediaMotionAllowed &&
            !powerSave

    val rhythmGlowScale = when {
        !onPlayerPanel || fullscreenCanvasDisplaying -> 0f
        waveAlpha > 0f -> 0.45f
        else -> 1f
    }

    return PlayerBackdropPolicy(
        fullscreenCanvasVisible = fullscreenCanvasVisible,
        fullscreenCanvasDisplaying = fullscreenCanvasDisplaying,
        fullscreenScrimAlpha = fullscreenScrimAlpha,
        waveAlpha = waveAlpha,
        waveAnimate = waveAnimate,
        rhythmGlowScale = rhythmGlowScale,
    )
}
