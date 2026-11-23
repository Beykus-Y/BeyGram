package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackLogicalQueueTest {
    private val origin = PlaybackOrigin(1L, 10L)
    private fun track(id: Int, account: String = "acc") = PlaybackItem(
        key = "Audio:$id", kind = MediaKind.Audio, path = "/$id.mp3", title = "T$id", durationSeconds = 100,
        origin = origin, accountId = account
    )

    @Test
    fun appendKeepsOrderAndIndex() {
        val start = PlaybackLogicalQueue().restart(listOf(track(1), track(2)), 1)
        val next = start.appendPage(listOf(track(3), track(4)), start.generation)
        assertEquals(listOf("Audio:1", "Audio:2", "Audio:3", "Audio:4"), next.items.map { it.key })
        assertEquals(1, next.index)
        assertEquals("Audio:2", next.playing?.key)
    }

    @Test
    fun duplicatesAndOtherAccountsAreDropped() {
        val start = PlaybackLogicalQueue().restart(listOf(track(1), track(2)), 0)
        val next = start.appendPage(listOf(track(2), track(3), track(9, account = "other")), start.generation)
        assertEquals(listOf("Audio:1", "Audio:2", "Audio:3"), next.items.map { it.key })
    }

    @Test
    fun pageForAnOlderQueueIsRefused() {
        val old = PlaybackLogicalQueue().restart(listOf(track(1)), 0)
        val replaced = old.restart(listOf(track(5)), 0)
        assertEquals(replaced, replaced.appendPage(listOf(track(6)), old.generation))
    }

    @Test
    fun emptyOrIndexlessQueueTakesNothing() {
        val empty = PlaybackLogicalQueue()
        assertEquals(empty, empty.appendPage(listOf(track(1)), empty.generation))
        assertNull(empty.playing)
    }
}
