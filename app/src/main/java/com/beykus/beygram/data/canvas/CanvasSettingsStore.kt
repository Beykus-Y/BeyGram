package com.beykus.beygram.data.canvas

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent settings for BG Canvas animated covers.
 * Defaults to [BgCanvasMode.Off] and `respectDataSaver = true`.
 */
object CanvasSettingsStore {
    private const val PREFS_NAME = "beygram_canvas_settings"
    private const val KEY_MODE = "mode"
    private const val KEY_DATA_SAVER = "respect_data_saver"

    private val lock = Any()
    @Volatile
    private var loaded = false
    private val _settings = MutableStateFlow(CanvasSourceSettings())

    fun flow(context: Context): StateFlow<CanvasSourceSettings> {
        ensureLoaded(context)
        return _settings.asStateFlow()
    }

    fun current(context: Context): CanvasSourceSettings {
        ensureLoaded(context)
        return _settings.value
    }

    fun setMode(context: Context, mode: BgCanvasMode) = update(context) { it.copy(mode = mode) }

    fun setRespectDataSaver(context: Context, respect: Boolean) =
        update(context) { it.copy(respectDataSaver = respect) }

    fun update(context: Context, transform: (CanvasSourceSettings) -> CanvasSourceSettings) {
        ensureLoaded(context)
        val next = synchronized(lock) {
            val updated = transform(_settings.value)
            _settings.value = updated
            updated
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, next.mode.name)
            .putBoolean(KEY_DATA_SAVER, next.respectDataSaver)
            .apply()
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val mode = prefs.getString(KEY_MODE, null)
                ?.let { runCatching { BgCanvasMode.valueOf(it) }.getOrNull() }
                ?: BgCanvasMode.Off
            val dataSaver = prefs.getBoolean(KEY_DATA_SAVER, true)
            _settings.value = CanvasSourceSettings(mode = mode, respectDataSaver = dataSaver)
            loaded = true
        }
    }
}
