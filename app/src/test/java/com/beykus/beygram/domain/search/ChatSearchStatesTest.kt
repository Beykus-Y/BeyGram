package com.beykus.beygram.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchStatesTest {

    private fun state(
        criteria: Boolean = true,
        busy: Boolean = false,
        count: Int = 0,
        failed: Boolean = false,
        hasMore: Boolean = false,
        filter: Boolean = false
    ) = chatSearchListState(criteria, busy, count, failed, hasMore, filter)

    @Test
    fun noCriteriaShowsHintEvenWithLeftoverResults() {
        assertEquals(ChatSearchListState.Idle, state(criteria = false, count = 3))
    }

    @Test
    fun firstLoadWithoutResultsIsLoading() {
        assertEquals(ChatSearchListState.Loading, state(busy = true))
    }

    @Test
    fun refreshWithPreviousResultsKeepsList() {
        // Пока идёт обновление, прежние результаты остаются на экране (помечены устаревшими), а не пустым экраном.
        assertEquals(ChatSearchListState.Results, state(busy = true, count = 4))
    }

    @Test
    fun loadMoreErrorKeepsResults() {
        assertEquals(ChatSearchListState.Results, state(count = 20, failed = true, hasMore = true))
    }

    @Test
    fun firstPageErrorIsNotNoMatches() {
        assertEquals(ChatSearchListState.FirstPageError, state(failed = true))
        assertEquals(ChatSearchListState.FirstPageError, state(failed = true, filter = true))
    }

    @Test
    fun emptyAnswerDistinguishesCursorFilterAndPlainEmpty() {
        assertEquals(ChatSearchListState.NoMatchesOnPage, state(hasMore = true))
        assertEquals(ChatSearchListState.NoFilterMatches, state(filter = true))
        assertEquals(ChatSearchListState.NoMatches, state())
    }

    @Test
    fun resultsWinOverFilterAndCursorFlags() {
        assertEquals(ChatSearchListState.Results, state(count = 1, hasMore = true, filter = true))
    }

    @Test
    fun paginationBoundaryAndRepeatedCursorStopAutoLoad() {
        // Ровно на пороге автодогрузка срабатывает; ниже порога нет.
        val threshold = CHAT_SEARCH_AUTO_LOAD_THRESHOLD
        assertTrue(shouldAutoLoadChatSearchPage(lastVisibleIndex = 9 - threshold + 1, resultCount = 10, hasMore = true, busy = false, failed = false, stale = false))
        assertFalse(shouldAutoLoadChatSearchPage(lastVisibleIndex = 9 - threshold, resultCount = 10, hasMore = true, busy = false, failed = false, stale = false))
        // Повторённый курсор обнуляет дальнейшую загрузку, и автодогрузка больше не запускается.
        val next = nextChatSearchCursor(requestedCursor = 77L, returnedCursor = 77L)
        assertFalse(shouldAutoLoadChatSearchPage(lastVisibleIndex = 9, resultCount = 10, hasMore = next != 0L, busy = false, failed = false, stale = false))
    }
}
