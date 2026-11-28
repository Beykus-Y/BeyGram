package com.beykus.beygram.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 815 cleared the ended flag when the listener pressed Play/Next at the tail of a radio, and the page result
 * looked only at the published state: the new page was appended but playback never restarted, leaving an endless
 * buffer. These tests pin the resume decision to the explicit request, not to the state alone.
 */
class EndlessQueueRecoveryTest {

    @Test
    fun aManualPlayAtTheTailResumesWhenItsPageArrives() {
        // The UI shows a pending resume, so ended was cleared; the explicit request is what remembers the intent.
        assertTrue(queuePageResumesPlayback(ended = false, intentPlaying = true, endlessWaiting = true, recoveryPending = true))
        // Without that request the exact same state must not start playback: this was the regression.
        assertFalse(queuePageResumesPlayback(ended = false, intentPlaying = true, endlessWaiting = true, recoveryPending = false))
    }

    @Test
    fun anAutomaticWaitAfterNetworkLossResumesOnlyWhileTheQueueIsEnded() {
        assertTrue(queuePageResumesPlayback(ended = true, intentPlaying = true, endlessWaiting = true, recoveryPending = false))
        // A paused or stopped queue keeps its intent but must never start by itself.
        assertFalse(queuePageResumesPlayback(ended = true, intentPlaying = false, endlessWaiting = false, recoveryPending = false))
        assertFalse(queuePageResumesPlayback(ended = false, intentPlaying = false, endlessWaiting = false, recoveryPending = false))
    }

    @Test
    fun anEmptyOrFailedPageDoesNotResumeAnything() {
        assertFalse(queuePageResumesPlayback(ended = false, intentPlaying = true, endlessWaiting = true, recoveryPending = false))
    }

    @Test
    fun theAwaitingPageContractStillRequiresARequestInFlight() {
        assertTrue(
            bgMixAwaitsNextPage(
                finished = true,
                pendingIndex = null,
                endless = true,
                repeat = PlaybackRepeatMode.Off,
                pageInFlight = true,
            )
        )
        assertFalse(
            bgMixAwaitsNextPage(
                finished = true,
                pendingIndex = null,
                endless = true,
                repeat = PlaybackRepeatMode.Off,
                pageInFlight = false,
            )
        )
        // A track that is still waiting for its file is not a radio tail.
        assertFalse(
            bgMixAwaitsNextPage(
                finished = true,
                pendingIndex = 7,
                endless = true,
                repeat = PlaybackRepeatMode.Off,
                pageInFlight = true,
            )
        )
    }
}