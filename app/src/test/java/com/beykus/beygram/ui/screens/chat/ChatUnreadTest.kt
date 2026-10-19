package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ChatUnreadTest {
    private fun message(id: Long, outgoing: Boolean = false) = ChatMessage(id = id, chatId = 1L, text = "", time = "", isOutgoing = outgoing)

    /** Items are newest first, as the reversed list shows them. */
    private fun items(vararg messages: ChatMessage) = messages.map { ChatMessageListItem("message:${it.id}", listOf(it)) }

    private fun List<ChatMessageListItem>.keys() = map { it.key }

    @Test fun separatorGoesAboveOldestUnreadIncomingMessage() {
        val list = items(message(5), message(4), message(3), message(2), message(1))
        val result = withUnreadSeparator(list, lastReadInboxId = 3)
        assertEquals(listOf("message:5", "message:4", UNREAD_SEPARATOR_KEY, "message:3", "message:2", "message:1"), result.keys())
        assertEquals(true, result[2].unreadSeparator)
    }

    @Test fun separatorIsNotAddedWithoutUnreadMessages() {
        val list = items(message(3), message(2), message(1))
        assertSame(list, withUnreadSeparator(list, lastReadInboxId = 3))
    }

    @Test fun separatorIsNotAddedWhenNoReadMessageIsLoadedBelowUnread() {
        val list = items(message(3), message(2), message(1))
        assertSame(list, withUnreadSeparator(list, lastReadInboxId = 0))
    }

    @Test fun outgoingMessagesAreNotCountedAsUnread() {
        val list = items(message(5), message(4, outgoing = true), message(3), message(2), message(1))
        val result = withUnreadSeparator(list, lastReadInboxId = 2)
        assertEquals(listOf("message:5", "message:4", "message:3", UNREAD_SEPARATOR_KEY, "message:2", "message:1"), result.keys())
    }

    @Test fun pendingItemsWithoutMessagesAreSkipped() {
        val list = listOf(ChatMessageListItem("pending-video:a", emptyList())) + items(message(3), message(2), message(1))
        val result = withUnreadSeparator(list, lastReadInboxId = 2)
        assertEquals(listOf("pending-video:a", "message:3", UNREAD_SEPARATOR_KEY, "message:2", "message:1"), result.keys())
    }

    @Test fun openingScrollsToSeparatorWhenReadMessageIsLoaded() {
        val list = items(message(5), message(4), message(3), message(2), message(1))
        assertEquals(UnreadOpenStep.Scroll(2), unreadOpenStep(unreadCount = 2, lastReadInboxId = 3, items = list, windowRequested = false))
    }

    @Test fun openingDoesNothingWithoutUnreadOrBoundary() {
        val list = items(message(3), message(2), message(1))
        assertEquals(UnreadOpenStep.Done, unreadOpenStep(unreadCount = 0, lastReadInboxId = 3, items = list, windowRequested = false))
        assertEquals(UnreadOpenStep.Done, unreadOpenStep(unreadCount = 4, lastReadInboxId = 0, items = list, windowRequested = false))
    }

    @Test fun openingRequestsWindowOnceWhenBoundaryIsNotLoaded() {
        val list = items(message(9), message(8))
        assertEquals(UnreadOpenStep.RequestWindow, unreadOpenStep(unreadCount = 30, lastReadInboxId = 3, items = list, windowRequested = false))
        assertEquals(UnreadOpenStep.Done, unreadOpenStep(unreadCount = 30, lastReadInboxId = 3, items = list, windowRequested = true))
    }

    @Test fun openingIsDoneWhenLoadedWindowHasNoUnreadNewerThanBoundary() {
        val list = items(message(3), message(2), message(1))
        assertEquals(UnreadOpenStep.Done, unreadOpenStep(unreadCount = 1, lastReadInboxId = 3, items = list, windowRequested = false))
    }

    @Test fun boundaryOnNewestMessageHasNoSeparator() {
        val list = items(message(3), message(2), message(1))
        assertSame(list, withUnreadSeparator(list, lastReadInboxId = 3))
    }
}
