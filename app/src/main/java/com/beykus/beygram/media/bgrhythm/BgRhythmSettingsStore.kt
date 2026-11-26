package com.beykus.beygram.media.bgrhythm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent mode selection for BG Rhythm visual reactivity in the music player.
 * Defaults to [BgRhythmMode.Calm] so subtle rhythm reactivity is active out of the box while
 * respecting battery saver, reduced motion, and explicit user opt-outs.
 */
object BgRhythmSettingsStore {
    const val PREFS_NAME = "beygram_rhythm_settings"
    private const val KEY_MODE = "mode"
    val DEFAULT_MODE = BgRhythmMode.Calm

    private val lock = Any()
    @Volatile
    private var loaded = false
    private val _mode = MutableStateFlow(DEFAULT_MODE)

    fun flow(context: Context): StateFlow<BgRhythmMode> {
        ensureLoaded(context)
        return _mode.asStateFlow()
    }

    fun current(context: Context): BgRhythmMode {
        ensureLoaded(context)
        return _mode.value
    }

    fun setMode(context: Context, mode: BgRhythmMode) {
        ensureLoaded(context)
        synchronized(lock) {
            _mode.value = mode
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_MODE, null)
                ?.let { runCatching { BgRhythmMode.valueOf(it) }.getOrNull() }
                ?: DEFAULT_MODE
            _mode.value = saved
            loaded = true
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            loaded = false
            _mode.value = DEFAULT_MODE
        }
    }
}
