package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The export check of a video note (#1228). The rules are pure, so a broken encoder, a missing video track and a
 * container with the wrong codec are covered without a device codec.
 */
class VideoNoteOutputCheckTest {
    private fun facts(
        sizeBytes: Long = 1_024L,
        width: Int = 384,
        height: Int = 384,
        durationMs: Long = 10_000L,
        videoMime: String? = "video/avc",
        decodedFrames: Int = 3,
        expectedSide: Int = 384
    ) = VideoNoteOutputFacts(
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        durationMs = durationMs,
        videoMime = videoMime,
        decodedFrames = decodedFrames,
        expectedSide = expectedSide
    )

    @Test
    fun `a square h264 clip with decodable frames is accepted`() {
        assertNull(videoNoteOutputIssue(facts()))
    }

    @Test
    fun `an empty file is rejected before anything else`() {
        assertEquals(
            VideoNoteOutputIssue.EmptyFile,
            videoNoteOutputIssue(facts(sizeBytes = 0L, decodedFrames = 0, videoMime = "video/hevc"))
        )
    }

    @Test
    fun `a file the decoder cannot open is rejected`() {
        // The reported symptom: the exporter wrote something, the preview is black and nobody can watch it.
        assertEquals(VideoNoteOutputIssue.NoDecodableFrame, videoNoteOutputIssue(facts(decodedFrames = 0)))
    }

    @Test
    fun `a non h264 container is rejected`() {
        assertEquals(VideoNoteOutputIssue.NotH264, videoNoteOutputIssue(facts(videoMime = "video/hevc")))
    }

    @Test
    fun `an unknown container mime does not block a decodable clip`() {
        // Some firmware cannot list tracks; the decoded frames are then the evidence that the file is playable.
        assertNull(videoNoteOutputIssue(facts(videoMime = null)))
    }

    @Test
    fun `a non square clip is rejected`() {
        assertEquals(VideoNoteOutputIssue.NotSquare, videoNoteOutputIssue(facts(width = 480)))
    }

    @Test
    fun `a clip outside the telegram duration limit is rejected`() {
        assertEquals(VideoNoteOutputIssue.BadDuration, videoNoteOutputIssue(facts(durationMs = 0L)))
        assertEquals(VideoNoteOutputIssue.BadDuration, videoNoteOutputIssue(facts(durationMs = VIDEO_NOTE_MAX_MS + 1)))
    }

    @Test
    fun `a clip exactly at the limit is accepted`() {
        assertNull(videoNoteOutputIssue(facts(durationMs = VIDEO_NOTE_MAX_MS)))
    }
}