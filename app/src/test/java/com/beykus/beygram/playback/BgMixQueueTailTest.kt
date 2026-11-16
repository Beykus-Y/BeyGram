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

    private fun ready(vararg items: PlaybackItem): List<PlaybackItem?> = items.map { it.takeIf { item -> item.path.endsWith(".mp3") } }

    @Test
    fun prefixStartsAtTheTappedItemAndStopsAtTheFirstFileThatIsNotReady() {
        val queue = ready(track("a", "/music/a.stream"), track("b"), track("c"))
        // Items before the tapped one do not matter to BG Mix.
        assertEquals(listOf(1, 2), bgMixReadyPrefix(queue, 1))
        // The tapped item itself must be ready.
        assertNull(bgMixReadyPrefix(queue, 0))
        // A track that is still downloading holds back the ones after it; they join the session later, in order.
        val withGap = ready(track("a"), track("b", "/music/b.stream"), track("c"))
        assertEquals(listOf(0), bgMixReadyPrefix(withGap, 0))
    }

    @Test
    fun preparationAndDownloadsCannotCreepBeyondThePlayingTrack() {
        val size = 100
        for (current in 0 until size) {
            val nativeEnd = bgMixWindowEndExclusive(size, current, BG_MIX_NATIVE_AHEAD)
            val downloadEnd = bgMixWindowEndExclusive(size, current, 2)
            assertEquals(minOf(size, current + 2), nativeEnd)
            assertEquals(minOf(size, current + 3), downloadEnd)
            // Repeated feed ticks with every file ready must not advance the bound.
            repeat(100) {
                assertEquals(nativeEnd, bgMixWindowEndExclusive(size, current, BG_MIX_NATIVE_AHEAD))
            }
        }
        assertEquals(19, bgMixWindowEndExclusive(size, 18, 0)) // Repeat One
        assertEquals(0, bgMixWindowEndExclusive(size, -1, 1))
        assertEquals(0, bgMixWindowEndExclusive(size, size, 1))
        assertEquals(0, bgMixWindowEndExclusive(0, 0, 1))
    }

    @Test
    fun startIndexOutsideTheQueueIsRefused() {
        val queue = ready(track("a"))
        assertNull(bgMixReadyPrefix(queue, 1))
        assertNull(bgMixReadyPrefix(queue, -1))
        assertNull(bgMixReadyPrefix(emptyList(), 0))
    }
}
