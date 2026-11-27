package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OldestUnreadMarkTest {
    @Test fun followsCursorsToTheOldestMatch() = runTest {
        val cursors = mutableListOf<Long>()
        val oldest = oldestUnreadMark {
            cursors += it
            if (it == 0L) UnreadMarkPage(80L, 80L) else UnreadMarkPage(12L, 0L)
        }
        assertEquals(listOf(0L, 80L), cursors)
        assertEquals(12L, oldest)
    }

    @Test fun distinguishesAnEmptyPageFromAFailure() = runTest {
        assertNull(oldestUnreadMark { UnreadMarkPage(null, 0L) })
        val failure = IllegalStateException("lookup failed")
        assertEquals(failure, runCatching { oldestUnreadMark { throw failure } }.exceptionOrNull())
    }

    @Test fun stopsAtRepeatedCursorsWithoutRequestingTheSamePageAgain() = runTest {
        val cursors = mutableListOf<Long>()
        val oldest = oldestUnreadMark {
            cursors += it
            if (it == 0L) UnreadMarkPage(80L, 80L) else UnreadMarkPage(79L, 80L)
        }
        assertEquals(listOf(0L, 80L), cursors)
        assertEquals(79L, oldest)
    }

    @Test fun capsServerPagingAndKeepsTheOldestAvailableMatch() = runTest {
        var requests = 0
        val oldest = oldestUnreadMark {
            requests++
            val id = 100L - requests
            UnreadMarkPage(id, id)
        }
        assertEquals(MAX_UNREAD_MARK_PAGES, requests)
        assertEquals(100L - MAX_UNREAD_MARK_PAGES, oldest)
    }
}
