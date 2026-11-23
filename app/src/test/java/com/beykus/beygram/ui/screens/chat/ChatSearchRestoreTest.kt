package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatSearchFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchRestoreTest {
    private fun msg(id: Long) = ChatMessage(id = id, chatId = 1L, text = "hit $id", time = "12:00", isOutgoing = false)

    private val session = ChatSearchMatchSession(
        query = "hit",
        filter = ChatSearchFilter(senderId = 7L),
        results = listOf(msg(30L), msg(20L), msg(10L)),
        index = 1,
        totalCount = 3,
        nextFromMessageId = 5L
    )

    @Test
    fun restoresResultsWhenQueryAndFilterAreUnchanged() {
        assertTrue(session.canRestoreResults("hit", session.filter))
    }

    @Test
    fun doesNotRestoreWhenQueryChanged() {
        assertFalse(session.canRestoreResults("hits", session.filter))
    }

    @Test
    fun doesNotRestoreWhenFilterChanged() {
        assertFalse(session.canRestoreResults("hit", ChatSearchFilter()))
    }

    @Test
    fun doesNotRestoreEmptySnapshot() {
        assertFalse(ChatSearchMatchSession(query = "hit").canRestoreResults("hit", ChatSearchFilter()))
    }
}
