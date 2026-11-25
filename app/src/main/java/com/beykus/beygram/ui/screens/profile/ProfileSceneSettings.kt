package com.beykus.beygram.ui.screens.profile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which profile layout is shown. A is the stable default; C is the experimental adaptive scene (#1138). */
enum class ProfileVariant { A, C }

/**
 * Experiment switch for Profile 2.0 C, the adaptive scene. Off by default in every channel, including
 * Stable. Turning it off always returns the stable profile A. Never switches users to C on update.
 */
object ProfileSceneSettings {
    const val PREFS_NAME = "profile_scene_settings"
    const val KEY_ENABLED = "scene_enabled"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _enabled.value = readEnabled(context)
            loaded = true
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        _enabled.value = value
        try {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, value).apply()
        } catch (_: RuntimeException) {
            // The in-memory value still applies for this process.
        }
    }

    /** Pure mapping, kept separate so it can be unit tested without Android. */
    fun variantFor(sceneEnabled: Boolean): ProfileVariant = if (sceneEnabled) ProfileVariant.C else ProfileVariant.A

    private fun readEnabled(context: Context): Boolean = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    } catch (_: RuntimeException) {
        false
    }
}
