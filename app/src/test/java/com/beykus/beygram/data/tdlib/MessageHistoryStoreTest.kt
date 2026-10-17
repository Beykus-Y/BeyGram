package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageHistoryStoreTest {
    private fun message(id: Long, date: Long = 1L, state: MessageSendState = MessageSendState.Sent, chatId: Long = 1L) =
        ChatMessage(id, chatId, "test", "12:00", true, date, state)

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

    @Test fun snapshotRequestedBeforeAnUpdateCannotRollItBack() {
        val store = MessageHistoryStore()
        store.put(message(1L))
        store.beginLoad(1L)
        val requestVersion = store.version()
        store.update(1L, 1L) { it.copy(isEdited = true) }
        store.finishLoad(1L, listOf(message(1L)), hasOlder = false, requestVersion = requestVersion)
        assertTrue(store.observe(1L).value.messages.single().isEdited)
    }

    @Test fun coldChatsBeyondTheLimitLoseMessagesWhileOpenUnsentAndLoadingChatsKeepTheirs() {
        val store = MessageHistoryStore(maxCachedChats = 2)
        store.opened(1L)
        store.put(message(1L, chatId = 1L))
        store.put(message(2L, chatId = 2L, state = MessageSendState.Sending))
        store.put(message(3L, chatId = 3L))
        assertEquals(listOf(3L), store.observe(3L).value.messages.map { it.id })
        store.put(message(4L, chatId = 4L))
        assertEquals(listOf(1L), store.observe(1L).value.messages.map { it.id })
        assertEquals(listOf(2L), store.observe(2L).value.messages.map { it.id })
        assertEquals(emptyList<ChatMessage>(), store.observe(3L).value.messages)
        assertEquals(listOf(4L), store.observe(4L).value.messages.map { it.id })
    }

    @Test fun loadingChatIsNotEvicted() {
        val store = MessageHistoryStore(maxCachedChats = 1)
        store.beginLoad(1L)
        store.put(message(2L, chatId = 2L))
        store.put(message(3L, chatId = 3L))
        assertTrue(store.observe(1L).value.isLoading)
        assertEquals(emptyList<ChatMessage>(), store.observe(2L).value.messages)
    }

    @Test fun recentlyUsedColdChatsSurviveWhileTheLeastRecentlyUsedGoes() {
        val store = MessageHistoryStore(maxCachedChats = 2)
        store.put(message(1L, chatId = 1L))
        store.put(message(2L, chatId = 2L))
        store.put(message(3L, chatId = 1L))
        store.put(message(4L, chatId = 3L))
        assertEquals(listOf(1L, 3L), store.observe(1L).value.messages.map { it.id })
        assertEquals(emptyList<ChatMessage>(), store.observe(2L).value.messages)
        assertEquals(listOf(4L), store.observe(3L).value.messages.map { it.id })
    }

    @Test fun closedChatIsEvictedLastBecauseItWasUsedLast() {
        val store = MessageHistoryStore(maxCachedChats = 2)
        store.opened(1L)
        store.put(message(1L, chatId = 1L))
        store.put(message(2L, chatId = 2L))
        store.closed(1L)
        store.put(message(3L, chatId = 3L))
        assertEquals(listOf(1L), store.observe(1L).value.messages.map { it.id })
        assertEquals(emptyList<ChatMessage>(), store.observe(2L).value.messages)
    }

    @Test fun evictionKeepsReadMarkers() {
        val store = MessageHistoryStore(maxCachedChats = 1)
        store.readInbox(1L, 5L)
        store.readOutbox(1L, 6L)
        store.put(message(1L, chatId = 1L))
        store.put(message(2L, chatId = 2L))
        assertEquals(emptyList<ChatMessage>(), store.observe(1L).value.messages)
        assertEquals(5L, store.observe(1L).value.lastReadInboxMessageId)
        assertEquals(6L, store.observe(1L).value.lastReadOutboxMessageId)
    }

    @Test fun retiredIdsOfABulkDeleteStayUntilTheRunningLoadEnds() {
        val store = MessageHistoryStore()
        val ids = (1L..300L).toList()
        store.beginLoad(1L)
        store.delete(1L, ids)
        store.finishLoad(1L, ids.map { message(it) }, hasOlder = false, continueLoading = true)
        assertTrue(store.observe(1L).value.messages.isEmpty())
        store.finishLoad(1L, ids.map { message(it) }, hasOlder = false)
        assertTrue(store.observe(1L).value.messages.isEmpty())
    }
}
