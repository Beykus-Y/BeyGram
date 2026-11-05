package com.beykus.beygram.data.settings

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatMemorySettingsStoreTest {
    private fun preferences(name: String) =
        RuntimeEnvironment.getApplication().getSharedPreferences(name, Context.MODE_PRIVATE)

    @Test
    fun everythingIsOffByDefault() {
        val store = ChatMemorySettingsStore(preferences("chat-memory-defaults"))

        assertEquals(ChatMemorySettings(), store.current)
        assertFalse(store.current.enabled)
    }

    @Test
    fun choicesSurviveAReload() {
        val preferences = preferences("chat-memory-reload")
        val store = ChatMemorySettingsStore(preferences)

        val saved = store.update {
            it.copy(
                enabled = true,
                saveAllChats = false,
                limitBytes = ChatMemorySettings.LIMIT_CHOICES[2],
                chats = mapOf(5L to ChatMemoryChatMode.Save, 6L to ChatMemoryChatMode.Skip)
            )
        }

        assertTrue(saved)
        assertEquals(store.current, ChatMemorySettingsStore(preferences).current)
    }

    @Test
    fun aRemovedChatChoiceIsForgotten() {
        val preferences = preferences("chat-memory-forget")
        val store = ChatMemorySettingsStore(preferences)
        store.update { it.copy(chats = mapOf(5L to ChatMemoryChatMode.Save)) }

        store.update { it.copy(chats = emptyMap()) }

        assertTrue(ChatMemorySettingsStore(preferences).current.chats.isEmpty())
    }

    @Test
    fun aTooSmallLimitIsRaisedToTheMinimum() {
        val store = ChatMemorySettingsStore(preferences("chat-memory-limit"))

        store.update { it.copy(limitBytes = 10L) }

        assertEquals(ChatMemorySettings.MIN_LIMIT_BYTES, store.current.limitBytes)
    }

    @Test
    fun listenersHearOnlyRealChanges() {
        val store = ChatMemorySettingsStore(preferences("chat-memory-listeners"))
        var notified = 0
        store.addListener { notified++ }

        store.update { it.copy(enabled = true) }
        store.update { it.copy(enabled = true) }

        assertEquals(1, notified)
    }
}
