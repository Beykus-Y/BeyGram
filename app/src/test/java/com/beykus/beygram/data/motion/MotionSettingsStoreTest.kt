package com.beykus.beygram.data.motion

import com.beykus.beygram.ui.motion.MotionCategory
import com.beykus.beygram.ui.motion.MotionPreset
import com.beykus.beygram.ui.motion.MotionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSettingsStoreTest {
    private class FakeStorage : MotionSettingsStorage {
        val values = mutableMapOf<String, String>()
        override fun readString(key: String): String? = values[key]
        override fun writeString(key: String, value: String) { values[key] = value }
    }

    @Test
    fun emptyStorageReadsBalancedDefaults() {
        assertEquals(MotionSettings(), MotionSettingsStore(FakeStorage()).state.value)
    }

    @Test
    fun updateIsPersistedAndReadBackByANewStore() {
        val storage = FakeStorage()
        MotionSettingsStore(storage).update {
            it.withPreset(MotionPreset.Expressive).withCategoryEnabled(MotionCategory.Lists, enabled = false)
        }

        val restored = MotionSettingsStore(storage).state.value
        assertEquals(MotionPreset.Expressive, restored.preset)
        assertEquals(0.75f, restored.speed, 0f)
        assertEquals(150, restored.intensityPercent)
        assertEquals(setOf(MotionCategory.Lists), restored.disabledCategories)
        assertEquals(MotionSettingsStore.SCHEMA_VERSION.toString(), storage.values[MotionSettingsStore.VERSION_KEY])
    }

    @Test
    fun unknownOrOutOfRangeValuesFallBackInsteadOfFailing() {
        val storage = FakeStorage().apply {
            values[MotionSettingsStore.PRESET_KEY] = "FromAFutureBuild"
            values[MotionSettingsStore.SPEED_KEY] = "99"
            values[MotionSettingsStore.INTENSITY_KEY] = "not-a-number"
            values[MotionSettingsStore.DISABLED_CATEGORIES_KEY] = "Media,Unknown"
        }

        val settings = MotionSettingsStore(storage).state.value
        assertEquals(MotionPreset.Balanced, settings.preset)
        assertEquals(2f, settings.speed, 0f)
        assertEquals(100, settings.intensityPercent)
        assertEquals(setOf(MotionCategory.Media), settings.disabledCategories)
        assertTrue(settings.enabled)
    }
}
