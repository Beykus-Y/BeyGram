package com.beykus.beygram.data.media

import android.media.MediaCodecInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoNoteCodecProfileTest {
    @Test fun avcProfilesAreNamedForReports() {
        assertEquals("Baseline", avcProfileLabel(MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline))
        assertEquals("Main", avcProfileLabel(MediaCodecInfo.CodecProfileLevel.AVCProfileMain))
        assertEquals("High", avcProfileLabel(MediaCodecInfo.CodecProfileLevel.AVCProfileHigh))
        assertEquals("Constrained High", avcProfileLabel(MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedHigh))
    }

    @Test fun avcLevelsAreNamedForReports() {
        assertEquals("3.1", avcLevelLabel(MediaCodecInfo.CodecProfileLevel.AVCLevel31))
        assertEquals("4", avcLevelLabel(MediaCodecInfo.CodecProfileLevel.AVCLevel4))
        assertEquals("5.2", avcLevelLabel(MediaCodecInfo.CodecProfileLevel.AVCLevel52))
        assertNull(avcLevelLabel(999))
    }

    @Test fun unknownCodesStayNumericInsteadOfBecomingAConfidentClaim() {
        assertNull(avcProfileLabel(999))
        assertEquals("999", codecProfileLabel("video/avc", 999))
        assertEquals("999", codecProfileLabel("video/hevc", 999))
        assertEquals("999", codecLevelLabel("video/hevc", 999))
    }
}
