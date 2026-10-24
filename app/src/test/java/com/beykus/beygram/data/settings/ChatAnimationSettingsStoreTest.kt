package com.beykus.beygram.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAnimationSettingsStoreTest {
    private class FakeStorage : ChatAnimationSettingsStorage {
        val values = mutableMapOf<String, Boolean>()
        override fun readBoolean(key: String, defaultValue: Boolean): Boolean = values[key] ?: defaultValue
        override fun writeBoolean(key: String, value: Boolean) { values[key] = value }
    }

    @Test
    fun defaultsKeepAnimatedEmojiAndEffectsEnabled() {
        val settings = ChatAnimationSettingsStore(FakeStorage()).state.value
        assertTrue(settings.animatedEmoji)
        assertTrue(settings.messageEffects)
    }

    @Test
    fun togglesUpdateTheStateImmediatelyAndSurviveANewStore() {
        val storage = FakeStorage()
        val store = ChatAnimationSettingsStore(storage)
        store.setAnimatedEmoji(false)
        store.setMessageEffects(false)

        assertFalse(store.state.value.animatedEmoji)
        assertFalse(store.state.value.messageEffects)

        val restored = ChatAnimationSettingsStore(storage).state.value
        assertFalse(restored.animatedEmoji)
        assertFalse(restored.messageEffects)
    }
}
