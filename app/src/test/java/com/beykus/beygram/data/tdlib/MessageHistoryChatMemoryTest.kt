package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatMemoryMark
import com.beykus.beygram.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageHistoryChatMemoryTest {
    private fun message(id: Long, chat: Long = CHAT) = ChatMessage(id = id, chatId = chat, text = "text $id", time = "10:00", isOutgoing = false)

    @Test
    fun aRetiredMessageStaysOutUnlessItIsKeptAsDeleted() {
        val store = MessageHistoryStore()
        store.put(message(1))
        store.delete(CHAT, listOf(1L))

        // A copy from a page that was in flight when the message was deleted must not bring it back.
        store.finishLoad(CHAT, listOf(message(1)), hasOlder = false)
        assertEquals(emptyList<Long>(), store.observe(CHAT).value.messages.map { it.id })

        // The copy Chat Memory kept is what the screen shows for that deletion, so it comes back marked.
        store.finishLoad(CHAT, listOf(message(1).copy(memory = ChatMemoryMark(deletedAtMillis = 500L))), hasOlder = false)
        assertEquals(ChatMemoryMark(deletedAtMillis = 500L), store.observe(CHAT).value.messages.single().memory)
    }

    @Test
    fun dropAllChatMemoryTakesTheKeptMessagesOfEveryLoadedChatOffTheScreen() {
        val store = MessageHistoryStore()
        store.put(message(1))
        store.put(message(2))
        store.markDeleted(CHAT, listOf(2L), deletedAtMillis = 500L)
        store.put(message(3, chat = OTHER_CHAT))
        store.markDeleted(OTHER_CHAT, listOf(3L), deletedAtMillis = 500L)

        store.dropAllChatMemory()

        assertEquals(listOf(1L), store.observe(CHAT).value.messages.map { it.id })
        assertEquals(emptyList<Long>(), store.observe(OTHER_CHAT).value.messages.map { it.id })
        assertTrue(store.chatsWithChatMemoryMarks().isEmpty())
    }

    @Test
    fun aKeptDeletionStaysOnScreenAsAMarkedMessage() {
        val store = MessageHistoryStore()
        store.put(message(1))
        store.put(message(2))

        store.markDeleted(CHAT, listOf(2L), deletedAtMillis = 500L)

        val messages = store.observe(CHAT).value.messages
        assertEquals(listOf(1L, 2L), messages.map { it.id })
        assertNull(messages.first { it.id == 1L }.memory)
        assertEquals(ChatMemoryMark(deletedAtMillis = 500L), messages.first { it.id == 2L }.memory)
    }

    @Test
    fun removingOtherMessagesLeavesTheKeptOne() {
        val store = MessageHistoryStore()
        store.put(message(1))
        store.put(message(2))
        store.markDeleted(CHAT, listOf(2L), deletedAtMillis = 500L)

        store.delete(CHAT, listOf(1L))

        assertEquals(listOf(2L), store.observe(CHAT).value.messages.map { it.id })
    }

    private companion object {
        const val CHAT = 77L
        const val OTHER_CHAT = 78L
    }
}
