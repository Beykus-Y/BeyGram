package com.beykus.beygram.data.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlaybackWindowTest {
    @Test
    fun shortPlaylistsAreResolvedInFull() {
        assertEquals(MusicPlayRange(0, 60), musicPlayWindow(60, 30, shuffled = false))
        assertEquals(MusicPlayRange(0, 1), musicPlayWindow(1, 0, shuffled = false))
        assertEquals(MusicPlayRange(0, 0), musicPlayWindow(0, 0, shuffled = false))
    }

    @Test
    fun aShuffledPlaylistIsAlwaysResolvedInFull() {
        assertEquals(MusicPlayRange(0, 1_000), musicPlayWindow(1_000, 500, shuffled = true))
    }

    @Test
    fun aLongPlaylistGetsTheEntriesWithinTheRadiusOfTheStart() {
        assertEquals(MusicPlayRange(480, 521), musicPlayWindow(1_000, 500, shuffled = false))
        assertEquals(MusicPlayRange(40, 61), musicPlayWindow(61, 60, shuffled = false))
    }

    @Test
    fun theWindowIsClampedAtBothEnds() {
        assertEquals(MusicPlayRange(0, 21), musicPlayWindow(1_000, 0, shuffled = false))
        assertEquals(MusicPlayRange(0, 24), musicPlayWindow(1_000, 3, shuffled = false))
        assertEquals(MusicPlayRange(977, 1_000), musicPlayWindow(1_000, 997, shuffled = false))
        assertEquals(MusicPlayRange(979, 1_000), musicPlayWindow(1_000, 999, shuffled = false))
    }

    @Test
    fun aWindowAlwaysHoldsTheStartAndAtMostFortyOneEntries() {
        for (start in 0 until 1_000) {
            val window = musicPlayWindow(1_000, start, shuffled = false)
            assertTrue("start $start in $window", start in window.start until window.endExclusive)
            assertTrue("size of $window", window.size <= 2 * MUSIC_PLAY_WINDOW_RADIUS + 1)
        }
    }

    @Test
    fun pagesStartWhereTheWindowEndsAndCoverTheRestInOrder() {
        val size = 1_000
        val window = musicPlayWindow(size, 500, shuffled = false)
        val pager = MusicPlayPager(size, window.endExclusive)
        val pages = mutableListOf<MusicPlayRange>()
        while (pager.hasMore()) {
            val page = pager.peek()
            pages += page
            pager.advance(page)
        }
        assertEquals(window.endExclusive, pages.first().start)
        assertEquals(size, pages.last().endExclusive)
        for ((previous, next) in pages.zipWithNext()) assertEquals(previous.endExclusive, next.start)
        assertTrue(pages.all { it.size in 1..MUSIC_PLAY_PAGE_SIZE })
        assertEquals(size - window.endExclusive, pages.sumOf { it.size })
    }

    @Test
    fun pagesHoldThirtyEntriesAndTheLastOneMayBeShorter() {
        assertEquals(MusicPlayRange(0, 30), musicPlayPage(0, 100))
        assertEquals(MusicPlayRange(30, 60), musicPlayPage(30, 100))
        assertEquals(MusicPlayRange(90, 100), musicPlayPage(90, 100))
        assertEquals(MusicPlayRange(100, 100), musicPlayPage(100, 100))
        assertEquals(MusicPlayRange(100, 100), musicPlayPage(130, 100))
    }

    @Test
    fun aPagerTakesAPageOnlyWhenItIsAdvancedTo() {
        val pager = MusicPlayPager(size = 100, startCursor = 0)
        val first = pager.peek()
        assertEquals(first, pager.peek())
        pager.advance(first)
        assertEquals(MUSIC_PLAY_PAGE_SIZE, pager.peek().start)
        assertTrue(pager.hasMore())
    }

    @Test(expected = IllegalArgumentException::class)
    fun aPagerRefusesAPageThatDoesNotFollowTheCursor() {
        MusicPlayPager(size = 100, startCursor = 0).advance(MusicPlayRange(30, 60))
    }

    @Test
    fun aPagerWithNothingLeftHasNoMore() {
        assertTrue(!MusicPlayPager(size = 10, startCursor = 10).hasMore())
    }
}
