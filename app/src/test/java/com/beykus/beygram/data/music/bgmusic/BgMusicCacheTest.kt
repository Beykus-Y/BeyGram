package com.beykus.beygram.data.music.bgmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BgMusicCacheTest {

    @Test
    fun readingAnEntryKeepsItAndTheOldestUnreadEntryGoesFirst() {
        val cache = BgMusicLruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        assertEquals(1, cache.get("a"))

        cache.put("c", 3)

        assertNull(cache.get("b"))
        assertEquals(1, cache.get("a"))
        assertEquals(3, cache.get("c"))
        assertEquals(2, cache.size())
    }

    @Test
    fun sizeNeverExceedsTheLimit() {
        val cache = BgMusicLruCache<Int, Int>(3)
        repeat(20) { cache.put(it, it * 10) }

        assertEquals(3, cache.size())
        assertNull(cache.get(0))
        assertEquals(190, cache.get(19))
    }

    @Test
    fun clearEmptiesTheCache() {
        val cache = BgMusicLruCache<String, String>(4)
        cache.put("x", "1")

        cache.clear()

        assertNull(cache.get("x"))
        assertEquals(0, cache.size())
    }

    @Test
    fun capacityMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) { BgMusicLruCache<String, Int>(0) }
    }

    @Test
    fun cacheKeysDependOnAccountFileIdAndSize() {
        val key = BgMusicCacheKey("account-1", 7, 100L)

        assertEquals(key, BgMusicCacheKey("account-1", 7, 100L))
        assertNotEquals(key, BgMusicCacheKey("account-2", 7, 100L))
        assertNotEquals(key, BgMusicCacheKey("account-1", 8, 100L))
        assertNotEquals(key, BgMusicCacheKey("account-1", 7, 101L))
    }
}
