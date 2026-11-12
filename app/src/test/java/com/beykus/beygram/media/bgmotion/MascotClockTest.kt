package com.beykus.beygram.media.bgmotion

import org.junit.Assert.assertEquals
import org.junit.Test

class MascotClockTest {
    private val start = 1_000_000_000L

    @Test
    fun extrapolatesBetweenPlayerTicks() {
        val clock = MascotClock()
        clock.onPlaybackState("track", 0L, 60_000L, playing = true, speed = 1f, nowNanos = start)

        assertEquals(0.0, clock.timeSeconds(start), 0.0001)
        assertEquals(0.25, clock.timeSeconds(start + 250_000_000L), 0.0001)
        assertEquals(0.5, clock.timeSeconds(start + 500_000_000L), 0.0001)
    }

    @Test
    fun normalTickDoesNotJumpBackwards() {
        val clock = MascotClock()
        clock.onPlaybackState("track", 0L, 60_000L, playing = true, speed = 1f, nowNanos = start)
        val beforeTick = clock.timeSeconds(start + 250_000_000L)

        clock.onPlaybackState("track", 250L, 60_000L, playing = true, speed = 1f, nowNanos = start + 250_000_000L)

        val afterTick = clock.timeSeconds(start + 250_000_000L)
        assertEquals(beforeTick, afterTick, 0.001)
    }

    @Test
    fun seekSnapsToTheNewPosition() {
        val clock = MascotClock()
        clock.onPlaybackState("track", 10_000L, 60_000L, playing = true, speed = 1f, nowNanos = start)

        clock.onPlaybackState("track", 40_000L, 60_000L, playing = true, speed = 1f, nowNanos = start + 1_000_000_000L)

        assertEquals(40.0, clock.timeSeconds(start + 1_000_000_000L), 0.001)
        assertEquals(41.0, clock.timeSeconds(start + 2_000_000_000L), 0.001)
    }

    @Test
    fun pauseFreezesAndResumeContinuesFromReportedPosition() {
        val clock = MascotClock()
        clock.onPlaybackState("track", 5_000L, 60_000L, playing = true, speed = 1f, nowNanos = start)
        clock.onPlaybackState("track", 5_000L, 60_000L, playing = false, speed = 1f, nowNanos = start + 1_000_000_000L)

        assertEquals(5.0, clock.timeSeconds(start + 5_000_000_000L), 0.001)

        clock.onPlaybackState("track", 5_000L, 60_000L, playing = true, speed = 1f, nowNanos = start + 6_000_000_000L)
        assertEquals(6.0, clock.timeSeconds(start + 7_000_000_000L), 0.001)
    }

    @Test
    fun playbackSpeedIsApplied() {
        val clock = MascotClock()
        clock.onPlaybackState("track", 0L, 60_000L, playing = true, speed = 1.25f, nowNanos = start)

        assertEquals(1.25, clock.timeSeconds(start + 1_000_000_000L), 0.001)
    }

    @Test
    fun itemChangeSnapsWithoutMixingTracks() {
        val clock = MascotClock()
        clock.onPlaybackState("track-a", 20_000L, 60_000L, playing = true, speed = 1f, nowNanos = start)
        clock.onPlaybackState("track-b", 3_000L, 60_000L, playing = true, speed = 1f, nowNanos = start + 1_000_000_000L)

        assertEquals(3.0, clock.timeSeconds(start + 1_000_000_000L), 0.001)
    }

    @Test
    fun durationClampPreventsRunningPastTheEnd() {
        val clock = MascotClock()
        clock.onPlaybackState("track", 59_000L, 60_000L, playing = true, speed = 1f, nowNanos = start)

        assertEquals(60.0, clock.timeSeconds(start + 2_000_000_000L), 0.001)
    }
}
