package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrossfadeTest {
    @Test
    fun switchPointLeavesTheOverlapBeforeTheEnd() {
        assertEquals(196_000L, crossfadeSwitchMs(200_000L, 0L))
    }

    @Test
    fun trailingSilenceMovesTheSwitchPointEarlier() {
        assertEquals(193_000L, crossfadeSwitchMs(200_000L, 3_000L))
    }

    @Test
    fun unknownTrailingSilenceCountsAsNone() {
        assertEquals(196_000L, crossfadeSwitchMs(200_000L, -1L))
    }

    @Test
    fun trackShorterThanTheOverlapHasNoSwitchPoint() {
        assertNull(crossfadeSwitchMs(CROSSFADE_MS, 0L))
        assertEquals(1L, crossfadeSwitchMs(CROSSFADE_MS + 1L, 0L))
    }

    @Test
    fun progressIsClampedToTheOverlap() {
        assertEquals(0f, crossfadeProgress(-5L), 0f)
        assertEquals(0.5f, crossfadeProgress(CROSSFADE_MS / 2), 1e-6f)
        assertEquals(1f, crossfadeProgress(CROSSFADE_MS * 3), 0f)
    }

    @Test
    fun gainsStartAndEndAtTheirLevels() {
        assertEquals(1f, outgoingGain(0f), 1e-6f)
        assertEquals(0f, incomingGain(0f), 1e-6f)
        assertEquals(0f, outgoingGain(1f), 1e-5f)
        assertEquals(1f, incomingGain(1f), 1e-5f)
    }

    @Test
    fun gainsKeepEqualPowerThroughTheOverlap() {
        for (step in 0..10) {
            val progress = step / 10f
            val power = outgoingGain(progress) * outgoingGain(progress) +
                incomingGain(progress) * incomingGain(progress)
            assertEquals(1f, power, 1e-5f)
        }
    }

    @Test
    fun checksGetFrequentNearTheSwitchPoint() {
        assertEquals(CROSSFADE_IDLE_STEP_MS, crossfadeWaitMs(60_000L))
        assertEquals(CROSSFADE_ARM_STEP_MS, crossfadeWaitMs(CROSSFADE_PREPARE_LEAD_MS))
        assertEquals(CROSSFADE_ARM_STEP_MS, crossfadeWaitMs(0L))
    }
}
