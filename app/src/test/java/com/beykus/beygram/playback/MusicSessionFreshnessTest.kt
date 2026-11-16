package com.beykus.beygram.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSessionFreshnessTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun aSessionIsFreshUntilSevenDaysAfterItWasWritten() {
        assertTrue(musicSessionFresh(savedAtMs = 1_000L, nowMs = 1_000L))
        assertTrue(musicSessionFresh(savedAtMs = 0L, nowMs = 7 * day))
        assertFalse(musicSessionFresh(savedAtMs = 0L, nowMs = 7 * day + 1))
    }

    @Test
    fun aSessionWrittenInTheFutureIsNotTrusted() {
        assertFalse(musicSessionFresh(savedAtMs = 2_000L, nowMs = 1_000L))
    }
}
