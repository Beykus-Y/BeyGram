package com.beykus.beygram.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMemoryChatChoiceTest {
    @Test
    fun skippingOneChatWhileSavingAllStoresAnException() {
        val settings = ChatMemorySettings(enabled = true, saveAllChats = true).withChatSaved(7L, saved = false)

        assertEquals(mapOf(7L to ChatMemoryChatMode.Skip), settings.chats)
        assertFalse(settings.savesChat(7L))
        assertTrue(settings.savesChat(8L))
    }

    @Test
    fun choosingTheDefaultRemovesTheException() {
        val skipped = ChatMemorySettings(enabled = true, saveAllChats = true, chats = mapOf(7L to ChatMemoryChatMode.Skip))

        val back = skipped.withChatSaved(7L, saved = true)

        assertTrue(back.chats.isEmpty())
        assertTrue(back.savesChat(7L))
    }

    @Test
    fun savingOneChatWhileSavingNoneStoresAnException() {
        val settings = ChatMemorySettings(enabled = true, saveAllChats = false).withChatSaved(9L, saved = true)

        assertEquals(mapOf(9L to ChatMemoryChatMode.Save), settings.chats)
        assertTrue(settings.savesChat(9L))
        assertFalse(settings.savesChat(10L))
    }

    @Test
    fun unsavingAChatWhileSavingNoneRemovesTheException() {
        val saved = ChatMemorySettings(enabled = true, saveAllChats = false, chats = mapOf(9L to ChatMemoryChatMode.Save))

        val back = saved.withChatSaved(9L, saved = false)

        assertTrue(back.chats.isEmpty())
        assertFalse(back.savesChat(9L))
    }

    @Test
    fun changingOneChatLeavesTheOthersAlone() {
        val settings = ChatMemorySettings(enabled = true, saveAllChats = true, chats = mapOf(1L to ChatMemoryChatMode.Skip))
            .withChatSaved(2L, saved = false)

        assertEquals(mapOf(1L to ChatMemoryChatMode.Skip, 2L to ChatMemoryChatMode.Skip), settings.chats)
    }
}
