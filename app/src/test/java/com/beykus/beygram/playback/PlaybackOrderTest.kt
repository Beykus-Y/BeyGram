package com.beykus.beygram.playback

import androidx.media3.common.util.UnstableApi
import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackOrderTest {
    private fun track(key: String, fileId: Int) = PlaybackItem(
        key = key,
        kind = MediaKind.Audio,
        path = "",
        title = key,
        durationSeconds = 100,
        fileId = fileId
    )

    private val growing = GrowingPlaybackSource(
        totalSize = 100L,
        readablePrefix = { 20L },
        isComplete = { false },
        completedPath = { null },
        isDownloading = { true },
        onNeedMore = {},
        advertiseLength = false
    )

    @Test
    fun lazilyResolvedGrowingSourceIsPublishedWithoutChangingIdentityOrQueue() {
        val current = track("current", 1)
        val other = track("other", 2)
        val state = PlaybackState(
            item = current,
            queue = listOf(other, current),
            queueIndex = 1,
            positionMs = 42_000L
        )
        val resolved = current.copy(path = "/partial.ogg", growing = growing)

        val published = state.withResolvedSource(resolved)

        assertEquals("current", published.item?.key)
        assertEquals("/partial.ogg", published.item?.path)
        assertSame(growing, published.item?.growing)
        assertEquals(listOf("other", "current"), published.queue.map { it.key })
        assertEquals("/partial.ogg", published.queue[1].path)
        assertEquals(1, published.queueIndex)
        assertEquals(42_000L, published.positionMs)
    }

    @Test
    fun sourceHandlePublishesResolvedGrowingSourceOnlyWhenItChanges() {
        val item = track("current", 1)
        var published: PlaybackItem? = null
        var count = 0
        val handle = PlaybackSourceHandle(item, null) {
            published = it
            count++
        }
        val resolved = ResolvedPlaybackSource("/partial.ogg", growing)

        handle.publish(resolved)
        handle.publish(resolved)

        assertEquals(1, count)
        assertEquals("/partial.ogg", published?.path)
        assertSame(growing, published?.growing)
    }

    @Test
    fun playNextInsertsTheTrackImmediatelyAfterCurrentInShuffledPlaybackOrder() {
        val first = track("first", 1)
        val second = track("second", 2)
        val third = track("third", 3)
        val fourth = track("fourth", 4)
        val inserted = track("inserted", 5)
        val queue = listOf(first, second, third, fourth)

        val result = queueAfterPlayNext(
            queue = queue,
            currentIndex = 1,
            item = inserted,
            existingIndex = -1,
            shuffleOrder = listOf(1, 3, 0, 2)
        )

        assertEquals(listOf("first", "second", "inserted", "third", "fourth"), result.queue.map { it.key })
        assertEquals(1, result.queueIndex)
        // Current "second" is order index 1; inserting puts "inserted" (new index 2) directly next.
        assertEquals(listOf(1, 2, 4, 0, 3), result.playbackOrder)
    }

    @Test
    fun playNextMovesAnAlreadyQueuedTrackNextInShuffledOrder() {
        val first = track("first", 1)
        val second = track("second", 2)
        val third = track("third", 3)
        val fourth = track("fourth", 4)
        val queue = listOf(first, second, third, fourth)

        val result = queueAfterPlayNext(
            queue = queue,
            currentIndex = 0,
            item = third,
            existingIndex = 2,
            shuffleOrder = listOf(0, 2, 3, 1)
        )

        assertEquals(listOf("first", "third", "second", "fourth"), result.queue.map { it.key })
        assertEquals(0, result.queueIndex)
        assertEquals(listOf(0, 1, 3, 2), result.playbackOrder)
    }

    @OptIn(UnstableApi::class)
    @Test
    fun explicitShuffleOrderFollowsTheProvidedPlaybackSequence() {
        val order = PlaybackShuffleOrder(listOf(2, 0, 1))
        assertEquals(listOf(2, 0, 1), order.toIndexOrder())
        assertEquals(2, order.firstIndex)
        assertEquals(1, order.lastIndex)
        assertEquals(3, order.length)
    }
}
