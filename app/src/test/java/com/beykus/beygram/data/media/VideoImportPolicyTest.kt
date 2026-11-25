package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoImportPolicyTest {
    @Test
    fun h264Mp4IsAVideoMessage() {
        assertTrue(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/avc")))
        assertTrue(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/avc", "video/avc")))
        assertEquals(VideoCodecClass.Avc, videoCodecClass(listOf("video/avc")))
        assertTrue(videoIsAlreadyTargetCodec(listOf("video/avc")))
        assertFalse(videoCodecNeedsConversion("video/mp4", listOf("video/avc")))
    }

    @Test
    fun hevcMp4IsNotPromisedAsAVideoMessage() {
        // A HEVC encoder in one Android client and an iOS decoder do not prove that an arbitrary MP4/HEVC profile plays
        // for the recipient (Codex review of #1184). HEVC stays a document and gets an explicit conversion path.
        assertFalse(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/hevc")))
        assertEquals(VideoCodecClass.Hevc, videoCodecClass(listOf("video/hevc")))
        assertTrue(videoCodecNeedsConversion("video/mp4", listOf("video/hevc")))
        assertFalse(videoIsAlreadyTargetCodec(listOf("video/hevc")))
    }

    @Test
    fun av1AndVp9AreNotPromisedAsVideoMessages() {
        // TDLib accepts the upload, but no Telegram client is known to decode these inside a video message.
        assertFalse(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/av01")))
        assertFalse(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/x-vnd.on2.vp9")))
        assertEquals(VideoCodecClass.Unconfirmed, videoCodecClass(listOf("video/av01")))
        assertEquals(VideoCodecClass.Unconfirmed, videoCodecClass(listOf("video/x-vnd.on2.vp9")))
        // The composer offers a transcode instead, and only that makes them a video.
        assertTrue(videoCodecNeedsConversion("video/mp4", listOf("video/av01")))
        assertTrue(videoCodecNeedsConversion("video/mp4", listOf("video/x-vnd.on2.vp9")))
        assertFalse(videoIsAlreadyTargetCodec(listOf("video/av01")))
    }

    @Test
    fun mixedOrUnknownVideoCodecsAreNotPromised() {
        assertFalse(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/avc", "video/hevc")))
        assertFalse(videoCanBeSentAsVideoMessage("video/mp4", listOf("video/mp4v")))
        assertEquals(VideoCodecClass.Unconfirmed, videoCodecClass(listOf("video/mp4v")))
    }

    @Test
    fun otherContainersStayDocuments() {
        assertFalse(videoCanBeSentAsVideoMessage("video/x-matroska", listOf("video/avc")))
        assertFalse(videoCanBeSentAsVideoMessage("video/webm", listOf("video/x-vnd.on2.vp9")))
        assertFalse(videoCanBeSentAsVideoMessage("video/quicktime", listOf("video/avc")))
        // A container we never send as video is not offered a compatibility transcode either.
        assertFalse(videoCodecNeedsConversion("video/x-matroska", listOf("video/av01")))
    }

    @Test
    fun missingVideoTrackIsNotAVideo() {
        assertFalse(videoCanBeSentAsVideoMessage("video/mp4", emptyList()))
        assertEquals(VideoCodecClass.Unconfirmed, videoCodecClass(emptyList()))
        assertFalse(videoIsAlreadyTargetCodec(emptyList()))
    }
}