package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchContextTest {
    @Test
    fun contextIsAttachedByChatIdWithoutChangingSearchResults() {
        val first = ChatMessage(1L, 101L, "same text", "12:34", false)
        val second = ChatMessage(2L, 202L, "same text", "12:35", false)
        val firstChat = ChatPreview(101L, "Project chat", "PC", com.beykus.beygram.domain.UiText.Raw(""), "", 0, 1L)
        val secondChat = ChatPreview(202L, "Friends", "FR", com.beykus.beygram.domain.UiText.Raw(""), "", 0, 2L)
        val page = GlobalMessagePage(listOf(first, second), "offset", mapOf(101L to firstChat, 202L to secondChat))
        assertEquals("Project chat", page.chats[first.chatId]?.title)
        assertEquals("Friends", page.chats[second.chatId]?.title)
        assertEquals(2, page.messages.size)
        assertEquals("offset", page.nextOffset)
    }

    @Test
    fun unavailableChatDoesNotHideSearchHit() {
        val item = ChatMessage(3L, 303L, "found", "10:00", false)
        val page = GlobalMessagePage(listOf(item), "")
        assertEquals(item, page.messages.single())
        assertTrue(page.chats.isEmpty())
    }

    @Test
    fun forumTopicIsStoredForExactNavigation() {
        val item = ChatMessage(4L, 404L, "message in topic", "10:00", false, forumTopicId = 913L)
        assertEquals(913L, item.forumTopicId)
    }
}
