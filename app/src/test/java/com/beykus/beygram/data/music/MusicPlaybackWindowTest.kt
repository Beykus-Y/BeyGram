package com.beykus.beygram.data.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun pagesHoldThirtyEntriesAndTheLastOneMayBeShorter() {
        assertEquals(MusicPlayRange(0, 30), musicPlayPage(0, 100))
        assertEquals(MusicPlayRange(30, 60), musicPlayPage(30, 100))
        assertEquals(MusicPlayRange(90, 100), musicPlayPage(90, 100))
        assertEquals(MusicPlayRange(100, 100), musicPlayPage(100, 100))
        assertEquals(MusicPlayRange(100, 100), musicPlayPage(130, 100))
    }

    @Test
    fun laterPagesAreTheTailThenTheHeadInPlaylistOrder() {
        // Window 30..70 of 100 entries: the tail is 71..99 and the head 0..29.
        val window = musicPlayWindow(100, 50, shuffled = false)
        assertEquals(MusicPlayRange(30, 71), window)
        assertEquals(listOf(MusicPlayRange(71, 100), MusicPlayRange(0, 30)), musicPlayLaterPages(100, window))
    }

    @Test
    fun longerTailsAndHeadsAreCutIntoPagesOfThirty() {
        // Window 80..120 of 200 entries: the tail is 121..199 and the head 0..79.
        val window = musicPlayWindow(200, 100, shuffled = false)
        assertEquals(
            listOf(
                MusicPlayRange(121, 151),
                MusicPlayRange(151, 181),
                MusicPlayRange(181, 200),
                MusicPlayRange(0, 30),
                MusicPlayRange(30, 60),
                MusicPlayRange(60, 80)
            ),
            musicPlayLaterPages(200, window)
        )
    }

    @Test
    fun aWindowAtTheStartLeavesOnlyATail() {
        // Window 0..20 of 200 entries: the tail is 21..199 and there is no head.
        val pages = musicPlayLaterPages(200, musicPlayWindow(200, 0, shuffled = false))
        assertEquals(MusicPlayRange(21, 51), pages.first())
        assertEquals(MusicPlayRange(171, 200), pages.last())
        assertEquals(179, pages.sumOf { it.size })
    }

    @Test
    fun aWindowAtTheEndLeavesOnlyAHead() {
        // Window 179..199 of 200 entries: there is no tail, and the head is 0..178.
        val pages = musicPlayLaterPages(200, musicPlayWindow(200, 199, shuffled = false))
        assertEquals(MusicPlayRange(0, 30), pages.first())
        assertEquals(MusicPlayRange(150, 179), pages.last())
        assertEquals(179, pages.sumOf { it.size })
    }

    @Test
    fun noLaterPagesWhenTheWindowHoldsTheWholePlaylist() {
        assertTrue(musicPlayLaterPages(50, musicPlayWindow(50, 10, shuffled = false)).isEmpty())
        assertTrue(musicPlayLaterPages(1_000, musicPlayWindow(1_000, 500, shuffled = true)).isEmpty())
        assertTrue(musicPlayLaterPages(0, musicPlayWindow(0, 0, shuffled = false)).isEmpty())
    }

    @Test
    fun theTailRunsToTheEndBeforeTheHeadStartsAtTheFirstEntry() {
        val window = musicPlayWindow(200, 100, shuffled = false)
        val pages = musicPlayLaterPages(200, window)
        val tail = pages.takeWhile { it.start >= window.endExclusive }
        val head = pages.drop(tail.size)
        assertEquals(window.endExclusive, tail.first().start)
        assertEquals(200, tail.last().endExclusive)
        assertEquals(0, head.first().start)
        assertEquals(window.start, head.last().endExclusive)
    }

    @Test
    fun everyPositionOutsideTheWindowIsPlannedExactlyOnce() {
        for (size in listOf(61, 100, 200, 1_001)) {
            for (start in listOf(0, 1, 20, 21, 40, size / 2, size - 22, size - 21, size - 1)) {
                val window = musicPlayWindow(size, start, shuffled = false)
                val planned = IntArray(size)
                for (position in window.start until window.endExclusive) planned[position]++
                for (page in musicPlayLaterPages(size, window)) {
                    assertTrue("page $page of $size entries", page.size in 1..MUSIC_PLAY_PAGE_SIZE)
                    for (position in page.start until page.endExclusive) planned[position]++
                }
                assertTrue("start $start of $size entries", planned.all { it == 1 })
            }
        }
    }

    @Test
    fun aPagerHandsOutItsPagesInOrderAndTakesEachOnlyWhenAdvancedTo() {
        val pager = MusicPlayPager(musicPlayLaterPages(100, musicPlayWindow(100, 50, shuffled = false)))
        val first = pager.peek()
        assertEquals(MusicPlayRange(71, 100), first)
        assertEquals(first, pager.peek())
        pager.advance(first)
        assertEquals(MusicPlayRange(0, 30), pager.peek())
        pager.advance(pager.peek())
        assertFalse(pager.hasMore())
    }

    @Test(expected = IllegalArgumentException::class)
    fun aPagerRefusesAPageThatIsNotNext() {
        MusicPlayPager(listOf(MusicPlayRange(71, 100), MusicPlayRange(0, 30))).advance(MusicPlayRange(0, 30))
    }

    @Test
    fun aPagerWithNoPagesHasNoMore() {
        assertFalse(MusicPlayPager(emptyList()).hasMore())
    }
}
