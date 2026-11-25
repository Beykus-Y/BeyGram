package com.beykus.beygram.ui.screens.playback

import com.beykus.beygram.data.canvas.BgCanvasMode
import com.beykus.beygram.data.canvas.CanvasAsset
import com.beykus.beygram.data.canvas.CanvasLoadState
import com.beykus.beygram.data.canvas.CanvasUnavailableReason
import com.beykus.beygram.ui.screens.music.trackWaveSeedFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerVisualPolicyTest {

    private val readyCanvas = CanvasLoadState.Ready(
        CanvasAsset(
            playbackKey = "ym:1",
            localFilePath = "/tmp/canvas.mp4",
            videoId = "v1",
            sizeBytes = 1024L,
        )
    )

    @Test
    fun fullscreenCanvasHidesWavesOnlyAfterFirstFrame() {
        val preparing = resolvePlayerBackdropPolicy(
            waveEligible = true,
            canvasMode = BgCanvasMode.Fullscreen,
            canvasLoadState = readyCanvas,
            canvasVisualState = CanvasVisualState.Preparing,
            panel = PlayerPanel.Player,
            playing = true,
            resumed = true,
            reducedMotion = false,
            mediaMotionAllowed = true,
            powerSave = false,
        )
        assertTrue(preparing.fullscreenCanvasVisible)
        assertFalse(preparing.fullscreenCanvasDisplaying)
        assertEquals(0f, preparing.fullscreenScrimAlpha, 0.001f)
        assertTrue(preparing.waveAlpha > 0f)
        assertTrue(preparing.waveAnimate)

        val displaying = resolvePlayerBackdropPolicy(
            waveEligible = true,
            canvasMode = BgCanvasMode.Fullscreen,
            canvasLoadState = readyCanvas,
            canvasVisualState = CanvasVisualState.DisplayingFirstFrame,
            panel = PlayerPanel.Player,
            playing = true,
            resumed = true,
            reducedMotion = false,
            mediaMotionAllowed = true,
            powerSave = false,
        )
        assertTrue(displaying.fullscreenCanvasVisible)
        assertTrue(displaying.fullscreenCanvasDisplaying)
        assertEquals(0.48f, displaying.fullscreenScrimAlpha, 0.001f)
        assertEquals(0f, displaying.waveAlpha, 0.001f)
        assertFalse(displaying.waveAnimate)
        assertEquals(0f, displaying.rhythmGlowScale, 0.001f)
    }

    @Test
    fun canvasFailureOrMissingVideoRestoresWaves() {
        val failed = resolvePlayerBackdropPolicy(
            waveEligible = true,
            canvasMode = BgCanvasMode.Fullscreen,
            canvasLoadState = readyCanvas,
            canvasVisualState = CanvasVisualState.Failed,
            panel = PlayerPanel.Player,
            playing = true,
            resumed = true,
            reducedMotion = false,
            mediaMotionAllowed = true,
            powerSave = false,
        )
        assertFalse(failed.fullscreenCanvasVisible)
        assertEquals(0.78f, failed.waveAlpha, 0.001f)
        assertTrue(failed.waveAnimate)

        val unavailable = resolvePlayerBackdropPolicy(
            waveEligible = true,
            canvasMode = BgCanvasMode.Fullscreen,
            canvasLoadState = CanvasLoadState.Unavailable(CanvasUnavailableReason.NotAvailable),
            canvasVisualState = CanvasVisualState.Inactive,
            panel = PlayerPanel.Player,
            playing = true,
            resumed = true,
            reducedMotion = false,
            mediaMotionAllowed = true,
            powerSave = false,
        )
        assertFalse(unavailable.fullscreenCanvasVisible)
        assertEquals(0.78f, unavailable.waveAlpha, 0.001f)
    }

    @Test
    fun cardCanvasAllowsSubtleWavesAndRespectsPauseLifecycleMotionAndPowerSave() {
        val cardActive = resolvePlayerBackdropPolicy(
            waveEligible = true,
            canvasMode = BgCanvasMode.Card,
            canvasLoadState = readyCanvas,
            canvasVisualState = CanvasVisualState.DisplayingFirstFrame,
            panel = PlayerPanel.Player,
            playing = true,
            resumed = true,
            reducedMotion = false,
            mediaMotionAllowed = true,
            powerSave = false,
        )
        assertFalse(cardActive.fullscreenCanvasVisible)
        assertEquals(0.32f, cardActive.waveAlpha, 0.001f)
        assertTrue(cardActive.waveAnimate)

        // Pause, background, reducedMotion, mediaMotionDisabled, powerSave all stop animation
        assertFalse(
            resolvePlayerBackdropPolicy(
                waveEligible = true,
                canvasMode = BgCanvasMode.Off,
                canvasLoadState = CanvasLoadState.Idle,
                canvasVisualState = CanvasVisualState.Inactive,
                panel = PlayerPanel.Player,
                playing = false,
                resumed = true,
                reducedMotion = false,
                mediaMotionAllowed = true,
                powerSave = false,
            ).waveAnimate
        )
        assertFalse(
            resolvePlayerBackdropPolicy(
                waveEligible = true,
                canvasMode = BgCanvasMode.Off,
                canvasLoadState = CanvasLoadState.Idle,
                canvasVisualState = CanvasVisualState.Inactive,
                panel = PlayerPanel.Player,
                playing = true,
                resumed = false,
                reducedMotion = false,
                mediaMotionAllowed = true,
                powerSave = false,
            ).waveAnimate
        )
        assertFalse(
            resolvePlayerBackdropPolicy(
                waveEligible = true,
                canvasMode = BgCanvasMode.Off,
                canvasLoadState = CanvasLoadState.Idle,
                canvasVisualState = CanvasVisualState.Inactive,
                panel = PlayerPanel.Player,
                playing = true,
                resumed = true,
                reducedMotion = true,
                mediaMotionAllowed = true,
                powerSave = false,
            ).waveAnimate
        )
        assertFalse(
            resolvePlayerBackdropPolicy(
                waveEligible = true,
                canvasMode = BgCanvasMode.Off,
                canvasLoadState = CanvasLoadState.Idle,
                canvasVisualState = CanvasVisualState.Inactive,
                panel = PlayerPanel.Player,
                playing = true,
                resumed = true,
                reducedMotion = false,
                mediaMotionAllowed = false,
                powerSave = false,
            ).waveAnimate
        )
        assertFalse(
            resolvePlayerBackdropPolicy(
                waveEligible = true,
                canvasMode = BgCanvasMode.Off,
                canvasLoadState = CanvasLoadState.Idle,
                canvasVisualState = CanvasVisualState.Inactive,
                panel = PlayerPanel.Player,
                playing = true,
                resumed = true,
                reducedMotion = false,
                mediaMotionAllowed = true,
                powerSave = true,
            ).waveAnimate
        )
    }

    @Test
    fun lyricsOrQueuePanelDisablesWavesAndFullscreenCanvas() {
        val lyrics = resolvePlayerBackdropPolicy(
            waveEligible = true,
            canvasMode = BgCanvasMode.Fullscreen,
            canvasLoadState = readyCanvas,
            canvasVisualState = CanvasVisualState.DisplayingFirstFrame,
            panel = PlayerPanel.Lyrics,
            playing = true,
            resumed = true,
            reducedMotion = false,
            mediaMotionAllowed = true,
            powerSave = false,
        )
        assertFalse(lyrics.fullscreenCanvasVisible)
        assertEquals(0f, lyrics.waveAlpha, 0.001f)
        assertEquals(0f, lyrics.rhythmGlowScale, 0.001f)
    }

    @Test
    fun trackWaveSeedIsDeterministicAndDistinctAcrossTracks() {
        val a1 = trackWaveSeedFor("ym:1001")
        val a2 = trackWaveSeedFor("ym:1001")
        val b = trackWaveSeedFor("ym:2002")
        assertEquals(a1, a2)
        assertNotEquals(a1, b)
    }
}
