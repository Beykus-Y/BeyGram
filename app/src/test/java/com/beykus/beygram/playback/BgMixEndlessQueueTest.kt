package com.beykus.beygram.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgMixEndlessQueueTest {
    @Test
    fun finishedEndlessQueueWaitsForItsNextPage() {
        assertTrue(
            bgMixAwaitsNextPage(
                finished = true,
                pendingIndex = null,
                endless = true,
                repeat = PlaybackRepeatMode.Off,
                pageInFlight = true
            )
        )
    }

    @Test
    fun queueThatIsNotFinishedDoesNotWait() {
        assertFalse(bgMixAwaitsNextPage(false, null, true, PlaybackRepeatMode.Off, true))
    }

    @Test
    fun queueWithAPendingItemDoesNotWait() {
        assertFalse(bgMixAwaitsNextPage(true, 3, true, PlaybackRepeatMode.Off, true))
    }

    @Test
    fun finiteQueueDoesNotWait() {
        assertFalse(bgMixAwaitsNextPage(true, null, false, PlaybackRepeatMode.Off, true))
    }

    @Test
    fun repeatAllDoesNotWait() {
        assertFalse(bgMixAwaitsNextPage(true, null, true, PlaybackRepeatMode.All, true))
    }

    @Test
    fun repeatOneDoesNotWait() {
        assertFalse(bgMixAwaitsNextPage(true, null, true, PlaybackRepeatMode.One, true))
    }

    @Test
    fun noPageRequestDoesNotWait() {
        assertFalse(bgMixAwaitsNextPage(true, null, true, PlaybackRepeatMode.Off, false))
    }
}
