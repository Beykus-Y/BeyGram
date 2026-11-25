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
    fun mp4WithAnyOtherVideoCodecIsStillAVideoMessage() {
        // The codec decides whether this device re-encodes the file, never whether the message is a video (#1175).
        assertTrue(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/hevc")))
        assertTrue(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/av01")))
        assertTrue(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/x-vnd.on2.vp9")))
    }

    @Test
    fun matroskaAndOtherContainersStayDocuments() {
        assertFalse(videoCanBeSentAsVideoMessage("video/x-matroska", listOf("video/avc")))
        assertFalse(videoCanBeSentAsVideoMessage("video/x-matroska", listOf("video/x-matroska")))
        assertFalse(videoCanBeSentAsVideoMessage("video/webm", listOf("video/x-vnd.on2.vp9")))
        assertFalse(videoCanBeSentAsVideoMessage("video/quicktime", listOf("video/avc")))
    }

    @Test
    fun missingVideoTrackIsNotAVideo() {
        assertFalse(videoCanBeSentAsVideoMessage("video/mp4", emptyList()))
    }

    @Test
    fun onlyH264CountsAsTheTargetCodec() {
        assertTrue(videoIsAlreadyTargetCodec(listOf("video/avc")))
        assertTrue(videoIsAlreadyTargetCodec(listOf("video/avc", "video/avc")))
        assertFalse(videoIsAlreadyTargetCodec(listOf("video/hevc")))
        assertFalse(videoIsAlreadyTargetCodec(listOf("video/avc", "video/hevc")))
        assertFalse(videoIsAlreadyTargetCodec(emptyList()))
    }
}