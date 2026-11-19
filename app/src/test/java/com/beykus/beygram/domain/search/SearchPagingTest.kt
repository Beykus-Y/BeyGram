package com.beykus.beygram.domain.search

import com.beykus.beygram.domain.model.ChatSearchFilter
import com.beykus.beygram.domain.model.SharedMediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPagingTest {

    @Test
    fun appendSkipsOverlapAndKeepsOrder() {
        val first = listOf(1 to "a", 2 to "b")
        val second = listOf(2 to "b", 3 to "c")
        val merged = appendDistinctBy(first, second) { it.first }
        assertEquals(listOf(1 to "a", 2 to "b", 3 to "c"), merged)
    }

    @Test
    fun appendToEmptyKeepsIncoming() {
        val merged = appendDistinctBy(emptyList<Int>(), listOf(5, 6, 5)) { it }
        assertEquals(listOf(5, 6), merged)
    }

    @Test
    fun appendWithEmptyIncomingKeepsExisting() {
        val merged = appendDistinctBy(listOf(1, 2), emptyList()) { it }
        assertEquals(listOf(1, 2), merged)
    }

    @Test
    fun appendDropsDuplicatesInsideIncoming() {
        val merged = appendDistinctBy(listOf(1), listOf(2, 2, 3)) { it }
        assertEquals(listOf(1, 2, 3), merged)
    }

    @Test
    fun staleQueryIsNotCurrent() {
        assertTrue(isCurrentSearch(" котик ", "котик"))
        assertFalse(isCurrentSearch("котик", "кот"))
    }

    @Test
    fun staleChatSearchAlsoChecksTheFilter() {
        val filter = ChatSearchFilter(senderId = 7L, kind = SharedMediaKind.Files)
        assertTrue(isCurrentChatSearch(" report ", filter, "report", filter))
        assertFalse(isCurrentChatSearch("report", filter, "report", filter.copy(kind = SharedMediaKind.Links)))
        assertFalse(isCurrentChatSearch("report", filter, "invoice", filter))
    }

    @Test
    fun sameMessageIdInDifferentChatsIsNotDropped() {
        // Идентификаторы сообщений уникальны только внутри чата: ключом служит пара (chatId, id).
        val first = listOf(Triple(10L, 7L, "a"))
        val second = listOf(Triple(11L, 7L, "b"), Triple(10L, 7L, "a"))
        val merged = appendDistinctBy(first, second) { it.first to it.second }
        assertEquals(listOf(Triple(10L, 7L, "a"), Triple(11L, 7L, "b")), merged)
    }

    @Test
    fun autoLoadOnlyNearEndWithoutBusyErrorOrStale() {
        assertTrue(shouldAutoLoadChatSearchPage(lastVisibleIndex = 40, resultCount = 45, hasMore = true, busy = false, failed = false, stale = false))
        assertFalse(shouldAutoLoadChatSearchPage(lastVisibleIndex = 10, resultCount = 45, hasMore = true, busy = false, failed = false, stale = false))
        assertFalse(shouldAutoLoadChatSearchPage(40, 45, hasMore = false, busy = false, failed = false, stale = false))
        assertFalse(shouldAutoLoadChatSearchPage(40, 45, hasMore = true, busy = true, failed = false, stale = false))
        assertFalse(shouldAutoLoadChatSearchPage(40, 45, hasMore = true, busy = false, failed = true, stale = false))
        assertFalse(shouldAutoLoadChatSearchPage(40, 45, hasMore = true, busy = false, failed = false, stale = true))
        assertFalse(shouldAutoLoadChatSearchPage(0, 0, hasMore = true, busy = false, failed = false, stale = false))
    }

    @Test
    fun repeatedCursorStopsPaging() {
        assertEquals(0L, nextChatSearchCursor(requestedCursor = 500L, returnedCursor = 500L))
        assertEquals(420L, nextChatSearchCursor(requestedCursor = 500L, returnedCursor = 420L))
        assertEquals(0L, nextChatSearchCursor(requestedCursor = 0L, returnedCursor = 0L))
    }
}
