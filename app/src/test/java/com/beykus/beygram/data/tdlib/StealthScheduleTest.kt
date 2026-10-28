package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.OutgoingMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StealthScheduleTest {
    @Test fun textIsScheduledExactlyTheChosenLeadAfterServerTime() {
        assertEquals(1_000_015L, stealthScheduleAt(1_000_000L, 15, 0L))
        assertEquals(1_000_060L, stealthScheduleAt(1_000_000L, 60, 0L))
    }

    @Test fun everyLeadStaysClearOfTheImmediateSendThreshold() {
        val now = 1_700_000_000L
        val at = stealthScheduleAt(now, 15, 0L)
        assertTrue(MessageSendOptions(scheduledAtSeconds = at).scheduledTimeIsFuture(now))
        assertTrue(at - now > MessageSendOptions.MIN_SCHEDULE_LEAD_SECONDS)
    }

    @Test fun uploadsGetTimeAtASlowMobileSpeedRoundedUp() {
        assertEquals(15L + 1, stealthScheduleAt(0L, 15, 1L))
        assertEquals(15L + 16, stealthScheduleAt(0L, 15, 16 * STEALTH_ASSUMED_UPLOAD_BYTES_PER_SECOND))
        assertEquals(15L + 17, stealthScheduleAt(0L, 15, 16 * STEALTH_ASSUMED_UPLOAD_BYTES_PER_SECOND + 1))
    }

    @Test fun uploadAllowanceIsCapped() {
        assertEquals(15L + STEALTH_MAX_UPLOAD_ALLOWANCE_SECONDS, stealthScheduleAt(0L, 15, Long.MAX_VALUE / 2))
    }

    @Test fun mediaAlreadyOnTheServerNeedsNoUploadTime() {
        val remote = OutgoingMedia(path = "", fileName = "s.webp", mimeType = "image/webp", kind = MediaKind.Sticker, size = 50_000, remoteFileId = 7)
        assertEquals(0L, remote.uploadBytes())
        val local = OutgoingMedia(path = "/tmp/none", fileName = "p.jpg", mimeType = "image/jpeg", kind = MediaKind.Photo, size = 2_000_000)
        assertEquals(2_000_000L, local.uploadBytes())
    }
}
