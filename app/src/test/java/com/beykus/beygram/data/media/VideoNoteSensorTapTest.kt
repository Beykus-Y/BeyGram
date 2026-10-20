package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val stats = tap.finish(30)!!.stats
        assertEquals(30.0, stats.measuredFps!!, 0.2)
        assertTrue(stats.coverage > 0.95)
        assertEquals(0, stats.longGaps)
    }

    @Test fun halfRateAgainst60ShowsLongGaps() {
        val tap = VideoNoteSensorTap()
        tap.begin()
        repeat(90) { tap.record(it * frameNs, null) }
        val stats = tap.finish(60)!!.stats
        assertEquals(30.0, stats.measuredFps!!, 0.2)
        assertTrue(stats.longGaps >= 80)
    }

    @Test fun resultsAfterStopAreNotCounted() {
        val tap = VideoNoteSensorTap()
        tap.begin()
        repeat(30) { tap.record(it * frameNs, null) }
        tap.stop()
        repeat(30) { tap.record((30 + it) * frameNs, null) }
        assertEquals(30, tap.finish(30)!!.stats.frames)
    }

    @Test fun finishClearsSoTheNextTakeNeverSeesOldNumbers() {
        val tap = VideoNoteSensorTap()
        tap.begin()
        tap.record(0L, null, "0,0 100x100", 1.0f)
        assertEquals("0,0 100x100", tap.finish(30)!!.crop)
        assertNull(tap.finish(30))
    }

    @Test fun capacityIsReportedAsTruncation() {
        val tap = VideoNoteSensorTap(capacity = 10)
        tap.begin()
        repeat(20) { tap.record(it * frameNs, null) }
        val result = tap.finish(30)!!
        assertTrue(result.truncated)
        assertEquals(10, result.stats.frames)
    }

    @Test fun cameraSwitchStartsANewSegment() {
        val tap = VideoNoteSensorTap()
        tap.begin()
        repeat(10) { tap.record(it * frameNs, null) }
        tap.begin()
        repeat(5) { tap.record(9_000_000_000_000L + it * frameNs, null) }
        val result = tap.finish(30)!!
        assertEquals(5, result.stats.frames)
        assertFalse(result.truncated)
    }
}
