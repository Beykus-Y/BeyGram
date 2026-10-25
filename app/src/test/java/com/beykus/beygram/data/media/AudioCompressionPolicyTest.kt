package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCompressionPolicyTest {
    private fun info(
        duration: Int = 180,
        size: Long = 8L * 1024 * 1024,
        mime: String = "audio/mpeg",
        aac: Boolean = false,
        metadata: Boolean = false
    ) = AudioSourceInfo(duration, size, mime, aac, metadata)

    @Test
    fun presetsExposeExpectedBitrates() {
        assertEquals(48_000, audioQualityBitrate(AudioQualityOption.Compact))
        assertEquals(96_000, audioQualityBitrate(AudioQualityOption.Balanced))
        assertEquals(0, audioQualityBitrate(AudioQualityOption.Original))
    }

    @Test
    fun originalNeverReencodes() {
        val plan = audioCompressionPlan(info(), AudioQualityOption.Original)
        assertFalse(plan.transcode)
        assertEquals(AudioPlanReason.OriginalRequested, plan.reason)
    }

    @Test
    fun unknownMetadataFallsBackToTheOriginal() {
        val plan = audioCompressionPlan(info(duration = 0), AudioQualityOption.Balanced)
        assertFalse(plan.transcode)
        assertEquals(AudioPlanReason.UnknownMetadata, plan.reason)
    }

    @Test
    fun taggedFilesKeepTheirMetadata() {
        val plan = audioCompressionPlan(info(metadata = true), AudioQualityOption.Compact)
        assertFalse(plan.transcode)
        assertEquals(AudioPlanReason.MetadataPresent, plan.reason)
    }

    @Test
    fun tinyAudioIsNotTranscoded() {
        val plan = audioCompressionPlan(info(size = 100_000L), AudioQualityOption.Compact)
        assertFalse(plan.transcode)
        assertEquals(AudioPlanReason.AlreadyEfficient, plan.reason)
    }

    @Test
    fun efficientAacIsKept() {
        // 96 kbps AAC in an MP4 container and no tags.
        val plan = audioCompressionPlan(
            info(duration = 180, size = 2_160_000L, mime = "audio/mp4", aac = true),
            AudioQualityOption.Balanced
        )
        assertFalse(plan.transcode)
        assertEquals(AudioPlanReason.AlreadyEfficient, plan.reason)
    }

    @Test
    fun largeUntaggedAudioIsTranscoded() {
        val plan = audioCompressionPlan(info(), AudioQualityOption.Balanced)
        assertTrue(plan.transcode)
        assertEquals(AudioPlanReason.Transcode, plan.reason)
        assertEquals(96_000, plan.bitrate)
        assertEquals(2_160_000L, plan.expectedBytes)
        assertTrue(plan.expectedBytes < 8L * 1024 * 1024)
    }

    @Test
    fun compressionRequiresMeaningfulSavings() {
        // ~112 kbps MP3: a 96 kbps re-encode saves less than 15%.
        val plan = audioCompressionPlan(
            info(duration = 180, size = 2_520_000L),
            AudioQualityOption.Balanced
        )
        assertFalse(plan.transcode)
        assertEquals(AudioPlanReason.NotWorthIt, plan.reason)
    }

    @Test
    fun estimatedBytesUseDurationAndBitrate() {
        assertEquals(2_160_000L, estimatedAudioBytes(180, 96_000))
        assertEquals(0L, estimatedAudioBytes(0, 96_000))
        assertEquals(0L, estimatedAudioBytes(180, 0))
    }

    @Test
    fun compressedNameKeepsTheOriginalBasename() {
        assertEquals("track.m4a", compressedAudioFileName("track.mp3"))
        assertEquals("recording.m4a", compressedAudioFileName("recording"))
        assertEquals("audio.m4a", compressedAudioFileName(".mp3"))
    }
}
