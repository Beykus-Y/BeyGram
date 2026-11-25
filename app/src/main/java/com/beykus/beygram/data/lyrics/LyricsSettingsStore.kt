package com.beykus.beygram.data.lyrics

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LyricsSourceSettings(
    val externalLookupEnabled: Boolean = false,
    val embeddedTagsEnabled: Boolean = true,
    val yandexEnabled: Boolean = true,
    val lrcLibEnabled: Boolean = true,
)

/**
 * Persistent opt-in and source toggles for BG Lyrics 2.0.
 * External lookup is disabled by default until the user explicitly enables it.
 */
object LyricsSettingsStore {
    const val PREFS_NAME = "beygram_lyrics_v2"
    private const val KEY_EXTERNAL_ENABLED = "external_lookup_enabled"
    private const val KEY_EMBEDDED_ENABLED = "embedded_tags_enabled"
    private const val KEY_YANDEX_ENABLED = "yandex_enabled"
    private const val KEY_LRCLIB_ENABLED = "lrclib_enabled"

    private val lock = Any()
    @Volatile
    private var loaded = false
    private val _settings = MutableStateFlow(LyricsSourceSettings())

    fun flow(context: Context): StateFlow<LyricsSourceSettings> {
        ensureLoaded(context)
        return _settings.asStateFlow()
    }

    fun current(context: Context): LyricsSourceSettings {
        ensureLoaded(context)
        return _settings.value
    }

    fun setExternalLookupEnabled(context: Context, enabled: Boolean) {
        update(context) { it.copy(externalLookupEnabled = enabled) }
    }

    fun update(context: Context, transform: (LyricsSourceSettings) -> LyricsSourceSettings) {
        ensureLoaded(context)
        val next = synchronized(lock) {
            val updated = transform(_settings.value)
            _settings.value = updated
            updated
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXTERNAL_ENABLED, next.externalLookupEnabled)
            .putBoolean(KEY_EMBEDDED_ENABLED, next.embeddedTagsEnabled)
            .putBoolean(KEY_YANDEX_ENABLED, next.yandexEnabled)
            .putBoolean(KEY_LRCLIB_ENABLED, next.lrcLibEnabled)
            .apply()
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _settings.value = LyricsSourceSettings(
                externalLookupEnabled = prefs.getBoolean(KEY_EXTERNAL_ENABLED, false),
                embeddedTagsEnabled = prefs.getBoolean(KEY_EMBEDDED_ENABLED, true),
                yandexEnabled = prefs.getBoolean(KEY_YANDEX_ENABLED, true),
                lrcLibEnabled = prefs.getBoolean(KEY_LRCLIB_ENABLED, true),
            )
            loaded = true
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            loaded = false
            _settings.value = LyricsSourceSettings()
        }
    }
}
