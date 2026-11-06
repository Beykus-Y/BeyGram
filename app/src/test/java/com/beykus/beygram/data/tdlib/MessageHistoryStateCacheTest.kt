package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageHistoryStateCacheTest {
    private fun message(id: Long, chatId: Long, state: MessageSendState = MessageSendState.Sent) =
        ChatMessage(id, chatId, "test", "12:00", true, id, state)

    /** Touches many chats the way a chat list load does: each one gets a read marker and nothing else. */
    private fun touchChats(store: MessageHistoryStore, from: Long, count: Int = 100) {
        for (chatId in from until from + count) store.readInbox(chatId, 1L)
    }

    @Test fun idleChatStatesAreBoundedAndTheirReadMarkersSurvive() {
        val store = MessageHistoryStore(maxStates = 8)
        store.readInbox(1L, 5L)
        store.readOutbox(1L, 6L)
        touchChats(store, from = 100L)
        assertTrue(store.stateCount() <= 8)
        assertFalse(store.stateHeld(1L))
        val history = store.observe(1L).value
        assertEquals(5L, history.lastReadInboxMessageId)
        assertEquals(6L, history.lastReadOutboxMessageId)
        assertTrue(history.messages.isEmpty())
    }

    @Test fun chatWithMessagesIsNeverDropped() {
        val store = MessageHistoryStore(maxStates = 8)
        store.put(message(1L, chatId = 1L))
        touchChats(store, from = 100L)
        assertTrue(store.stateHeld(1L))
        assertEquals(listOf(1L), store.observe(1L).value.messages.map { it.id })
    }

    @Test fun openChatIsKeptWhileItIsOpenAndDroppedAfterItCloses() {
        val store = MessageHistoryStore(maxStates = 8)
        store.opened(1L)
        touchChats(store, from = 100L)
        assertTrue(store.stateHeld(1L))
        store.closed(1L)
        touchChats(store, from = 300L)
        assertFalse(store.stateHeld(1L))
    }

    @Test fun unsentMessagesKeepAChatAlive() {
        val store = MessageHistoryStore(maxStates = 8)
        store.put(message(1L, chatId = 1L, state = MessageSendState.Sending))
        touchChats(store, from = 100L)
        assertTrue(store.stateHeld(1L))
    }

    @Test fun collectedChatIsKeptAndAHandleFollowsItAfterADrop() = runBlocking {
        val store = MessageHistoryStore(maxStates = 8)
        val handle = store.observe(1L)
        val collection = launch(Dispatchers.Unconfined) { handle.collect { } }
        touchChats(store, from = 100L)
        assertTrue(store.stateHeld(1L))
        collection.cancelAndJoin()
        touchChats(store, from = 300L)
        assertFalse(store.stateHeld(1L))
        store.put(message(7L, chatId = 1L))
        assertEquals(listOf(7L), handle.value.messages.map { it.id })
    }

    @Test fun handleKeptWhileIdleSeesMessagesAfterItsChatWasDropped() {
        val store = MessageHistoryStore(maxStates = 8)
        store.readInbox(1L, 2L)
        val handle = store.observe(1L)
        touchChats(store, from = 100L)
        assertFalse(store.stateHeld(1L))
        store.put(message(7L, chatId = 1L))
        assertEquals(listOf(7L), handle.value.messages.map { it.id })
        assertEquals(2L, handle.value.lastReadInboxMessageId)
    }

    @Test fun aChatLeavingTheMessageCacheIsDroppedAtOnce() {
        val store = MessageHistoryStore(maxCachedChats = 1, maxStates = 64)
        store.opened(1L)
        store.put(message(1L, chatId = 1L))
        store.closed(1L)
        store.opened(2L)
        store.put(message(2L, chatId = 2L))
        store.closed(2L)
        assertFalse(store.stateHeld(1L))
        assertTrue(store.stateHeld(2L))
        assertTrue(store.observe(1L).value.messages.isEmpty())
    }

    @Test fun switchingBetweenChatsKeepsOnlyTheCachedChatsHeldAndAReopenedChatLoadsAgain() {
        val store = MessageHistoryStore(maxCachedChats = 2, maxStates = 64)
        for (chatId in 1L..6L) {
            store.opened(chatId)
            store.put(message(chatId * 10, chatId = chatId))
            store.closed(chatId)
        }
        assertEquals(listOf(5L, 6L), (1L..6L).filter { store.stateHeld(it) })
        store.opened(1L)
        store.put(message(11L, chatId = 1L))
        assertEquals(listOf(11L), store.observe(1L).value.messages.map { it.id })
    }

    @Test fun aChatWithReadMarkersIsDroppedWhenItsScreenClosesAndKeepsThem() {
        val store = MessageHistoryStore(maxStates = 64)
        store.readInbox(1L, 5L)
        store.opened(1L)
        store.closed(1L)
        assertFalse(store.stateHeld(1L))
        assertEquals(5L, store.observe(1L).value.lastReadInboxMessageId)
    }

    @Test fun aCollectedChatLeavesTheMessageCacheButKeepsItsStateUntilNobodyCollectsIt() = runBlocking {
        val store = MessageHistoryStore(maxCachedChats = 1, maxStates = 8)
        store.put(message(1L, chatId = 1L))
        val handle = store.observe(1L)
        val collection = launch(Dispatchers.Unconfined) { handle.collect { } }
        store.put(message(2L, chatId = 2L))
        assertTrue(handle.value.messages.isEmpty())
        assertTrue(store.stateHeld(1L))
        collection.cancelAndJoin()
        touchChats(store, from = 100L)
        assertFalse(store.stateHeld(1L))
    }
}
