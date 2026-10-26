package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderHeaderTest {
    private fun chat(
        isGroup: Boolean = false,
        isChannel: Boolean = false,
        isSavedMessages: Boolean = false,
        isSecret: Boolean = false
    ) = ChatPreview(
        id = 1L, title = "Chat", initials = "C", lastMessage = UiText.Raw(""), lastMessageTime = "",
        unreadCount = 0, avatarColorArgb = 0L, isGroup = isGroup, isChannel = isChannel,
        isSavedMessages = isSavedMessages, isSecret = isSecret
    )

    private fun message(
        id: Long,
        senderId: Long = 0L,
        senderName: String = "",
        outgoing: Boolean = false,
        serviceText: String? = null,
        dateSeconds: Long = id
    ) = ChatMessage(
        id = id, chatId = 1L, text = "", time = "", isOutgoing = outgoing,
        dateSeconds = dateSeconds, senderId = senderId, senderName = senderName, serviceText = serviceText
    )

    @Test
    fun privateChatDoesNotRepeatThePeerName() {
        val alice = message(1L, senderId = 42L, senderName = "Alice")
        assertFalse(showSenderHeader(chat(), alice, previous = null))
        assertFalse(showSenderHeader(chat(isSecret = true), alice, previous = null))
    }

    @Test
    fun savedMessagesDoNotNeedASenderHeader() {
        // Saved Messages is a private chat with the account itself; its title already identifies the only author.
        val saved = message(1L, senderId = 42L, senderName = "Alice")
        assertFalse(showSenderHeader(chat(isSavedMessages = true), saved, previous = null))
    }

    @Test
    fun broadcastChannelKeepsItsAuthorLabel() {
        val post = message(1L, senderName = "News")
        assertTrue(showSenderHeader(chat(isGroup = true, isChannel = true), post, previous = null))
    }

    @Test
    fun groupShowsTheAuthorOncePerBlock() {
        val group = chat(isGroup = true)
        val alice1 = message(1L, senderId = 42L, senderName = "Alice")
        val alice2 = message(2L, senderId = 42L, senderName = "Alice")
        val bob = message(3L, senderId = 43L, senderName = "Bob")
        val alice3 = message(4L, senderId = 42L, senderName = "Alice")

        assertTrue(showSenderHeader(group, alice1, previous = null))
        assertFalse(showSenderHeader(group, alice2, previous = alice1))
        assertTrue(showSenderHeader(group, bob, previous = alice2))
        assertTrue(showSenderHeader(group, alice3, previous = bob))
    }

    @Test
    fun outgoingMessageStartsANewIncomingBlock() {
        val group = chat(isGroup = true)
        val alice = message(1L, senderId = 42L, senderName = "Alice")
        val mine = message(2L, outgoing = true)
        val aliceAgain = message(3L, senderId = 42L, senderName = "Alice")

        assertTrue(showSenderHeader(group, aliceAgain, previous = mine))
        assertFalse(showSenderHeader(group, mine, previous = alice))
    }

    @Test
    fun serviceEventStartsANewBlock() {
        val group = chat(isGroup = true)
        val alice = message(1L, senderId = 42L, senderName = "Alice")
        val joined = message(2L, senderId = 42L, senderName = "Alice", serviceText = "Alice joined")
        val aliceAgain = message(3L, senderId = 42L, senderName = "Alice")

        assertTrue(showSenderHeader(group, aliceAgain, previous = joined))
    }

    @Test
    fun anonymousAdminsAndChannelSendersUseTheirVisibleName() {
        val group = chat(isGroup = true)
        val anonymousGroup = message(1L, senderName = "Group")
        val anonymousGroupAgain = message(2L, senderName = "Group")
        val channelA = message(3L, senderName = "Channel A")
        val channelB = message(4L, senderName = "Channel B")

        assertTrue(showSenderHeader(group, anonymousGroup, previous = null))
        assertFalse(showSenderHeader(group, anonymousGroupAgain, previous = anonymousGroup))
        assertTrue(showSenderHeader(group, channelA, previous = anonymousGroupAgain))
        assertTrue(showSenderHeader(group, channelB, previous = channelA))
    }

    @Test
    fun blankSenderAndOutgoingMessagesNeverShowAHeader() {
        val group = chat(isGroup = true)
        assertFalse(showSenderHeader(group, message(1L), previous = null))
        assertFalse(showSenderHeader(group, message(2L, outgoing = true, senderName = "Alice"), previous = null))
    }
}
