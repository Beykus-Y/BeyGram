package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceBubbleStateTest {
    private val row = Any()
    private val otherRow = Any()

    @Test fun failedDownloadWithoutWaitingOffersRetry() {
        assertTrue(voiceDownloadFailed(path = null, hasError = true, waiting = false))
    }

    @Test fun retryInProgressKeepsSpinnerInsteadOfFailure() {
        assertFalse(voiceDownloadFailed(path = null, hasError = true, waiting = true))
    }

    @Test fun downloadedVoiceNeverShowsFailure() {
        assertFalse(voiceDownloadFailed(path = "/files/voice.ogg", hasError = true, waiting = false))
    }

    @Test fun noErrorNeverShowsFailure() {
        assertFalse(voiceDownloadFailed(path = null, hasError = false, waiting = false))
    }

    @Test fun errorReleasesThisBubblesRequest() {
        assertNull(voicePendingAfterError(pending = row, row = row, hasError = true))
    }

    @Test fun errorKeepsAnotherBubblesRequest() {
        assertSame(otherRow, voicePendingAfterError(pending = otherRow, row = row, hasError = true))
    }

    @Test fun successKeepsThisBubblesRequest() {
        assertSame(row, voicePendingAfterError(pending = row, row = row, hasError = false))
    }
}
