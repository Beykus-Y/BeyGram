package com.beykus.beygram.playback

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The equalizer's user-facing presets. [Custom] stores the levels the listener built by hand, keyed by
 * the real band frequencies reported by the device.
 */
enum class EqualizerPreset {
    Flat,
    Bass,
    Vocal,
    Pop,
    Rock,
    Electronic,
    Classical,
    Jazz,
    Custom
}

/**
 * Device-local equalizer preferences. They belong to this installation, not to a Telegram account: changing an
 * account must not silently change or reset the sound of the shared player.
 *
 * [customLevelsMb] is a frequency-to-gain map rather than a positional list. A five-band phone and a ten-band DAC
 * therefore restore the same curve at the frequencies both devices actually support.
 */
data class EqualizerSettings(
    val enabled: Boolean = false,
    val selectedPreset: EqualizerPreset = EqualizerPreset.Flat,
    val customLevelsMb: Map<Int, Int> = emptyMap()
) {
    val hasCustomPreset: Boolean get() = customLevelsMb.isNotEmpty()
}

internal interface EqualizerSettingsStorage {
    fun readBoolean(key: String, defaultValue: Boolean): Boolean
    fun readString(key: String): String?
    fun writeBoolean(key: String, value: Boolean)
    fun writeString(key: String, value: String)
}

private const val EQUALIZER_PREFERENCES_NAME = "beygram-playback-equalizer"

private class SharedPreferencesEqualizerSettingsStorage(context: Context) : EqualizerSettingsStorage {
    private val preferences = context.applicationContext.getSharedPreferences(EQUALIZER_PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun readBoolean(key: String, defaultValue: Boolean): Boolean =
        runCatching { preferences.getBoolean(key, defaultValue) }.getOrDefault(defaultValue)

    override fun readString(key: String): String? =
        runCatching { preferences.getString(key, null) }.getOrNull()

    override fun writeBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    override fun writeString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

/** Serializes only real, positive frequencies so a corrupt preference can never build an invalid band. */
internal fun encodeEqualizerLevels(levelsMb: Map<Int, Int>): String =
    levelsMb.entries
        .filter { (frequencyHz, levelMb) -> frequencyHz > 0 && levelMb in EQUALIZER_MIN_STORED_LEVEL_MB..EQUALIZER_MAX_STORED_LEVEL_MB }
        .sortedBy { it.key }
        .joinToString(separator = ";") { (frequencyHz, levelMb) -> "$frequencyHz:$levelMb" }

internal fun decodeEqualizerLevels(encoded: String?): Map<Int, Int> {
    if (encoded.isNullOrBlank()) return emptyMap()
    val result = linkedMapOf<Int, Int>()
    encoded.split(';').forEach { entry ->
        val separator = entry.indexOf(':')
        if (separator <= 0 || separator == entry.lastIndex) return@forEach
        val frequency = entry.substring(0, separator).toIntOrNull() ?: return@forEach
        val level = entry.substring(separator + 1).toIntOrNull() ?: return@forEach
        if (frequency <= 0 || level !in EQUALIZER_MIN_STORED_LEVEL_MB..EQUALIZER_MAX_STORED_LEVEL_MB) return@forEach
        result[frequency] = level
    }
    return result
}

/**
 * Persists the equalizer switch, the selected preset and the custom curve. The flow is hot, so a slider move is
 * reflected by the playing session and by an already open settings screen without a restart.
 */
class EqualizerStore internal constructor(private val storage: EqualizerSettingsStorage) {
    constructor(context: Context) : this(SharedPreferencesEqualizerSettingsStorage(context))

    private val _state = MutableStateFlow(read())
    val state: StateFlow<EqualizerSettings> = _state.asStateFlow()

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (_state.value.enabled == enabled) return
        storage.writeBoolean(KEY_ENABLED, enabled)
        _state.value = _state.value.copy(enabled = enabled)
    }

    @Synchronized
    fun selectPreset(preset: EqualizerPreset) {
        if (_state.value.selectedPreset == preset) return
        storage.writeString(KEY_PRESET, preset.name)
        _state.value = _state.value.copy(selectedPreset = preset)
    }

    /**
     * Stores the curve the listener is editing and selects [EqualizerPreset.Custom]. [persist] is false while a
     * slider is still moving: the live sound and the UI update immediately, while the preference disk write waits
     * for the end of the gesture instead of running on every frame.
     */
    @Synchronized
    fun setCustomLevels(levelsMb: Map<Int, Int>, persist: Boolean = true) {
        val normalized = levelsMb.filterKeys { it > 0 }
        val next = _state.value.copy(
            selectedPreset = EqualizerPreset.Custom,
            customLevelsMb = normalized
        )
        if (next != _state.value) _state.value = next
        if (persist) {
            storage.writeString(KEY_PRESET, EqualizerPreset.Custom.name)
            storage.writeString(KEY_CUSTOM_LEVELS, encodeEqualizerLevels(normalized))
        }
    }

    /** Saves the curve currently heard as the user preset and switches to it. */
    @Synchronized
    fun saveCustom(levelsMb: Map<Int, Int>) = setCustomLevels(levelsMb, persist = true)

    /** Returns the current curve to neutral; the saved custom preset is kept for later. */
    @Synchronized
    fun reset() = selectPreset(EqualizerPreset.Flat)

    private fun read(): EqualizerSettings {
        val preset = storage.readString(KEY_PRESET)
            ?.let { stored -> EqualizerPreset.entries.firstOrNull { it.name == stored } }
            ?: EqualizerPreset.Flat
        return EqualizerSettings(
            enabled = storage.readBoolean(KEY_ENABLED, false),
            selectedPreset = preset,
            customLevelsMb = decodeEqualizerLevels(storage.readString(KEY_CUSTOM_LEVELS))
        )
    }

    internal companion object {
        @VisibleForTesting const val KEY_ENABLED = "enabled"
        @VisibleForTesting const val KEY_PRESET = "preset"
        @VisibleForTesting const val KEY_CUSTOM_LEVELS = "custom_levels"

        @Volatile private var instance: EqualizerStore? = null

        fun get(context: Context): EqualizerStore =
            instance ?: synchronized(this) {
                instance ?: EqualizerStore(context.applicationContext).also { instance = it }
            }
    }
}

/** The stored curve is bounded to a range wider than current hardware, so a future or malformed device cannot overflow. */
internal const val EQUALIZER_MIN_STORED_LEVEL_MB = -2_400
internal const val EQUALIZER_MAX_STORED_LEVEL_MB = 2_400
