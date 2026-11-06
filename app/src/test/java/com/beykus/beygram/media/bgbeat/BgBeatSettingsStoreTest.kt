package com.beykus.beygram.media.bgbeat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BgBeatSettingsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakePreferenceStorage : BgBeatPreferenceStorage {
        val map = mutableMapOf<String, Boolean>()
        val listeners = mutableListOf<(key: String) -> Unit>()

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            map[key] ?: defaultValue

        override fun putBoolean(key: String, value: Boolean) {
            map[key] = value
            listeners.forEach { it(key) }
        }

        override fun registerListener(listener: (key: String) -> Unit) {
            listeners.add(listener)
        }
    }

    @Test
    fun defaultEnabledIsFalse() {
        val storage = FakePreferenceStorage()
        val store = BgBeatSettingsStore(storage) { tempFolder.root }

        assertFalse(store.enabled.value)
    }

    @Test
    fun setEnabledUpdatesStateFlowImmediatelyWithoutRestart() {
        val storage = FakePreferenceStorage()
        val store = BgBeatSettingsStore(storage) { tempFolder.root }

        store.setEnabled(true)
        assertTrue("StateFlow must update to true immediately", store.enabled.value)
        assertTrue("Storage must persist true", storage.getBoolean(BgBeatSettingsStore.KEY_ENABLED, false))

        store.setEnabled(false)
        assertFalse("StateFlow must update to false immediately", store.enabled.value)
        assertFalse("Storage must persist false", storage.getBoolean(BgBeatSettingsStore.KEY_ENABLED, true))
    }

    @Test
    fun multipleStoreInstancesSynchronizeThroughStorageListener() {
        val sharedStorage = FakePreferenceStorage()
        val store1 = BgBeatSettingsStore(sharedStorage) { tempFolder.root }
        val store2 = BgBeatSettingsStore(sharedStorage) { tempFolder.root }

        assertFalse(store1.enabled.value)
        assertFalse(store2.enabled.value)

        // Toggling store1 must immediately propagate to store2's StateFlow without app restart
        store1.setEnabled(true)
        assertTrue("Store1 flow must be true", store1.enabled.value)
        assertTrue("Store2 flow must immediately be true via storage listener", store2.enabled.value)

        store2.setEnabled(false)
        assertFalse("Store1 flow must immediately be false via storage listener", store1.enabled.value)
        assertFalse("Store2 flow must be false", store2.enabled.value)
    }

    @Test
    fun accountCacheDirectoriesAreIsolated() {
        val baseDir = tempFolder.newFolder("bgbeat-cache")

        val account1Dir = BgBeatSettingsStore.getAccountCacheDir(baseDir, "user_123")
        val account2Dir = BgBeatSettingsStore.getAccountCacheDir(baseDir, "user_456")

        assertTrue(account1Dir.absolutePath != account2Dir.absolutePath)
        assertTrue(account1Dir.name.contains("user_123"))
        assertTrue(account2Dir.name.contains("user_456"))

        // Create files in both account directories
        account1Dir.mkdirs()
        account2Dir.mkdirs()
        val file1 = File(account1Dir, "track_a.bgm").apply { writeBytes(ByteArray(512)) }
        val file2 = File(account2Dir, "track_b.bgm").apply { writeBytes(ByteArray(512)) }

        assertEquals(1024L, BgBeatSettingsStore.getCacheSizeBytes(baseDir))

        // Clear only account1 cache
        val freed = BgBeatSettingsStore.clearAccountCache(baseDir, "user_123")
        assertEquals(512L, freed)
        assertFalse("Account 1 file must be deleted", file1.exists())
        assertTrue("Account 2 file must remain completely untouched", file2.exists())
        assertEquals(512L, BgBeatSettingsStore.getCacheSizeBytes(baseDir))
    }
}
