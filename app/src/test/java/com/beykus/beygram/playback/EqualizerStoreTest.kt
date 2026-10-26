package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerStoreTest {
    private class FakeStorage : EqualizerSettingsStorage {
        val booleans = mutableMapOf<String, Boolean>()
        val strings = mutableMapOf<String, String>()

        override fun readBoolean(key: String, defaultValue: Boolean): Boolean = booleans[key] ?: defaultValue
        override fun readString(key: String): String? = strings[key]
        override fun writeBoolean(key: String, value: Boolean) {
            booleans[key] = value
        }

        override fun writeString(key: String, value: String) {
            strings[key] = value
        }
    }

    @Test
    fun defaultsAreDisabledFlatWithoutInventedCustomLevels() {
        val settings = EqualizerStore(FakeStorage()).state.value
        assertFalse(settings.enabled)
        assertEquals(EqualizerPreset.Flat, settings.selectedPreset)
        assertTrue(settings.customLevelsMb.isEmpty())
        assertFalse(settings.hasCustomPreset)
    }

    @Test
    fun switchPresetAndCustomCurveSurviveANewStore() {
        val storage = FakeStorage()
        val store = EqualizerStore(storage)
        store.setEnabled(true)
        store.selectPreset(EqualizerPreset.Rock)
        store.saveCustom(mapOf(60 to 450, 230 to -150))

        val restored = EqualizerStore(storage).state.value
        assertTrue(restored.enabled)
        assertEquals(EqualizerPreset.Custom, restored.selectedPreset)
        assertEquals(mapOf(60 to 450, 230 to -150), restored.customLevelsMb)
        assertTrue(restored.hasCustomPreset)
    }

    @Test
    fun draggingUpdatesLiveStateButOnlyTheFinishedGestureWritesToDisk() {
        val storage = FakeStorage()
        val store = EqualizerStore(storage)
        val levels = mapOf(60 to 300, 230 to 0)

        store.setCustomLevels(levels, persist = false)
        assertEquals(levels, store.state.value.customLevelsMb)
        assertEquals(EqualizerPreset.Custom, store.state.value.selectedPreset)
        assertFalse("A moving slider must not write every frame", storage.strings.containsKey(EqualizerStore.KEY_CUSTOM_LEVELS))

        store.setCustomLevels(levels, persist = true)
        assertEquals(levels, EqualizerStore(storage).state.value.customLevelsMb)
        assertEquals(EqualizerPreset.Custom, EqualizerStore(storage).state.value.selectedPreset)
    }

    @Test
    fun corruptPresetAndOutOfRangeLevelsFallBackSafely() {
        val storage = FakeStorage().apply {
            strings[EqualizerStore.KEY_PRESET] = "NotAPreset"
            strings[EqualizerStore.KEY_CUSTOM_LEVELS] = "bad;60:5000;230:-100;invalid:10;0:200"
        }
        val settings = EqualizerStore(storage).state.value
        assertEquals(EqualizerPreset.Flat, settings.selectedPreset)
        assertEquals(mapOf(230 to -100), settings.customLevelsMb)
    }

    @Test
    fun resetReturnsToNeutralButKeepsTheSavedCustomPreset() {
        val storage = FakeStorage()
        val store = EqualizerStore(storage)
        store.saveCustom(mapOf(60 to 500))
        store.reset()

        assertEquals(EqualizerPreset.Flat, store.state.value.selectedPreset)
        assertTrue(store.state.value.hasCustomPreset)
        assertEquals(EqualizerPreset.Flat, EqualizerStore(storage).state.value.selectedPreset)
    }

    @Test
    fun levelEncodingRoundTripsOnlyValidPairs() {
        val encoded = encodeEqualizerLevels(mapOf(14_000 to -600, 60 to 600, 230 to 0))
        assertEquals("60:600;230:0;14000:-600", encoded)
        assertEquals(mapOf(60 to 600, 230 to 0, 14_000 to -600), decodeEqualizerLevels(encoded))
        assertEquals(emptyMap<Int, Int>(), decodeEqualizerLevels(""))
        assertEquals(emptyMap<Int, Int>(), decodeEqualizerLevels(null))
    }
}
