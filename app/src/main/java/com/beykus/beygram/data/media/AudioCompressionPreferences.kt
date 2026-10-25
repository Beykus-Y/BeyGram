package com.beykus.beygram.data.media

import android.content.Context

/** Composer-level audio compression preset, stored next to the video preset. */
internal class AudioCompressionPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): AudioQualityOption {
        val stored = preferences.getString(KEY_QUALITY, null)
        return AudioQualityOption.entries.firstOrNull { it.name == stored } ?: AudioQualityOption.Balanced
    }

    fun save(option: AudioQualityOption) {
        preferences.edit().putString(KEY_QUALITY, option.name).apply()
    }

    private companion object {
        const val PREFERENCES = "media_composer"
        const val KEY_QUALITY = "audio_quality"
    }
}
