package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MessageHistoryStoreTest {
    private fun message(id: Long, date: Long = 1L, state: MessageSendState = MessageSendState.Sent) =
        ChatMessage(id, 1L, "test", "12:00", true, date, state)

    @Test fun successReplacesTemporaryMessageAndLateResponseCannotRestoreIt() {
        val store = MessageHistoryStore()
        val pending = message(10L, state = MessageSendState.Sending)
        store.put(pending)
        store.put(pending)
        store.put(message(100L), replaces = 10L)
        store.put(pending) // A late SendMessage callback after the success update.
        assertEquals(listOf(message(100L)), store.observe(1L).value.messages)
    }

    @Test fun overlappingHistoryRetainsLiveMessagesWithoutDuplicates() {
        val store = MessageHistoryStore()
        store.put(message(3L, 3L))
        store.finishLoad(1L, listOf(message(2L, 2L), message(1L)), true)
        store.finishLoad(1L, listOf(message(3L, 3L), message(2L, 2L)), true)
        assertEquals(listOf(1L, 2L, 3L), store.observe(1L).value.messages.map { it.id })
    }

    @Test fun failedSendUpdatesExistingMessage() {
        val store = MessageHistoryStore()
        store.put(message(10L, state = MessageSendState.Sending))
        store.put(message(10L, state = MessageSendState.Failed), replaces = 10L)
        assertEquals(listOf(MessageSendState.Failed), store.observe(1L).value.messages.map { it.sendState })
    }

    @Test fun deletedMessageCannotReturnFromAnOlderHistoryRequest() {
        val store = MessageHistoryStore()
        store.put(message(1L))
        store.delete(1L, listOf(1L))
        store.finishLoad(1L, listOf(message(1L)), false)
        assertEquals(emptyList<ChatMessage>(), store.observe(1L).value.messages)
        assertFalse(store.observe(1L).value.isLoading)
    }
}
