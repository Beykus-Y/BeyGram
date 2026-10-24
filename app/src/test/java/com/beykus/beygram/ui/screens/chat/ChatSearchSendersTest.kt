package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSearchSendersTest {
    private fun message(id: Long, senderId: Long, senderName: String) =
        ChatMessage(id = id, chatId = 1L, text = "", time = "", isOutgoing = false, senderId = senderId, senderName = senderName)

    @Test
    fun distinctKnownSendersAreSortedByName() {
        val senders = chatSearchSenders(listOf(
            message(1L, 20L, "Zoe"),
            message(2L, 10L, "alice"),
            message(3L, 20L, "Zoe"),
            message(4L, 30L, "Bob")
        ))
        assertEquals(listOf(10L, 30L, 20L), senders.map { it.userId })
        assertEquals(listOf("alice", "Bob", "Zoe"), senders.map { it.name })
    }

    @Test
    fun channelPostsWithoutAUserSenderAreNotOffered() {
        val senders = chatSearchSenders(listOf(
            message(1L, 0L, "Channel"),
            message(2L, 42L, "Alice")
        ))
        assertEquals(listOf(42L), senders.map { it.userId })
    }

    @Test
    fun missingNameFallsBackToTheUserId() {
        val senders = chatSearchSenders(listOf(message(1L, 42L, "")))
        assertEquals("42", senders.single().name)
    }
}
