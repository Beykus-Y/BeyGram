package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedLruMapTest {
    @Test fun keepsOnlyTheNewestEntries() {
        val map = BoundedLruMap<Long, Int>(3)
        for (id in 1L..10L) map[id] = id.toInt()
        assertEquals(listOf(8L, 9L, 10L), map.keys.toList())
    }

    @Test fun readingAnEntryKeepsItLonger() {
        val map = BoundedLruMap<Long, Int>(3)
        map[1L] = 1
        map[2L] = 2
        map[3L] = 3
        assertEquals(1, map[1L])
        map[4L] = 4
        assertEquals(setOf(1L, 3L, 4L), map.keys)
        assertNull(map[2L])
    }

    @Test fun rewritingAnEntryKeepsItLonger() {
        val map = BoundedLruMap<Long, Int>(2)
        map[1L] = 1
        map[2L] = 2
        map[1L] = 10
        map[3L] = 3
        assertEquals(setOf(1L, 3L), map.keys)
        assertEquals(10, map[1L])
    }

    @Test fun scrollingThroughAManyBubblesStaysBounded() {
        val map = BoundedLruMap<Long, Int>(MEDIA_ORIGIN_LIMIT)
        for (id in 1L..5_000L) map[id] = id.toInt()
        assertEquals(MEDIA_ORIGIN_LIMIT, map.size)
        assertEquals(5_000L, map.keys.last())
        assertNull(map[1L])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsANonPositiveLimit() {
        BoundedLruMap<Long, Int>(0)
    }
}
