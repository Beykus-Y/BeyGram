package com.beykus.beygram.ui.screens.calls

import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.CallRecord
import com.beykus.beygram.domain.model.CallStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CallsScreenStateTest {
    private fun call(messageId: Long) = CallRecord(
        messageId = messageId, chatId = 1L, title = "Анна", photo = null, avatarColorArgb = 0L,
        isVideo = false, isOutgoing = false, status = CallStatus.Answered, durationSeconds = 60, dateSeconds = messageId
    )

    private fun page(vararg messageIds: Long, next: String = "") = CallHistoryPage(messageIds.map { call(it) }, next)

    private fun ids(state: CallsScreenState) = state.calls.map { it.messageId }

    @Test fun firstLoadFillsTheListAndOffset() {
        val state = CallsScreenState()
        state.replaceFirstPage(page(2, 1, next = "p2"))
        assertEquals(listOf(2L, 1L), ids(state))
        assertEquals("p2", state.nextOffset)
    }

    @Test fun refreshKeepsOlderPagesThatTheFreshPageOverlaps() {
        val state = CallsScreenState()
        state.replaceFirstPage(page(5, 4, next = "p2"))
        state.appendPage(page(3, 2, next = "p3"))

        state.replaceFirstPage(page(6, 5, 4, next = "p2"))

        assertEquals(listOf(6L, 5L, 4L, 3L, 2L), ids(state))
        assertEquals("p3", state.nextOffset)
    }

    @Test fun refreshWithoutOverlapDropsOlderPagesToAvoidAGap() {
        val state = CallsScreenState()
        state.replaceFirstPage(page(5, 4, next = "p2"))
        state.appendPage(page(3, 2, next = "p3"))

        state.replaceFirstPage(page(9, 8, next = "p9"))

        assertEquals(listOf(9L, 8L), ids(state))
        assertEquals("p9", state.nextOffset)
    }

    @Test fun refreshThatCoversEveryLoadedCallTakesItsOffset() {
        val state = CallsScreenState()
        state.replaceFirstPage(page(5, 4, next = "p2"))
        state.appendPage(page(3, next = "p3"))

        state.replaceFirstPage(page(6, 5, 4, 3, next = "p4"))

        assertEquals(listOf(6L, 5L, 4L, 3L), ids(state))
        assertEquals("p4", state.nextOffset)
    }

    @Test fun appendSkipsCallsThatAreAlreadyListed() {
        val state = CallsScreenState()
        state.replaceFirstPage(page(5, 4, next = "p2"))

        state.appendPage(page(4, 3, next = "p3"))

        assertEquals(listOf(5L, 4L, 3L), ids(state))
        assertEquals("p3", state.nextOffset)
    }
}
