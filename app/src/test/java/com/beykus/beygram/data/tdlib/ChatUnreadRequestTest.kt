package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUnreadRequestTest {
    @Test fun markingUnreadSetsTheMarkerWithoutTouchingReadMessages() {
        val request = markChatUnreadRequest(42L)
        assertEquals(42L, request.chatId)
        assertTrue(request.isMarkedAsUnread)
    }

    @Test fun readingClearsTheMarker() {
        val request = markChatReadToggleRequest(42L)
        assertEquals(42L, request.chatId)
        assertFalse(request.isMarkedAsUnread)
    }

    @Test fun emptyChatCanStillClearAManualUnreadMarker() {
        assertTrue(canMarkChatRead(lastMessageId = null, markedAsUnread = true))
        assertTrue(canMarkChatRead(lastMessageId = 10L, markedAsUnread = false))
        assertFalse(canMarkChatRead(lastMessageId = null, markedAsUnread = false))
    }
}
