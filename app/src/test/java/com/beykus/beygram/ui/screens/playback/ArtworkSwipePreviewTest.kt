package com.beykus.beygram.ui.screens.playback

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkSwipePreviewTest {

    private fun item(id: Int) = PlaybackItem(
        key = "ym:$id",
        kind = MediaKind.Audio,
        path = "",
        title = "Track $id",
        durationSeconds = 180,
    )

    @Test
    fun resolvesAdjacentQueueItemsForSwipeDirectionsAndBounds() {
        val q = listOf(item(1), item(2), item(3))
        val middle = PlaybackState(item = q[1], queue = q, queueIndex = 1)
        assertEquals("ym:3", adjacentQueueItem(middle, -1)?.key)
        assertEquals("ym:1", adjacentQueueItem(middle, 1)?.key)
        assertNull(adjacentQueueItem(middle, 0))

        val first = PlaybackState(item = q[0], queue = q, queueIndex = 0)
        assertEquals("ym:2", adjacentQueueItem(first, -1)?.key)
        assertNull(adjacentQueueItem(first, 1))

        val last = PlaybackState(item = q[2], queue = q, queueIndex = 2)
        assertNull(adjacentQueueItem(last, -1))
        assertEquals("ym:2", adjacentQueueItem(last, 1)?.key)
    }

    @Test
    fun clampsSwipeProgressBetweenZeroAndOne() {
        assertEquals(0f, artworkSwipeProgress(0f, 100f), 0.001f)
        assertEquals(0.5f, artworkSwipeProgress(-50f, 100f), 0.001f)
        assertEquals(1f, artworkSwipeProgress(180f, 100f), 0.001f)
    }
}
