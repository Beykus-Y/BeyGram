package com.beykus.beygram.media.bgmotion

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Experimental switches for the BG Motion mascot. Off by default.
 *
 * Phase 0/1 ships a temporary 2D test rig, not a finished mascot: it only exists to validate that
 * playback sync drives the character. The final visual will be a VRM model on top of the same settings.
 */
object BgMascotSettingsStore {
    const val PREFS_NAME = "bgmascot_settings"

    const val KEY_ENABLED = "enabled"
    const val KEY_DEBUG_OVERLAY = "debug_overlay"
    const val KEY_SHOW_IN_PLAYER = "show_in_player"
    const val KEY_INTENSITY = "intensity"
    const val KEY_AUDIO_OFFSET_MS = "audio_offset_ms"

    const val DEFAULT_INTENSITY = 1.0f
    const val MIN_INTENSITY = 0.5f
    const val MAX_INTENSITY = 1.5f

    const val DEFAULT_AUDIO_OFFSET_MS = 0
    const val MIN_AUDIO_OFFSET_MS = -150
    const val MAX_AUDIO_OFFSET_MS = 150

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _debugOverlay = MutableStateFlow(false)
    val debugOverlay: StateFlow<Boolean> = _debugOverlay.asStateFlow()

    private val _showInPlayer = MutableStateFlow(true)
    val showInPlayer: StateFlow<Boolean> = _showInPlayer.asStateFlow()

    private val _intensity = MutableStateFlow(DEFAULT_INTENSITY)
    val intensity: StateFlow<Float> = _intensity.asStateFlow()

    private val _audioOffsetMs = MutableStateFlow(DEFAULT_AUDIO_OFFSET_MS)
    val audioOffsetMs: StateFlow<Int> = _audioOffsetMs.asStateFlow()

    @Volatile
    private var loaded = false

    /**
     * Returns the process-wide store and makes sure its flows are loaded. Present for symmetry with the
     * other BG settings stores; this object is already the singleton, so the context is only used by [load].
     */
    fun getInstance(context: Context): BgMascotSettingsStore {
        load(context)
        return this
    }

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = prefsOrNull(context)
            _enabled.value = readBoolean(prefs, KEY_ENABLED, false)
            _debugOverlay.value = readBoolean(prefs, KEY_DEBUG_OVERLAY, false)
            _showInPlayer.value = readBoolean(prefs, KEY_SHOW_IN_PLAYER, true)
            _intensity.value = clampIntensity(readFloat(prefs, KEY_INTENSITY, DEFAULT_INTENSITY))
            _audioOffsetMs.value = clampAudioOffsetMs(readInt(prefs, KEY_AUDIO_OFFSET_MS, DEFAULT_AUDIO_OFFSET_MS))
            loaded = true
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        _enabled.value = value
        edit(context) { it.edit().putBoolean(KEY_ENABLED, value).apply() }
    }

    fun setDebugOverlay(context: Context, value: Boolean) {
        _debugOverlay.value = value
        edit(context) { it.edit().putBoolean(KEY_DEBUG_OVERLAY, value).apply() }
    }

    fun setShowInPlayer(context: Context, value: Boolean) {
        _showInPlayer.value = value
        edit(context) { it.edit().putBoolean(KEY_SHOW_IN_PLAYER, value).apply() }
    }

    fun setIntensity(context: Context, value: Float) {
        val clamped = clampIntensity(value)
        _intensity.value = clamped
        edit(context) { it.edit().putFloat(KEY_INTENSITY, clamped).apply() }
    }

    fun setAudioOffsetMs(context: Context, value: Int) {
        val clamped = clampAudioOffsetMs(value)
        _audioOffsetMs.value = clamped
        edit(context) { it.edit().putInt(KEY_AUDIO_OFFSET_MS, clamped).apply() }
    }

    internal fun clampIntensity(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MIN_INTENSITY, MAX_INTENSITY) else DEFAULT_INTENSITY

    internal fun clampAudioOffsetMs(value: Int): Int = value.coerceIn(MIN_AUDIO_OFFSET_MS, MAX_AUDIO_OFFSET_MS)

    private fun prefsOrNull(context: Context): SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: RuntimeException) {
        null
    }

    private fun edit(context: Context, block: (SharedPreferences) -> Unit) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            block(prefs)
        } catch (_: RuntimeException) {
            // The in-memory value still applies for this process.
        }
    }

    private fun readBoolean(prefs: SharedPreferences?, key: String, defaultValue: Boolean): Boolean =
        if (prefs == null) defaultValue else try {
            prefs.getBoolean(key, defaultValue)
        } catch (_: RuntimeException) {
            defaultValue
        }

    private fun readFloat(prefs: SharedPreferences?, key: String, defaultValue: Float): Float =
        if (prefs == null) defaultValue else try {
            prefs.getFloat(key, defaultValue)
        } catch (_: RuntimeException) {
            defaultValue
        }

    private fun readInt(prefs: SharedPreferences?, key: String, defaultValue: Int): Int =
        if (prefs == null) defaultValue else try {
            prefs.getInt(key, defaultValue)
        } catch (_: RuntimeException) {
            defaultValue
        }
}
