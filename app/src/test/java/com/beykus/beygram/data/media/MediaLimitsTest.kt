package com.beykus.beygram.data.media

import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaLimitsTest {
    @Test
    fun photoLimitMatchesTheImporterLimit() {
        assertNull(mediaLimitIssue(MediaKind.Photo, MediaLimits.MAX_PHOTO_BYTES, 0))
        assertEquals(MediaLimitIssue.PhotoTooLarge, mediaLimitIssue(MediaKind.Photo, MediaLimits.MAX_PHOTO_BYTES + 1, 0))
        assertNull(mediaLimitIssue(MediaKind.Photo, 0, 0))
    }

    @Test
    fun videoLimitsAreAppliedToVideoOnly() {
        assertEquals(MediaLimitIssue.VideoTooLarge, mediaLimitIssue(MediaKind.Video, MediaLimits.MAX_VIDEO_BYTES + 1, 30))
        assertEquals(MediaLimitIssue.VideoTooLong, mediaLimitIssue(MediaKind.Video, 1_000, MediaLimits.MAX_VIDEO_DURATION_SECONDS + 1))
        assertNull(mediaLimitIssue(MediaKind.Video, MediaLimits.MAX_VIDEO_BYTES, MediaLimits.MAX_VIDEO_DURATION_SECONDS))
        assertNull(mediaLimitIssue(MediaKind.Document, MediaLimits.MAX_VIDEO_BYTES + 1, 10_000))
    }

    @Test
    fun photoMegabytesMatchesTheUserFacingLimit() {
        assertEquals(10, MediaLimits.maxPhotoMegabytes)
    }
}
