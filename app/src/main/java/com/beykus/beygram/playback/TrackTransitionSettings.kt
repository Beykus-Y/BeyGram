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
    private const val KEY_CROSSFADE = "crossfade"
    private const val DEFAULT_CROSSFADE = false

    private val _skipSilence = MutableStateFlow(DEFAULT_SKIP_SILENCE)
    private val _crossfade = MutableStateFlow(DEFAULT_CROSSFADE)

    /** Skips the silence at the end of a track and at the start of the next one. On by default. */
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    /** Overlaps the end of a music track with the start of the next one. Off by default. */
    val crossfade: StateFlow<Boolean> = _crossfade.asStateFlow()

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                _skipSilence.value = prefs.getBoolean(KEY_SKIP_SILENCE, DEFAULT_SKIP_SILENCE)
                _crossfade.value = prefs.getBoolean(KEY_CROSSFADE, DEFAULT_CROSSFADE)
            } catch (_: RuntimeException) {
                _skipSilence.value = DEFAULT_SKIP_SILENCE
                _crossfade.value = DEFAULT_CROSSFADE
            }
            loaded = true
        }
    }

    fun setSkipSilence(context: Context, value: Boolean) {
        _skipSilence.value = value
        writeBoolean(context, KEY_SKIP_SILENCE, value)
    }

    fun setCrossfade(context: Context, value: Boolean) {
        _crossfade.value = value
        writeBoolean(context, KEY_CROSSFADE, value)
    }

    private fun writeBoolean(context: Context, key: String, value: Boolean) {
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, value)
                .apply()
        } catch (_: RuntimeException) {
            // The in-memory value still applies for this process.
        }
    }
}
