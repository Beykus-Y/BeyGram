package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PeekHistoryRequestTest {
    @Test fun `peek asks for the newest page of the chat and nothing else`() {
        val request = peekHistoryRequest(-100123L, PEEK_MESSAGE_LIMIT)
        assertEquals(-100123L, request.chatId)
        // fromMessageId 0 with offset 0 means "from the newest message"; the request is GetChatHistory only.
        assertEquals(0L, request.fromMessageId)
        assertEquals(0, request.offset)
        assertEquals(PEEK_MESSAGE_LIMIT, request.limit)
        assertFalse(request.onlyLocal)
    }

    @Test fun `peek never asks for more than the preview limit`() {
        assertEquals(PEEK_MESSAGE_LIMIT, peekHistoryRequest(1L, 500).limit)
        assertEquals(1, peekHistoryRequest(1L, 0).limit)
    }
}
