package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A queue edit during the native BG Mix transition was deferred and then applied after the crossfade. Build 815 picked
 * the edited row with `indexOfFirst(...).coerceAtLeast(0)`, so removing the incoming track could jump playback to the
 * first track of the queue. The logical index must follow the occurrence that is playing.
 */
class BgMixQueueEditTest {

    private fun track(key: String, occurrence: String = key) = PlaybackItem(
        key = key,
        kind = MediaKind.Audio,
        path = "/music/$key.mp3",
        title = key,
        durationSeconds = 180,
        occurrenceId = occurrence,
    )

    @Test
    fun thePlayingOccurrenceKeepsItsPlaceWhenTheIncomingTrackIsRemoved() {
        val playing = track("b")
        val queue = listOf(track("a"), playing, track("d"))
        assertEquals(1, bgMixLogicalIndex(queue, playing.occurrenceId, playing.key, fallbackIndex = 1))
    }

    @Test
    fun aRemovedIncomingTrackNeverTurnsIntoIndexZero() {
        // The playing occurrence is gone from the edited queue (a stale edit). The old position is kept, clamped,
        // instead of blindly starting the queue from its first track.
        val queue = listOf(track("x"), track("y"), track("z"))
        assertEquals(2, bgMixLogicalIndex(queue, occurrenceId = "b", key = "b", fallbackIndex = 2))
        assertEquals(1, bgMixLogicalIndex(queue, occurrenceId = null, key = "missing", fallbackIndex = 1))
    }

    @Test
    fun anExactOccurrenceWinsOverARepeatedKey() {
        val first = track("same", occurrence = "first")
        val second = track("same", occurrence = "second")
        val queue = listOf(track("a"), first, track("c"), second)
        assertEquals(1, bgMixLogicalIndex(queue, "first", "same", fallbackIndex = 3))
        assertEquals(3, bgMixLogicalIndex(queue, "second", "same", fallbackIndex = 1))
    }

    @Test
    fun anEmptyQueueHasNoLogicalIndex() {
        assertEquals(-1, bgMixLogicalIndex(emptyList(), "a", "a", fallbackIndex = 0))
    }
}