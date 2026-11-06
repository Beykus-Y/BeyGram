package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BgMixQueueTailTest {

    private fun track(key: String, path: String = "/music/$key.mp3") = PlaybackItem(
        key = key,
        kind = MediaKind.Audio,
        path = path,
        title = key,
        durationSeconds = 180
    )

    private val localMp3: (PlaybackItem) -> Boolean = { it.path.endsWith(".mp3") }

    @Test
    fun tailStartsAtTheTappedItemAndNeedsEveryItemAfterIt() {
        val queue = listOf(track("a", "/music/a.stream"), track("b"), track("c"))
        // Items before the tapped one do not matter to BG Mix.
        assertEquals(listOf(1, 2), bgMixQueueTail(queue, 1, localMp3))
        // The tapped item itself must be eligible.
        assertNull(bgMixQueueTail(queue, 0, localMp3))
        // One ineligible item after the start sends the whole queue to ExoPlayer, instead of dropping it silently.
        val withGap = listOf(track("a"), track("b", "/music/b.stream"), track("c"))
        assertNull(bgMixQueueTail(withGap, 0, localMp3))
    }

    @Test
    fun startIndexOutsideTheQueueIsRefused() {
        val queue = listOf(track("a"))
        assertNull(bgMixQueueTail(queue, 1) { true })
        assertNull(bgMixQueueTail(queue, -1) { true })
        assertNull(bgMixQueueTail(emptyList(), 0) { true })
    }
}
