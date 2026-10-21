package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteSyntheticSourceTest {
    @Test fun sixtyFpsClipHas180StrictlyIncreasingFramesInThreeSeconds() {
        val frames = VideoNoteSyntheticSource.frameCount(VideoNoteSyntheticSource.FPS_60)
        assertEquals(180, frames)
        val pts = (0 until frames).map { syntheticFramePtsUs(it, 60) }
        assertTrue(pts.zipWithNext().all { (a, b) -> b > a })
        assertEquals(3_000_000L, syntheticFramePtsUs(frames, 60))
        assertEquals(60.0, measuredVideoFps(frames, pts.first(), pts.last())!!, 0.05)
    }

    @Test fun thirtyFpsClipKeepsThePreviousShape() {
        assertEquals(90, VideoNoteSyntheticSource.frameCount(VideoNoteSyntheticSource.FPS_30))
    }

    @Test fun everyLabRateGivesDistinctIncreasingFramesOverThreeSeconds() {
        assertEquals(listOf(24, 30, 50, 60), VideoNoteSyntheticSource.SUPPORTED_FPS)
        VideoNoteSyntheticSource.SUPPORTED_FPS.forEach { fps ->
            val frames = VideoNoteSyntheticSource.frameCount(fps)
            assertEquals(fps * 3, frames)
            val pts = (0 until frames).map { syntheticFramePtsUs(it, fps) }
            assertTrue(pts.zipWithNext().all { (a, b) -> b > a })
            assertEquals(fps.toDouble(), measuredVideoFps(frames, pts.first(), pts.last())!!, 0.05)
        }
    }

    @Test fun fpsKeptNeedsAMeasurement() {
        assertEquals(true, syntheticFpsKept(60, 59.9))
        assertFalse(syntheticFpsKept(60, 30.0)!!)
        assertNull(syntheticFpsKept(60, null))
    }
}
