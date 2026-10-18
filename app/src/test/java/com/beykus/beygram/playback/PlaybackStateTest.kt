package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStateTest {
    @Test
    fun fractionStaysZeroUntilDurationIsKnown() {
        assertEquals(0f, PlaybackState(positionMs = 5_000L).fraction, 0f)
    }

    @Test
    fun fractionFollowsPositionAndStaysInRange() {
        assertEquals(0.25f, PlaybackState(positionMs = 1_000L, durationMs = 4_000L).fraction, 0.0001f)
        assertEquals(1f, PlaybackState(positionMs = 9_000L, durationMs = 4_000L).fraction, 0f)
    }

    @Test
    fun speedLabelDropsZeroOnlyForWholeNumbers() {
        assertEquals("1", speedLabel(1f))
        assertEquals("2", speedLabel(2f))
        assertEquals("0.5", speedLabel(0.5f))
        assertEquals("1.5", speedLabel(1.5f))
    }
}
