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
        assertNull(CaptureRetryState.Empty.failedPath)
    }

    @Test
    fun failureKeepsTheCapturePath() {
        val state = CaptureRetryState.Empty.failed("/cache/media-capture/photo.jpg")
        assertTrue(state.canRetry)
        assertEquals("/cache/media-capture/photo.jpg", state.failedPath)
    }

    @Test
    fun blankFailurePathIsNotRetryable() {
        assertFalse(CaptureRetryState.Empty.failed("   ").canRetry)
    }

    @Test
    fun clearingForgetsTheFile() {
        val state = CaptureRetryState.Empty.failed("/tmp/video.mp4").cleared()
        assertFalse(state.canRetry)
        assertNull(state.failedPath)
    }

    @Test
    fun saverSurvivesStringRoundTrip() {
        val saved = CaptureRetryState.Empty.failed("/tmp/photo.jpg")
        val restored = CaptureRetryState(saved.failedPath)
        assertEquals(saved, restored)
        assertEquals(CaptureRetryState.Empty, CaptureRetryState(null))
    }
}
