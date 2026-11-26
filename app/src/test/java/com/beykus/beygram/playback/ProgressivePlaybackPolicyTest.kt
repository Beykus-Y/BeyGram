package com.beykus.beygram.playback

import com.beykus.beygram.data.media.Mp4StreamingInfo
import com.beykus.beygram.data.media.Mp4StreamingReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressivePlaybackPolicyTest {
    private fun info(reason: Mp4StreamingReason, streamable: Boolean = false) = Mp4StreamingInfo(
        isMp4 = reason != Mp4StreamingReason.NotMp4,
        fastStart = reason == Mp4StreamingReason.Streamable,
        videoCodecs = emptySet(),
        streamable = streamable,
        reason = reason,
    )

    @Test
    fun `a fast-start file plays with or without ranges`() {
        val streamable = info(Mp4StreamingReason.Streamable, streamable = true)
        assertTrue(canPlayProgressively(streamable, rangesAvailable = true))
        assertTrue(canPlayProgressively(streamable, rangesAvailable = false))
    }

    @Test
    fun `an index at the end plays only when ranges are available`() {
        val moovLast = info(Mp4StreamingReason.MoovAfterMediaData)
        assertTrue(canPlayProgressively(moovLast, rangesAvailable = true))
        assertFalse(canPlayProgressively(moovLast, rangesAvailable = false))
    }

    @Test
    fun `an unsupported codec never plays, with or without ranges`() {
        val unsupported = info(Mp4StreamingReason.UnsupportedVideoCodec)
        assertFalse(canPlayProgressively(unsupported, rangesAvailable = true))
        assertFalse(canPlayProgressively(unsupported, rangesAvailable = false))
    }

    @Test
    fun `an index at the end stops being a permanent rejection once ranges exist`() {
        val moovLast = info(Mp4StreamingReason.MoovAfterMediaData)
        assertTrue(isPermanentProgressiveRejection(moovLast, rangesAvailable = false))
        assertFalse(isPermanentProgressiveRejection(moovLast, rangesAvailable = true))
    }

    @Test
    fun `a truncated or missing index stays retryable`() {
        assertFalse(isPermanentProgressiveRejection(info(Mp4StreamingReason.TruncatedMoov), rangesAvailable = true))
        assertFalse(isPermanentProgressiveRejection(info(Mp4StreamingReason.MissingMoov), rangesAvailable = true))
    }

    @Test
    fun `container and track failures stay permanent`() {
        assertTrue(isPermanentProgressiveRejection(info(Mp4StreamingReason.NotMp4), rangesAvailable = true))
        assertTrue(isPermanentProgressiveRejection(info(Mp4StreamingReason.MoovTooLarge), rangesAvailable = true))
        assertTrue(isPermanentProgressiveRejection(info(Mp4StreamingReason.NoVideoTrack), rangesAvailable = true))
        assertTrue(isPermanentProgressiveRejection(info(Mp4StreamingReason.UnsupportedVideoCodec), rangesAvailable = true))
    }

    @Test
    fun `a failed read is retried instead of rejected`() {
        assertFalse(isPermanentProgressiveRejection(info(Mp4StreamingReason.ReadFailed), rangesAvailable = true))
        assertFalse(isPermanentProgressiveRejection(null, rangesAvailable = true))
    }
}