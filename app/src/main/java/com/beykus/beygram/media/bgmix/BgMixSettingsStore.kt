package com.beykus.beygram.media.bgmix

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Experimental switch for BG Mix music transitions. Off by default. */
object BgMixSettingsStore {
    private const val PREFS_NAME = "bgmix_settings"
    private const val KEY_ENABLED = "bgmix_enabled"

    private val _enabled = MutableStateFlow(false)
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
                    .getBoolean(KEY_ENABLED, false)
            } catch (_: RuntimeException) {
                false
            }
            loaded = true
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
