package com.beykus.beygram.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadMeterTest {
    private val second = 1_000_000_000L

    @Test fun percentIsTheTransferOnlyAndCapsAtOneHundred() {
        assertEquals(0, DownloadProgress(0, 0, null, null).percent)
        assertEquals(50, DownloadProgress(500, 1000, null, null).percent)
        assertEquals(100, DownloadProgress(1000, 1000, null, null).percent)
        assertEquals(100, DownloadProgress(1200, 1000, null, null).percent)
    }

    @Test fun resumedBytesCountTowardsTheTotal() {
        val meter = DownloadMeter(total = 1000, startBytes = 400, startNanos = 0)
        val progress = meter.sample(bytes = 400, nowNanos = second)
        assertEquals(400L, progress.bytes)
        assertEquals(40, progress.percent)
        assertNull(progress.bytesPerSecond)
    }

    @Test fun speedIsSmoothedFromExactByteDeltas() {
        val meter = DownloadMeter(total = 100_000_000, startBytes = 0, startNanos = 0)
        // 1 MB per second, then 2 MB per second: the exponential average moves only part of the way.
        meter.sample(bytes = 1_000_000, nowNanos = second)
        val progress = meter.sample(bytes = 3_000_000, nowNanos = 2 * second)
        val expected = (DownloadMeter.SMOOTHING * 2_000_000 + (1 - DownloadMeter.SMOOTHING) * 1_000_000).toLong()
        assertEquals(expected.toDouble(), progress.bytesPerSecond!!.toDouble(), 2_000.0)
    }

    @Test fun etaWaitsForThreeSecondsAndFiveSamples() {
        val meter = DownloadMeter(total = 1_000_000, startBytes = 0, startNanos = 0)
        // Five samples inside the first second: enough samples, but not enough elapsed time.
        for (i in 1..5) meter.sample(bytes = i * 10_000L, nowNanos = i * 100_000_000L)
        assertNull(meter.sample(bytes = 60_000, nowNanos = 900_000_000L).etaSeconds)
        // Later samples at a steady rate: the ETA appears once time and samples are both enough.
        var last: DownloadProgress? = null
        for (i in 1..10) last = meter.sample(bytes = 60_000L + i * 10_000L, nowNanos = second + i * 300_000_000L)
        assertTrue(last!!.etaSeconds != null)
    }

    @Test fun etaIsNullWhenNothingRemains() {
        val meter = DownloadMeter(total = 100, startBytes = 0, startNanos = 0)
        for (i in 1..10) meter.sample(bytes = i * 10L, nowNanos = i * second)
        assertNull(meter.sample(bytes = 100, nowNanos = 11 * second).etaSeconds)
    }

    @Test fun throttleLetsOneEventThroughEveryQuarterSecondAndTheCompletingOne() {
        val throttle = ProgressThrottle()
        assertTrue(throttle.shouldEmit(nowNanos = 0, complete = false))
        assertFalse(throttle.shouldEmit(nowNanos = 100_000_000L, complete = false))
        assertFalse(throttle.shouldEmit(nowNanos = 249_000_000L, complete = false))
        assertTrue(throttle.shouldEmit(nowNanos = 250_000_000L, complete = false))
        assertTrue(throttle.shouldEmit(nowNanos = 260_000_000L, complete = true))
    }

}
