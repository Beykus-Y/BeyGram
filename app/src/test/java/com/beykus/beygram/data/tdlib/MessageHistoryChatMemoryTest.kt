package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatMemoryMark
import com.beykus.beygram.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageHistoryChatMemoryTest {
    private fun message(id: Long) = ChatMessage(id = id, chatId = CHAT, text = "text $id", time = "10:00", isOutgoing = false)

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
    }
}
