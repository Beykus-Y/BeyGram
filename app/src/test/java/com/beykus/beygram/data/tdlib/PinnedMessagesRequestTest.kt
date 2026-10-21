package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class PinnedMessagesRequestTest {
    @Test fun `initial search includes pinned media without text or sender restrictions`() {
        val request = pinnedMessagesRequest(-100123L, 0L)
        assertEquals(-100123L, request.chatId)
        assertTrue(request.filter is TdApi.SearchMessagesFilterPinned)
        assertEquals("", request.query)
        assertNull(request.senderId)
        assertNull(request.topicId)
        assertEquals(0L, request.fromMessageId)
        assertEquals(0, request.offset)
        assertEquals(50, request.limit)
    }

    @Test fun `next page preserves Telegram cursor and chat scope`() {
        val request = pinnedMessagesRequest(456L, 789000L)
        assertEquals(456L, request.chatId)
        assertEquals(789000L, request.fromMessageId)
        assertTrue(request.filter is TdApi.SearchMessagesFilterPinned)
    }
    @Test fun `forum pins search is scoped to the selected topic`() {
        val request = pinnedMessagesRequest(-100123L, 500L, forumTopicId = 37L)
        assertEquals(-100123L, request.chatId)
        assertEquals(500L, request.fromMessageId)
        assertEquals(37, (request.topicId as TdApi.MessageTopicForum).forumTopicId)
        assertTrue(request.filter is TdApi.SearchMessagesFilterPinned)
    }

}
