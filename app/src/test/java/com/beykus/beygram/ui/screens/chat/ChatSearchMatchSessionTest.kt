package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchMatchSessionTest {
    private fun msg(id: Long) = ChatMessage(id = id, chatId = 1L, text = "hit $id", time = "12:00", isOutgoing = false)

    @Test
    fun steppingMovesBetweenOlderAndNewerMatchesWithinLoadedPage() {
        val initial = ChatSearchMatchSession(
            query = "hit",
            results = listOf(msg(30L), msg(20L), msg(10L)),
            index = 1,
            totalCount = 3
        )
        assertTrue(initial.isActive)
        assertTrue(initial.canStepOlder)
        assertTrue(initial.canStepNewer)
        assertEquals(20L, initial.currentMessageId)

        val older = initial.stepOlderLoaded()
        assertEquals(2, older.index)
        assertEquals(10L, older.currentMessageId)
        assertFalse(older.canStepOlder)
        assertTrue(older.canStepNewer)

        val newer = initial.stepNewer().stepNewer()
        assertEquals(0, newer.index)
        assertEquals(30L, newer.currentMessageId)
        assertFalse(newer.canStepNewer)
        assertTrue(newer.canStepOlder)
    }

    @Test
    fun appendPageAdvancesToFirstNewOlderMatchAndDeduplicates() {
        val initial = ChatSearchMatchSession(
            query = "hit",
            results = listOf(msg(30L), msg(20L)),
            index = 1,
            totalCount = 4,
            nextFromMessageId = 20L,
            busy = true
        )
        assertFalse(initial.canStepOlder)

        val updated = initial.appendPage(listOf(msg(20L), msg(15L), msg(10L)), nextId = 0L)
        assertFalse(updated.busy)
        assertEquals(listOf(30L, 20L, 15L, 10L), updated.results.map { it.id })
        assertEquals(2, updated.index)
        assertEquals(15L, updated.currentMessageId)
        assertTrue(updated.canStepOlder)
        assertEquals(4, updated.totalDisplayCount)
    }
}
