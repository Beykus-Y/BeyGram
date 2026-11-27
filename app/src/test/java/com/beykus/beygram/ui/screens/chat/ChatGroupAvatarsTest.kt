package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatGroupAvatarsTest {
    @Test fun timeAndSeparatorsStartNewGroups() {
        val one = message(1, date = 100)
        val two = message(2, date = 200)
        val three = message(3, date = 600)
        val four = message(4, date = 650)
        val timeline = listOf(four, three, two, one).map { row(it) }

        assertEquals(setOf("1", "3", "4"), groupAvatarRows(timeline, setOf("4")).keys)
        assertEquals(setOf("1", "3"), groupAvatarRows(timeline, emptySet()).keys)
    }

    @Test fun outgoingAndServiceRowsSeparateGroupsButReceiveNoAvatar() {
        val first = message(1)
        val outgoing = message(2).copy(isOutgoing = true)
        val third = message(3)
        val service = message(4).copy(serviceText = "service")
        val fifth = message(5)
        val timeline = listOf(fifth, service, third, outgoing, first).map { row(it) }

        assertEquals(setOf("1", "3", "5"), groupAvatarRows(timeline, emptySet()).keys)
    }

    private fun row(message: ChatMessage) = ChatMessageListItem(message.id.toString(), listOf(message))

    private fun message(id: Long, date: Long = id) =
        ChatMessage(id, 1, "text", "", false, dateSeconds = date, senderId = 5, senderName = "Sender")
}
