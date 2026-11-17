package com.beykus.beygram.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForumNavigationStoreTest {
    private class FakeStorage : ForumNavigationStorage {
        val values = mutableMapOf<String, String>()
        override fun value(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun keys(): Set<String> = values.keys.toSet()
    }

    @Test fun modeIsScopedToOneChatAndDefaultsToClassic() {
        val store = ForumNavigationStore(FakeStorage())
        store.setMode(1L, ForumNavigationMode.Tabs)
        store.setMode(2L, ForumNavigationMode.Rail)

        assertEquals(ForumNavigationMode.Tabs, store.modes.value[1L])
        assertEquals(ForumNavigationMode.Rail, store.modes.value[2L])
        assertNull(store.modes.value[3L])
    }

    @Test fun classicRemovesTheStoredChoice() {
        val storage = FakeStorage()
        val store = ForumNavigationStore(storage)
        store.setMode(7L, ForumNavigationMode.Tabs)
        store.setMode(7L, ForumNavigationMode.Classic)

        assertNull(store.modes.value[7L])
        assertNull(storage.values["mode:7"])
    }

    @Test fun choicesPersistAcrossInstances() {
        val storage = FakeStorage()
        ForumNavigationStore(storage).setMode(10L, ForumNavigationMode.Rail)

        assertEquals(ForumNavigationMode.Rail, ForumNavigationStore(storage).modes.value[10L])
    }

    @Test fun unknownOrClassicStoredValuesAreIgnored() {
        val storage = FakeStorage()
        storage.values["mode:5"] = "Carousel"
        storage.values["mode:6"] = "Classic"
        storage.values["mode:bad"] = "Tabs"
        storage.values["other:9"] = "Rail"

        assertEquals(emptyMap<Long, ForumNavigationMode>(), ForumNavigationStore(storage).modes.value)
    }

    @Test fun lastTopicIsRememberedPerChat() {
        val store = ForumNavigationStore(FakeStorage())
        assertEquals(0L, store.lastTopic(4L))

        store.setLastTopic(4L, 42L)
        store.setLastTopic(5L, 43L)
        assertEquals(42L, store.lastTopic(4L))
        assertEquals(43L, store.lastTopic(5L))

        store.setLastTopic(4L, 0L)
        assertEquals(0L, store.lastTopic(4L))
        assertEquals(43L, store.lastTopic(5L))
    }

    @Test fun preferenceNamesAreAccountScoped() {
        assertEquals("forum-navigation", forumNavigationPreferencesName("legacy"))
        assertNotEquals(forumNavigationPreferencesName("legacy"), forumNavigationPreferencesName("second"))
        assertNotEquals(forumNavigationPreferencesName("first"), forumNavigationPreferencesName("second"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun preferenceNamesRejectPathEscapes() {
        forumNavigationPreferencesName("../legacy")
    }
}
