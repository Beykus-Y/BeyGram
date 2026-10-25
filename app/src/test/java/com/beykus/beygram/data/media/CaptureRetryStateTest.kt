package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureRetryStateTest {
    @Test
    fun emptyStateCannotRetry() {
        assertFalse(CaptureRetryState.Empty.canRetry)
        assertFalse(CaptureRetryState().canRetry)
        assertNull(CaptureRetryState.Empty.nextPath)
        assertTrue(CaptureRetryState.Empty.failedPaths.isEmpty())
    }

    @Test
    fun multipleFailuresAreRetained() {
        val state = CaptureRetryState.Empty
            .failed("/cache/media-capture/photo.jpg")
            .failed("/cache/media-capture/video.mp4")
        assertTrue(state.canRetry)
        assertEquals(2, state.failedPaths.size)
        assertEquals("/cache/media-capture/photo.jpg", state.nextPath)
    }

    @Test
    fun duplicateFailureIsNotAddedTwice() {
        val state = CaptureRetryState.Empty.failed("/tmp/photo.jpg").failed("/tmp/photo.jpg")
        assertEquals(1, state.failedPaths.size)
    }

    @Test
    fun blankFailurePathIsNotRetryable() {
        assertFalse(CaptureRetryState.Empty.failed("   ").canRetry)
    }

    @Test
    fun clearingOnePathKeepsTheNextCapture() {
        val state = CaptureRetryState.Empty
            .failed("/tmp/first.mp4")
            .failed("/tmp/second.mp4")
            .cleared("/tmp/first.mp4")
        assertEquals(listOf("/tmp/second.mp4"), state.failedPaths)
        assertEquals("/tmp/second.mp4", state.nextPath)
    }

    @Test
    fun clearingAllForgetsEverything() {
        val state = CaptureRetryState.Empty.failed("/tmp/video.mp4").cleared()
        assertFalse(state.canRetry)
        assertNull(state.nextPath)
    }

    @Test
    fun saverRoundTripKeepsEveryPath() {
        val saved = CaptureRetryState.Empty.failed("/tmp/photo.jpg").failed("/tmp/video.mp4")
        val restored = CaptureRetryState(saved.failedPaths.joinToString("\n").split('\n').filter(String::isNotBlank))
        assertEquals(saved, restored)
    }
}
