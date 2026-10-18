package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSearchPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMediaWindowTest {
    private fun message(id: Long) = ChatMessage(id = id, chatId = 1L, text = "", time = "", isOutgoing = false)

    @Test fun olderPagesFollowCursorUntilZeroAndKeepIdsUnique() = runBlocking {
        val requested = mutableListOf<Pair<Long, Boolean>>()
        val pages = ArrayDeque(listOf(
            MessageSearchPage(listOf(message(3), message(2)), 77L),
            MessageSearchPage(listOf(message(2), message(1)), 0L)
        ))
        val window = SharedMediaWindow(load = { from, newer -> requested += from to newer; Result.success(pages.removeFirst()) }, failure = { "failed" })
        window.restart(null)
        window.loadOlder()
        window.loadOlder()
        window.loadOlder()
        assertEquals(listOf(0L to false, 77L to false), requested)
        assertEquals(listOf(1L, 2L, 3L), window.messages.map { it.id })
        assertTrue(window.loaded)
        assertFalse(window.hasOlder)
        assertFalse(window.hasNewer)
    }

    @Test fun newerPagesStartFromNewestLoadedAndStopWhenNothingIsNew() = runBlocking {
        val requested = mutableListOf<Pair<Long, Boolean>>()
        val pages = ArrayDeque(listOf(
            MessageSearchPage(listOf(message(10), message(9), message(8)), 7L),
            MessageSearchPage(listOf(message(13), message(12), message(11), message(10)), 0L),
            MessageSearchPage(listOf(message(13)), 0L)
        ))
        val window = SharedMediaWindow(load = { from, newer -> requested += from to newer; Result.success(pages.removeFirst()) }, failure = { "failed" })
        window.restart(10L)
        window.loadOlder()
        window.loadNewer()
        window.loadNewer()
        assertEquals(listOf(10L to false, 10L to true, 13L to true), requested)
        assertEquals(listOf(8L, 9L, 10L, 11L, 12L, 13L), window.messages.map { it.id })
        assertTrue(window.hasOlder)
        assertFalse(window.hasNewer)
    }

    @Test fun failedPageKeepsCursorForRetry() = runBlocking {
        val requested = mutableListOf<Pair<Long, Boolean>>()
        val responses = ArrayDeque<Result<MessageSearchPage>>(listOf(
            Result.success(MessageSearchPage(listOf(message(5)), 40L)),
            Result.failure(IllegalStateException("boom")),
            Result.success(MessageSearchPage(listOf(message(4)), 0L))
        ))
        val window = SharedMediaWindow(load = { from, newer -> requested += from to newer; responses.removeFirst() }, failure = { "Не удалось" })
        window.restart(null)
        window.loadOlder()
        window.loadOlder()
        assertEquals("Не удалось", window.error)
        assertTrue(window.hasOlder)
        window.loadOlder()
        assertNull(window.error)
        assertEquals(listOf(0L to false, 40L to false, 40L to false), requested)
        assertEquals(listOf(4L, 5L), window.messages.map { it.id })
    }

    @Test fun loadNearMeasuresEdgeAmongVisibleMessagesOnly() = runBlocking {
        val requested = mutableListOf<Pair<Long, Boolean>>()
        val pages = ArrayDeque(listOf(
            MessageSearchPage((11L..20L).map { message(it) }, 10L),
            MessageSearchPage((1L..10L).map { message(it) }, 0L)
        ))
        val window = SharedMediaWindow(load = { from, newer -> requested += from to newer; Result.success(pages.removeFirst()) }, failure = { "failed" })
        window.restart(null)
        window.loadOlder()
        // Шесть самых старых скрыты просмотрщиком: без фильтра 17 стоял бы на седьмом месте и старые не догрузились бы.
        window.loadNear(17L) { it.id >= 17L }
        assertEquals(listOf(0L to false, 10L to false), requested)
        assertEquals((1L..20L).toList(), window.messages.map { it.id })
    }
}
