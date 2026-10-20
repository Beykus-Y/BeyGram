package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoNoteBitrateTest {
    @Test
    fun `bitrate covers the whole clip including the last frame`() {
        // 31 frames at 30 FPS span 30 gaps = 1 s of timestamps, 31 frames of duration = 1.033 s
        val bps = measuredVideoBitrateBps(videoBytes = 125_000L, sampleCount = 31, earliestUs = 0L, latestUs = 1_000_000L)
        assertEquals(968_000, bps!!, 1000)
    }

    @Test
    fun `no bitrate without enough samples or bytes`() {
        assertNull(measuredVideoBitrateBps(1000L, 1, 0L, 0L))
        assertNull(measuredVideoBitrateBps(0L, 10, 0L, 1_000_000L))
    }
}
