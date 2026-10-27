package com.beykus.beygram.data.motion

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.beykus.beygram.ui.motion.MotionCategory
import com.beykus.beygram.ui.motion.MotionPreset
import com.beykus.beygram.ui.motion.MotionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface MotionSettingsStorage {
    fun readString(key: String): String?
    fun writeString(key: String, value: String)
}

private class SharedPreferencesMotionSettingsStorage(context: Context) : MotionSettingsStorage {
    private val preferences = context.applicationContext.getSharedPreferences("motion-settings", Context.MODE_PRIVATE)
    override fun readString(key: String): String? = preferences.getString(key, null)
    override fun writeString(key: String, value: String) { preferences.edit().putString(key, value).apply() }
}

/**
 * Persists the motion configuration as plain strings under a schema version.
 *
 * Version 1 is the first schema. A missing or unknown value falls back to its default instead of failing, so a future
 * key added by a newer build never resets the values this build understands.
 */
class MotionSettingsStore internal constructor(private val storage: MotionSettingsStorage) {
    constructor(context: Context) : this(SharedPreferencesMotionSettingsStorage(context))

    private val _state = MutableStateFlow(read())
    val state: StateFlow<MotionSettings> = _state.asStateFlow()

    @Synchronized
    fun update(transform: (MotionSettings) -> MotionSettings) {
        val next = transform(_state.value).normalized()
        write(next)
        _state.value = next
    }

    private fun write(settings: MotionSettings) {
        storage.writeString(VERSION_KEY, SCHEMA_VERSION.toString())
        storage.writeString(PRESET_KEY, settings.preset.name)
        storage.writeString(SPEED_KEY, settings.speed.toString())
        storage.writeString(INTENSITY_KEY, settings.intensityPercent.toString())
        storage.writeString(DISABLED_CATEGORIES_KEY, settings.disabledCategories.joinToString(",") { it.name })
    }

    private fun read(): MotionSettings {
        val defaults = MotionSettings()
        return MotionSettings(
            preset = storage.readString(PRESET_KEY)
                ?.let { name -> MotionPreset.entries.firstOrNull { it.name == name } }
                ?: defaults.preset,
            speed = storage.readString(SPEED_KEY)?.toFloatOrNull() ?: defaults.speed,
            intensityPercent = storage.readString(INTENSITY_KEY)?.toIntOrNull() ?: defaults.intensityPercent,
            disabledCategories = storage.readString(DISABLED_CATEGORIES_KEY)
                .orEmpty()
                .split(",")
                .mapNotNull { name -> MotionCategory.entries.firstOrNull { it.name == name } }
                .toSet()
        ).normalized()
    }

    internal companion object {
        @VisibleForTesting
        const val SCHEMA_VERSION = 1
        @VisibleForTesting
        const val VERSION_KEY = "version"
        @VisibleForTesting
        const val PRESET_KEY = "preset"
        @VisibleForTesting
        const val SPEED_KEY = "speed"
        @VisibleForTesting
        const val INTENSITY_KEY = "intensity"
        @VisibleForTesting
        const val DISABLED_CATEGORIES_KEY = "disabled_categories"
    }
}
