package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The artwork swipe is an explicit jump to the neighbouring occurrence, not the Previous button: it never restarts the
 * current track, and the preview and the commit share this one policy. Four seconds into the first track a back-swipe
 * must stay at the edge instead of restarting the song.
 */
class PlaybackSwipeNavigationTest {
    private fun next(index: Int, size: Int, order: List<Int> = (0 until size).toList(), repeat: PlaybackRepeatMode = PlaybackRepeatMode.Off) =
        adjacentPlaybackQueueIndex(size, index, order, repeat, step = 1)

    private fun previous(index: Int, size: Int, order: List<Int> = (0 until size).toList(), repeat: PlaybackRepeatMode = PlaybackRepeatMode.Off) =
        adjacentPlaybackQueueIndex(size, index, order, repeat, step = -1)

    @Test
    fun middleOfAQueueStepsToBothNeighbours() {
        assertEquals(2, next(1, 3))
        assertEquals(0, previous(1, 3))
    }

    @Test
    fun edgesDoNotRestartWithoutRepeat() {
        assertNull(previous(0, 3))
        assertEquals(1, previous(2, 3))
        assertNull(next(2, 3))
        assertEquals(1, next(0, 3))
    }

    @Test
    fun repeatModesWrapAroundTheSameOrder() {
        assertEquals(0, next(2, 3, repeat = PlaybackRepeatMode.All))
        assertEquals(2, previous(0, 3, repeat = PlaybackRepeatMode.All))
        // An explicit swipe advances even with Repeat One; the mode only means the track repeats when it ends.
        assertEquals(0, next(2, 3, repeat = PlaybackRepeatMode.One))
        assertEquals(2, previous(0, 3, repeat = PlaybackRepeatMode.One))
    }

    @Test
    fun shuffleOrderIsThePlaybackOrderNotTheQueueOrder() {
        val order = listOf(2, 0, 1)
        assertEquals(1, next(0, 3, order))
        assertEquals(2, previous(0, 3, order))
        assertNull(next(1, 3, order))
        assertEquals(0, previous(1, 3, order))
        // The current track sits at the end of the shuffled order: Repeat Off has no next.
        assertEquals(0, next(2, 3, order))
        assertNull(previous(2, 3, order))
    }

    @Test
    fun duplicateKeysAreChosenByOccurrenceIndex() {
        // Three rows, the same song at 0 and 2: the neighbour of 0 is the row at 1, never the duplicate occurrence.
        assertEquals(1, next(0, 3))
        assertEquals(1, previous(2, 3))
        assertEquals(2, next(1, 3))
    }

    @Test
    fun aSingleTrackHasNoNeighbour() {
        assertNull(next(0, 1))
        assertNull(previous(0, 1))
        assertNull(next(0, 1, repeat = PlaybackRepeatMode.All))
    }

    @Test
    fun malformedOrOutdatedInputIsRefused() {
        assertNull(adjacentPlaybackQueueIndex(3, 1, listOf(0, 1), PlaybackRepeatMode.Off, step = 1))
        assertNull(adjacentPlaybackQueueIndex(3, 1, listOf(0, 0, 1), PlaybackRepeatMode.Off, step = 1))
        assertNull(adjacentPlaybackQueueIndex(3, 5, step = 1))
        assertNull(adjacentPlaybackQueueIndex(3, 1, step = 0))
    }
}