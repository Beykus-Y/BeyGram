package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class MessageLinkAndForwardTest {
    @Test fun `message link request targets one message without media offsets`() {
        val request = messageLinkRequest(-100123L, 456L, inMessageThread = false)
        assertEquals(-100123L, request.chatId)
        assertEquals(456L, request.messageId)
        assertEquals(0, request.mediaTimestamp)
        assertEquals(0, request.checklistTaskId)
        assertEquals("", request.pollOptionId)
        assertFalse(request.forAlbum)
        assertFalse(request.inMessageThread)
    }

    @Test fun `thread link keeps comments attached to their thread`() {
        assertTrue(messageLinkRequest(-100123L, 456L, inMessageThread = true).inMessageThread)
    }

    @Test fun `regular forward leaves the original author visible`() {
        val request = forwardMessagesRequest(toChatId = 10L, fromChatId = 20L, messageId = 30L, asCopy = false)
        assertEquals(10L, request.chatId)
        assertEquals(20L, request.fromChatId)
        assertArrayEquals(longArrayOf(30L), request.messageIds)
        assertNull(request.topicId)
        assertFalse(request.sendCopy)
        assertFalse(request.removeCaption)
    }

    @Test fun `copy forward hides the original author and keeps the caption`() {
        val request = forwardMessagesRequest(toChatId = 10L, fromChatId = 20L, messageId = 30L, asCopy = true)
        assertTrue(request.sendCopy)
        assertFalse(request.removeCaption)
        assertArrayEquals(longArrayOf(30L), request.messageIds)
    }

    @Test fun `link permission is separate from forwarding permission`() {
        val linkOnly = com.beykus.beygram.domain.model.MessageCapabilities(canGetLink = true)
        assertTrue(linkOnly.canGetLink)
        assertFalse(linkOnly.canForward)
        val forwardOnly = com.beykus.beygram.domain.model.MessageCapabilities(canForward = true)
        assertTrue(forwardOnly.canForward)
        assertFalse(forwardOnly.canGetLink)
    }

    @Test fun `link requests use the TDLib link function`() {
        // A compile-time guard: the action must return a link, not a message or an info object.
        val request: TdApi.GetMessageLink = messageLinkRequest(1L, 2L, false)
        assertEquals(1L, request.chatId)
    }
}
