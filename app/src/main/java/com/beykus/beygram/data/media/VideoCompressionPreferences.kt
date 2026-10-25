package com.beykus.beygram.data.media

import android.content.Context

/**
 * Composer-level video compression preset. Stored separately from video-note settings: notes have
 * their own quality policy and must never be re-encoded by this path.
 */
internal class VideoCompressionPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): VideoQualityOption {
        val stored = preferences.getString(KEY_QUALITY, null)
        return VideoQualityOption.entries.firstOrNull { it.name == stored } ?: VideoQualityOption.Balanced
    }

    fun save(option: VideoQualityOption) {
        preferences.edit().putString(KEY_QUALITY, option.name).apply()
    }

    private companion object {
        const val PREFERENCES = "media_composer"
        const val KEY_QUALITY = "video_quality"
    }
}
