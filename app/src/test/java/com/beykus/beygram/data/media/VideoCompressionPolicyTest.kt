package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCompressionPolicyTest {
    private fun info(
        width: Int = 1920,
        height: Int = 1080,
        duration: Int = 60,
        size: Long = 60L * 1024 * 1024,
        mime: String = "video/mp4",
        h264: Boolean = true,
        audio: Boolean = true
    ) = VideoSourceInfo(width, height, duration, size, mime, h264, audio)

    @Test
    fun limitsAcceptExactBoundaryAndRejectBeyond() {
        assertNull(videoLimitIssue(info(duration = MediaLimits.MAX_VIDEO_DURATION_SECONDS)))
        assertEquals(VideoLimitIssue.TooLong, videoLimitIssue(info(duration = MediaLimits.MAX_VIDEO_DURATION_SECONDS + 1)))
        assertNull(videoLimitIssue(info(size = MediaLimits.MAX_VIDEO_BYTES)))
        assertEquals(VideoLimitIssue.TooLarge, videoLimitIssue(info(size = MediaLimits.MAX_VIDEO_BYTES + 1)))
    }

    @Test
    fun originalQualityNeverReencodes() {
        val plan = videoCompressionPlan(info(), VideoQualityOption.Original)
        assertFalse(plan.transcode)
        assertEquals(VideoPlanReason.OriginalRequested, plan.reason)
    }

    @Test
    fun alreadyEfficientH264AtTargetResolutionIsKept() {
        val plan = videoCompressionPlan(
            info(width = 1280, height = 720, size = 15L * 1024 * 1024),
            VideoQualityOption.Balanced
        )
        assertFalse(plan.transcode)
        assertEquals(VideoPlanReason.AlreadyEfficient, plan.reason)
    }

    @Test
    fun largeHighBitrateVideoIsTranscodedToTheBalancedTarget() {
        val plan = videoCompressionPlan(info(size = 90L * 1024 * 1024), VideoQualityOption.Balanced)
        assertTrue(plan.transcode)
        assertEquals(VideoPlanReason.Transcode, plan.reason)
        assertEquals(720, plan.targetHeight)
        assertEquals(2_500_000, plan.videoBitrate)
        assertEquals(128_000, plan.audioBitrate)
        assertTrue(plan.expectedBytes < 90L * 1024 * 1024)
    }

    @Test
    fun compactDownscalesTo480p() {
        val plan = videoCompressionPlan(info(size = 90L * 1024 * 1024), VideoQualityOption.Compact)
        assertTrue(plan.transcode)
        assertEquals(480, plan.targetHeight)
    }

    @Test
    fun portraitVideoUsesTheProportionalLongEdge() {
        val plan = videoCompressionPlan(
            info(width = 1080, height = 1920, size = 90L * 1024 * 1024),
            VideoQualityOption.Balanced
        )
        assertTrue(plan.transcode)
        assertEquals(1280, plan.targetHeight)
        assertEquals(720, videoQualityShortSide(VideoQualityOption.Balanced))
    }

    @Test
    fun smallSourceIsNeverUpscaled() {
        val plan = videoCompressionPlan(
            info(width = 854, height = 480, size = 30L * 1024 * 1024, mime = "video/x-matroska", h264 = false),
            VideoQualityOption.Balanced
        )
        assertEquals(480, plan.targetHeight)
    }

    @Test
    fun nonH264OrNonMp4IsTranscodedForCompatibility() {
        val nonH264 = videoCompressionPlan(info(size = 90L * 1024 * 1024, h264 = false), VideoQualityOption.Balanced)
        assertTrue(nonH264.transcode)
        val mkv = videoCompressionPlan(info(size = 90L * 1024 * 1024, mime = "video/x-matroska"), VideoQualityOption.Balanced)
        assertTrue(mkv.transcode)
    }

    @Test
    fun unknownMetadataFallsBackToTheOriginal() {
        val unknown = videoCompressionPlan(info(width = 0, height = 0), VideoQualityOption.Balanced)
        assertFalse(unknown.transcode)
        assertEquals(VideoPlanReason.UnknownMetadata, unknown.reason)
    }

    @Test
    fun tinyVideosAreNotTranscoded() {
        val tiny = videoCompressionPlan(info(size = 900_000L), VideoQualityOption.Compact)
        assertFalse(tiny.transcode)
        assertEquals(VideoPlanReason.AlreadyEfficient, tiny.reason)
    }

    @Test
    fun compressionIsSkippedWhenItWouldNotShrinkTheFile() {
        // 1080p at 1.5 Mbps: a 720p re-encode at the balanced bitrate would be larger.
        val plan = videoCompressionPlan(
            info(size = 11L * 1024 * 1024),
            VideoQualityOption.Balanced
        )
        assertFalse(plan.transcode)
        assertEquals(VideoPlanReason.NotWorthIt, plan.reason)
    }

    @Test
    fun estimatedBytesCountVideoAndAudio() {
        assertEquals(19_710_000L, estimatedVideoBytes(60, 2_500_000, 128_000))
        assertEquals(0L, estimatedVideoBytes(0, 2_500_000, 128_000))
    }
}
