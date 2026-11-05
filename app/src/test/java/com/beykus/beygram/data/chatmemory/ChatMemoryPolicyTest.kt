package com.beykus.beygram.data.chatmemory

import com.beykus.beygram.data.settings.ChatMemoryChatMode
import com.beykus.beygram.data.settings.ChatMemorySettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMemoryPolicyTest {
    private val plainChat = ChatMemoryChatFacts(isSecret = false, hasProtectedContent = false, hasAutoDeleteTimer = false)
    private val enabled = ChatMemorySettings(enabled = true)

    @Test
    fun isOffUntilTheUserTurnsItOn() {
        assertFalse(chatMemorySaves(ChatMemorySettings(), 1L, plainChat))
    }

    @Test
    fun keepsEveryChatWhenEnabled() {
        assertTrue(chatMemorySaves(enabled, 1L, plainChat))
    }

    @Test
    fun neverKeepsSecretChats() {
        val settings = enabled.copy(chats = mapOf(1L to ChatMemoryChatMode.Save))
        assertFalse(chatMemorySaves(settings, 1L, plainChat.copy(isSecret = true)))
    }

    @Test
    fun neverKeepsChatsWithProtectedContent() {
        val settings = enabled.copy(chats = mapOf(1L to ChatMemoryChatMode.Save))
        assertFalse(chatMemorySaves(settings, 1L, plainChat.copy(hasProtectedContent = true)))
    }

    @Test
    fun neverKeepsChatsWhoseMessagesExpireByTheirTimer() {
        val settings = enabled.copy(chats = mapOf(1L to ChatMemoryChatMode.Save))
        assertFalse(chatMemorySaves(settings, 1L, plainChat.copy(hasAutoDeleteTimer = true)))
    }

    @Test
    fun withAllChatsOffKeepsOnlyTheChatsSavedByName() {
        val settings = enabled.copy(saveAllChats = false, chats = mapOf(1L to ChatMemoryChatMode.Save))
        assertTrue(chatMemorySaves(settings, 1L, plainChat))
        assertFalse(chatMemorySaves(settings, 2L, plainChat))
    }

    @Test
    fun aSkippedChatIsNotKeptEvenWithAllChatsOn() {
        val settings = enabled.copy(chats = mapOf(1L to ChatMemoryChatMode.Skip))
        assertFalse(chatMemorySaves(settings, 1L, plainChat))
        assertTrue(chatMemorySaves(settings, 2L, plainChat))
    }
}
