package com.beykus.beygram.ui.screens.playback

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSearchTest {

    private fun queueRow(
        index: Int,
        title: String,
        artist: String,
        album: String = "",
    ) = QueueItemUi(
        index = index,
        item = PlaybackItem(
            key = "ym:$index",
            kind = MediaKind.Audio,
            path = "",
            title = "$artist — $title",
            trackTitle = title,
            performer = artist,
            album = album,
            durationSeconds = 180,
        ),
        status = QueueItemStatus.Ready,
        isCurrent = false,
    )

    @Test
    fun filtersByTitleArtistAndAlbumCaseInsensitively() {
        val rows = listOf(
            queueRow(0, "Midnight City", "M83", "Hurry Up"),
            queueRow(1, "Intro", "The xx", "xx"),
            queueRow(2, "Starlight", "Muse", "Black Holes"),
        )

        assertEquals(3, filterQueueRows(rows, "   ").size)
        assertEquals(listOf(0), filterQueueRows(rows, "midnight").map { it.index })
        assertEquals(listOf(2), filterQueueRows(rows, "MUSE").map { it.index })
        assertEquals(listOf(2), filterQueueRows(rows, "black holes").map { it.index })
        assertTrue(filterQueueRows(rows, "nonexistent").isEmpty())
    }

    @Test
    fun jumpToCurrentShowsOnlyWhenCurrentIsScrolledOffscreenAndNotFiltering() {
        assertTrue(
            shouldShowJumpToCurrent(
                currentItemListIndex = 2,
                visibleIndices = listOf(5, 6, 7),
                queryBlank = true,
                hasCurrent = true,
            )
        )
        assertFalse(
            shouldShowJumpToCurrent(
                currentItemListIndex = 2,
                visibleIndices = listOf(1, 2, 3),
                queryBlank = true,
                hasCurrent = true,
            )
        )
        assertFalse(
            shouldShowJumpToCurrent(
                currentItemListIndex = 2,
                visibleIndices = listOf(5, 6, 7),
                queryBlank = false,
                hasCurrent = true,
            )
        )
        assertFalse(
            shouldShowJumpToCurrent(
                currentItemListIndex = 2,
                visibleIndices = listOf(5, 6, 7),
                queryBlank = true,
                hasCurrent = false,
            )
        )
    }
}
