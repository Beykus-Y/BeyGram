package com.beykus.beygram.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-local switches for moving from one music track to the next. Not Telegram account data. */
object TrackTransitionSettings {
    private const val PREFS_NAME = "track_transition_settings"
    private const val KEY_SKIP_SILENCE = "skip_silence"
    private const val DEFAULT_SKIP_SILENCE = true

    private val _skipSilence = MutableStateFlow(DEFAULT_SKIP_SILENCE)

    /** Skips the silence at the end of a track and at the start of the next one. On by default. */
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _skipSilence.value = try {
                context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_SKIP_SILENCE, DEFAULT_SKIP_SILENCE)
            } catch (_: RuntimeException) {
                DEFAULT_SKIP_SILENCE
            }
            loaded = true
        }
    }

    fun setSkipSilence(context: Context, value: Boolean) {
        _skipSilence.value = value
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SKIP_SILENCE, value)
                .apply()
        } catch (_: RuntimeException) {
            // The in-memory value still applies for this process.
        }
    }
}
