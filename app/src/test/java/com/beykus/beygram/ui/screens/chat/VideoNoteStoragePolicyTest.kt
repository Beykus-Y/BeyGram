package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.data.media.VideoMessageSettings
import com.beykus.beygram.data.media.VideoQualityPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteStoragePolicyTest {
    @Test fun cameraBitrateFollowsPreset() {
        assertEquals(1_000_000, videoCameraBitrate(VideoMessageSettings(preset = VideoQualityPreset.Standard)))
        assertEquals(1_400_000, videoCameraBitrate(VideoMessageSettings(preset = VideoQualityPreset.High)))
        assertEquals(4_000_000, videoCameraBitrate(VideoMessageSettings(preset = VideoQualityPreset.Maximum)))
        assertEquals(2_000_000, videoCameraBitrate(VideoMessageSettings(preset = VideoQualityPreset.Custom, customVideoBitrateKbps = 2_000)))
    }

    @Test fun hourAtMaximumRequiresRawPlusSegmentsPlusMargin() {
        val required = videoPeakStorageBytes(videoCameraBitrate(VideoMessageSettings(preset = VideoQualityPreset.Maximum)), durationSeconds = 3_600)
        val rawBytes = (4_000_000L + 128_000L) * 3_600 / 8
        assertEquals(rawBytes * 2 + 256L * 1024 * 1024, required)
        assertTrue(required > 3_600_000_000L)
    }

    @Test fun minuteRecordingUsesSameFormula() {
        val required = videoPeakStorageBytes(videoCameraBitrate(VideoMessageSettings(preset = VideoQualityPreset.Standard)), durationSeconds = 60)
        val recordedBytes = (1_000_000L + 128_000L) * 60 / 8
        assertEquals(recordedBytes * 2 + 256L * 1024 * 1024, required)
    }
}
