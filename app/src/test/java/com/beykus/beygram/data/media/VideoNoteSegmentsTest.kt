package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteSegmentsTest {
    @Test fun shortRecordingStaysOneSegment() {
        assertEquals(listOf(0L to 12_000L), videoNoteSegments(12_000L, longRecording = false))
    }

    @Test fun normalRecordingStoppedByTimerBecomesOneCircle() {
        // Таймер останавливает обычную запись на минуте, а CameraX дописывает хвост: хвост отбрасывается.
        assertEquals(listOf(0L to VIDEO_NOTE_SEGMENT_MS), videoNoteSegments(60_250L, longRecording = false))
    }

    @Test fun longRecordingIsCutIntoConsecutiveSegments() {
        assertEquals(
            listOf(0L to 59_500L, 59_500L to 119_000L, 119_000L to 125_000L),
            videoNoteSegments(125_000L, longRecording = true)
        )
    }

    @Test fun tailShorterThanHalfSecondIsDropped() {
        assertEquals(
            listOf(0L to 59_500L, 59_500L to 119_000L),
            videoNoteSegments(119_300L, longRecording = true)
        )
    }

    @Test fun tailOfHalfSecondOrMoreIsKept() {
        assertEquals(119_000L to 119_500L, videoNoteSegments(119_500L, longRecording = true).last())
    }

    @Test fun segmentsAreContiguousAndCoverTheRecording() {
        val segments = videoNoteSegments(125_000L, longRecording = true)
        assertEquals(0L, segments.first().first)
        segments.zipWithNext().forEach { (previous, next) -> assertEquals(previous.second, next.first) }
        assertEquals(125_000L, segments.last().second)
    }

    @Test fun segmentsStayWithinTheLimitAfterEncoderOvershoot() {
        val encoderOvershootMs = 400L
        videoNoteSegments(3_600_000L, longRecording = true).forEach { (startMs, endMs) ->
            assertTrue(isAcceptedVideoNoteDuration(endMs - startMs + encoderOvershootMs))
        }
    }

    @Test fun clipCutAtExactlyOneMinuteIsRejectedWhenContainerOvershoots() {
        assertFalse(isAcceptedVideoNoteDuration(VIDEO_NOTE_MAX_MS + 33L))
    }

    @Test fun durationLimitIsInclusiveAtOneMinute() {
        assertTrue(isAcceptedVideoNoteDuration(VIDEO_NOTE_MAX_MS))
        assertFalse(isAcceptedVideoNoteDuration(VIDEO_NOTE_MAX_MS + 1))
        assertFalse(isAcceptedVideoNoteDuration(0L))
    }
}
