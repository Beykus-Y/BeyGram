package com.beykus.beygram.playback

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackErrorPolicyTest {
    @Test
    fun stalledOrUnresolvedFileIsANetworkWaitEvenWhenWrapped() {
        val wrapped = RuntimeException("outer", IOException("io", PlaybackNetworkWaitException("stalled")))
        assertEquals(PlaybackErrorKind.NetworkWait, classifyPlaybackError(wrapped))
    }

    @Test
    fun anyOtherErrorIsAnUnplayableTrack() {
        assertEquals(PlaybackErrorKind.Unplayable, classifyPlaybackError(IOException("bad header")))
    }

    @Test
    fun networkWaitContinuesUntilTheLimitThenFails() {
        assertEquals(
            PlaybackErrorAction.WaitForNetwork,
            playbackErrorAction(PlaybackErrorKind.NetworkWait, hasNext = true, consecutiveSkips = 0, networkWaitedMs = 1_000L)
        )
        assertEquals(
            PlaybackErrorAction.Fail,
            playbackErrorAction(PlaybackErrorKind.NetworkWait, hasNext = true, consecutiveSkips = 0, networkWaitedMs = NETWORK_WAIT_LIMIT_MS)
        )
    }

    @Test
    fun brokenTrackIsSkippedUpToThreeTimesInARow() {
        for (skips in 0 until MAX_CONSECUTIVE_SKIPS) {
            assertEquals(
                PlaybackErrorAction.SkipToNext,
                playbackErrorAction(PlaybackErrorKind.Unplayable, hasNext = true, consecutiveSkips = skips, networkWaitedMs = 0L)
            )
        }
        assertEquals(
            PlaybackErrorAction.Fail,
            playbackErrorAction(PlaybackErrorKind.Unplayable, hasNext = true, consecutiveSkips = MAX_CONSECUTIVE_SKIPS, networkWaitedMs = 0L)
        )
    }

    @Test
    fun lastBrokenTrackWithoutSuccessorFails() {
        assertEquals(
            PlaybackErrorAction.Fail,
            playbackErrorAction(PlaybackErrorKind.Unplayable, hasNext = false, consecutiveSkips = 0, networkWaitedMs = 0L)
        )
    }

    @Test
    fun queuePageRetriesBackOffThenStop() {
        assertEquals(2_000L, queuePageRetryDelayMs(0))
        assertEquals(5_000L, queuePageRetryDelayMs(1))
        assertEquals(15_000L, queuePageRetryDelayMs(2))
        assertNull(queuePageRetryDelayMs(3))
    }
}
