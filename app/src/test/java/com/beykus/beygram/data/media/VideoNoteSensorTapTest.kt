package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteSensorTapTest {
    private val frameNs = 33_333_333L

    @Test fun resultsBeforeBeginAreIgnored() {
        val tap = VideoNoteSensorTap()
        tap.record(1_000L, 1L)
        assertNull(tap.finish(30))
    }

    @Test fun steady30ReadsAs30WithFullCoverage() {
        val tap = VideoNoteSensorTap()
        tap.begin()
        repeat(90) { tap.record(it * frameNs, 20_000_000L) }
        val stats = tap.finish(30)!!
        assertEquals(30.0, stats.measuredFps!!, 0.2)
        assertTrue(stats.coverage > 0.95)
        assertEquals(0, stats.longGaps)
    }

    @Test fun halfRateAgainst60ShowsLowCoverageAndRate() {
        val tap = VideoNoteSensorTap()
        tap.begin()
        repeat(90) { tap.record(it * frameNs, null) }
        val stats = tap.finish(60)!!
        assertEquals(30.0, stats.measuredFps!!, 0.2)
        assertTrue(stats.longGaps >= 80)
    }

    @Test fun finishStopsCollecting() {
        val tap = VideoNoteSensorTap()
        tap.begin()
        tap.record(0L, null)
        assertNotNull(tap.finish(30))
        tap.record(frameNs, null)
        assertNull(tap.finish(30)?.takeIf { it.frames > 1 })
    }
}
