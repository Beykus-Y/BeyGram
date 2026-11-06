package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackCrossfaderStopTest {
    /** One check of the ramp: what the primary player reports at that moment. */
    private data class Check(
        val playerError: Boolean = false,
        val playWhenReady: Boolean = true,
        val suppressed: Boolean = false
    )

    /** Index of the first check that stops the overlap, or null when the ramp runs to the end. */
    private fun firstStop(checks: List<Check>): Int? =
        checks.indexOfFirst { crossfadeMustStop(it.playerError, it.playWhenReady, it.suppressed) }.takeIf { it >= 0 }

    @Test
    fun aTransientFocusLossStopsTheOverlapAtThatCheck() {
        // Media3 keeps playWhenReady set while the output is suppressed, so only the suppression shows the loss.
        val checks = listOf(Check(), Check(), Check(suppressed = true), Check(suppressed = true))
        assertEquals(2, firstStop(checks))
    }

    @Test
    fun focusComingBackDoesNotRestartAnOverlapThatStopped() {
        // The ramp stops at the first check. Later checks, with focus back, are never reached.
        val checks = listOf(Check(suppressed = true), Check(), Check())
        assertEquals(0, firstStop(checks))
    }

    @Test
    fun anOrdinaryRampRunsToTheEnd() {
        assertNull(firstStop(List(10) { Check() }))
    }

    @Test
    fun aPlayerErrorOrAPauseStopsTheOverlap() {
        assertEquals(1, firstStop(listOf(Check(), Check(playerError = true))))
        assertEquals(0, firstStop(listOf(Check(playWhenReady = false))))
    }
}
