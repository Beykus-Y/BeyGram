package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSpeedOptionsTest {
    @Test fun requestedSpeedChoicesArePresentAndOrdered() {
        assertEquals(listOf(0.5f, 1f, 1.5f, 2f, 5f), PLAYBACK_SPEED_OPTIONS)
    }

    @Test fun labelsAreCleanForFractionalAndWholeSpeeds() {
        assertEquals(listOf("0.5", "1", "1.5", "2", "5"), PLAYBACK_SPEED_OPTIONS.map(::speedLabel))
        assertTrue(1f in PLAYBACK_SPEED_OPTIONS)
    }
}
