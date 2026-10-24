package com.beykus.beygram.data.settings

import com.beykus.beygram.data.appearance.ChatWallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatOverridesStoreTest {
    private class FakeStorage : ChatOverrideStorage {
        val values = mutableMapOf<String, String>()
        override fun value(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun keys(): Set<String> = values.keys.toSet()
    }

    @Test fun wallpaperOverrideIsScopedToOneChat() {
        val store = ChatOverridesStore(FakeStorage())
        store.setWallpaper(1L, ChatWallpaper.Preset("mint-air"))
        store.setWallpaper(2L, ChatWallpaper.None)

        assertEquals(ChatWallpaper.Preset("mint-air"), store.state.value[1L]?.wallpaper)
        assertEquals(ChatWallpaper.None, store.state.value[2L]?.wallpaper)
        assertNull(store.state.value[3L])
    }

    @Test fun resetReturnsToGlobalAndPersistsAcrossInstances() {
        val storage = FakeStorage()
        val store = ChatOverridesStore(storage)
        store.setWallpaper(10L, ChatWallpaper.Solid(0xFF112233.toInt()))
        val reopened = ChatOverridesStore(storage)
        assertEquals(ChatWallpaper.Solid(0xFF112233.toInt()), reopened.state.value[10L]?.wallpaper)

        reopened.reset(10L)
        assertNull(ChatOverridesStore(storage).state.value[10L]?.wallpaper)
    }

    @Test fun accountPreferenceNamesAreIsolated() {
        assertEquals("chat-overrides", chatOverridePreferencesName("legacy"))
        assertNotEquals(chatOverridePreferencesName("legacy"), chatOverridePreferencesName("second"))
        assertNotEquals(chatOverridePreferencesName("first"), chatOverridePreferencesName("second"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun accountPreferenceNamesRejectPathEscapes() {
        chatOverridePreferencesName("../legacy")
    }
}
