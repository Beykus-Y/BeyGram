package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoFrameRatePolicyTest {
    @Test fun maximumProfileUsesChosenPriority() {
        assertEquals(24..60, VideoMessageSettings(preset = VideoQualityPreset.Maximum,
            frameRatePriority = VideoFrameRatePriority.MaximumQuality).requestedFrameRateRange())
        assertEquals(35..60, VideoMessageSettings(preset = VideoQualityPreset.Maximum,
            frameRatePriority = VideoFrameRatePriority.Balanced).requestedFrameRateRange())
        assertEquals(55..60, VideoMessageSettings(preset = VideoQualityPreset.Maximum,
            frameRatePriority = VideoFrameRatePriority.MaximumSmoothness).requestedFrameRateRange())
    }

    @Test fun standardAndHighIgnorePriority() {
        for (preset in listOf(VideoQualityPreset.Standard, VideoQualityPreset.High)) {
            assertEquals(30..30, VideoMessageSettings(preset = preset,
                frameRatePriority = VideoFrameRatePriority.MaximumQuality).requestedFrameRateRange())
        }
    }

    @Test fun customFpsRemainsUpperLimit() {
        assertEquals(24..30, VideoMessageSettings(preset = VideoQualityPreset.Custom,
            customFps = 30, frameRatePriority = VideoFrameRatePriority.MaximumQuality).requestedFrameRateRange())
        assertEquals(30..30, VideoMessageSettings(preset = VideoQualityPreset.Custom,
            customFps = 30, frameRatePriority = VideoFrameRatePriority.MaximumSmoothness).requestedFrameRateRange())
    }

    @Test fun supportedRangesRespectPreferredMinimumWhenPossible() {
        val ranges = listOf(15..30, 30..60, 60..60)
        assertEquals(30..60, chooseSupportedFrameRateRange(24..60, ranges))
        assertEquals(60..60, chooseSupportedFrameRateRange(35..60, ranges))
        assertEquals(60..60, chooseSupportedFrameRateRange(55..60, ranges))
    }

    @Test fun fallsBackToAvailableFpsWithoutInventingCameraCapabilities() {
        assertEquals(30..30, chooseSupportedFrameRateRange(55..60, listOf(15..30, 30..30)))
        assertNull(chooseSupportedFrameRateRange(24..60, emptyList()))
    }
}
