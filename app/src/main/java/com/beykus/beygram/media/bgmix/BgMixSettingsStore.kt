package com.beykus.beygram.media.bgmix

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Experimental switch for BG Mix music transitions. On by default. */
object BgMixSettingsStore {
    private const val PREFS_NAME = "bgmix_settings"
    private const val KEY_ENABLED = "bgmix_enabled"
    const val DEFAULT_ENABLED = true

    private val _enabled = MutableStateFlow(DEFAULT_ENABLED)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _enabled.value = try {
                context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
            } catch (_: RuntimeException) {
                DEFAULT_ENABLED
            }
            loaded = true
        }
    }

    internal fun resetForTests() {
        synchronized(this) {
            loaded = false
            _enabled.value = DEFAULT_ENABLED
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        _enabled.value = value
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, value)
                .apply()
        } catch (_: RuntimeException) {
            // The in-memory value still applies for this process.
        }
    }
}
