package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatSearchFilter
import com.beykus.beygram.domain.model.SharedMediaKind
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchRequestTest {
    @Test
    fun `first page carries chat query cursor and no restrictions`() {
        val request = chatSearchRequest(chatId = -100123L, query = "report", fromMessageId = 0L, filter = ChatSearchFilter())
        assertEquals(-100123L, request.chatId)
        assertEquals("report", request.query)
        assertEquals(0L, request.fromMessageId)
        assertEquals(0, request.offset)
        assertEquals(SEARCH_PAGE_SIZE, request.limit)
        assertNull(request.senderId)
        assertNull(request.topicId)
        assertNull(request.filter)
    }

    @Test
    fun `next page preserves query and cursor`() {
        val request = chatSearchRequest(chatId = 42L, query = "report", fromMessageId = 777L, filter = ChatSearchFilter())
        assertEquals(42L, request.chatId)
        assertEquals("report", request.query)
        assertEquals(777L, request.fromMessageId)
    }

    @Test
    fun `sender is mapped to a TDLib user sender`() {
        val request = chatSearchRequest(1L, "", 0L, ChatSearchFilter(senderId = 555L))
        assertEquals(555L, (request.senderId as TdApi.MessageSenderUser).userId)
    }

    @Test
    fun `attachment kinds map to shared media filters`() {
        assertTrue(chatSearchRequest(1L, "", 0L, ChatSearchFilter(kind = SharedMediaKind.Media)).filter is TdApi.SearchMessagesFilterPhotoAndVideo)
        assertTrue(chatSearchRequest(1L, "", 0L, ChatSearchFilter(kind = SharedMediaKind.Files)).filter is TdApi.SearchMessagesFilterDocument)
        assertTrue(chatSearchRequest(1L, "", 0L, ChatSearchFilter(kind = SharedMediaKind.Links)).filter is TdApi.SearchMessagesFilterUrl)
        assertTrue(chatSearchRequest(1L, "", 0L, ChatSearchFilter(kind = SharedMediaKind.Voice)).filter is TdApi.SearchMessagesFilterVoiceNote)
        assertTrue(chatSearchRequest(1L, "", 0L, ChatSearchFilter(kind = SharedMediaKind.Music)).filter is TdApi.SearchMessagesFilterAudio)
    }

    @Test
    fun `date range stays local because the TDLib request has no date fields`() {
        val request = chatSearchRequest(1L, "", 0L, ChatSearchFilter(fromDateSeconds = 100L, toDateSeconds = 200L))
        assertNull(request.filter)
        assertNull(request.senderId)
    }

    @Test
    fun `forum search keeps its topic`() {
        val request = chatSearchRequest(1L, "hello", 0L, ChatSearchFilter(), forumTopicId = 37L)
        assertEquals(37, (request.topicId as TdApi.MessageTopicForum).forumTopicId)
    }
}
