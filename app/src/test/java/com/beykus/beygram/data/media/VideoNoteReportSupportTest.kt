package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteReportSupportTest {
    @Test fun redactorMasksPathsHandlesPhonesEmailsAndTokens() {
        val text = "file /data/user/0/com.beykus.beygram.dev/files/x.mp4, @some_user +7 999 123-45-67 a.b@c.de " +
            "123456789:AAEhBOweik6ad9r_QXMENQjcrGbqCr4K-Yo"
        val out = VideoNoteReportRedactor.redact(text)
        assertEquals("file <path>, <handle> <phone> <email> <token>", out)
    }

    @Test fun redactorKeepsMeasurementsAndHashes() {
        val text = "frames 180/180, 60.00 FPS, 4ff60a09f3be6ec106ae48232f0945989007a0bac9e36dd76df05a5e1f49259c, 1796000000 ns"
        assertEquals(text, VideoNoteReportRedactor.redact(text))
    }

    @Test fun onlyMeasuredStatusesCarryAConclusion() {
        assertTrue(VideoNoteTestStatus.MEASURED_PASS.isMeasured)
        assertTrue(VideoNoteTestStatus.UNSTABLE.isMeasured)
        assertFalse(VideoNoteTestStatus.NOT_QUERIED.isMeasured)
        assertFalse(VideoNoteTestStatus.NO_FRAMES.isMeasured)
        assertFalse(VideoNoteTestStatus.INSUFFICIENT_FRAMES.isMeasured)
    }

    @Test fun statusKeysAreUnique() {
        assertEquals(VideoNoteTestStatus.values().size, VideoNoteTestStatus.values().map { it.key }.toSet().size)
    }
}
