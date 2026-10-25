package com.beykus.beygram.data.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoImportPolicyTest {
    @Test
    fun mp4H264CanBeSentAsVideo() {
        assertTrue(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/avc")))
        assertTrue(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/avc", "video/avc")))
    }

    @Test
    fun matroskaAndHevcStayDocuments() {
        assertFalse(videoCanBeSentAsVideoMessage("video/x-matroska", listOf("video/avc")))
        assertFalse(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/hevc")))
        assertFalse(videoCanBeSentAsVideoMessage("video/x-matroska", listOf("video/x-matroska")))
    }

    @Test
    fun missingVideoTrackIsNotAVideo() {
        assertFalse(videoCanBeSentAsVideoMessage("video/mp4", emptyList()))
    }
}
