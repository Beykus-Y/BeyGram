package com.beykus.beygram.ui.screens.music

import android.content.Context

/** The source tab of the music screen. [Provider] exists only in builds with an optional music source. */
internal enum class MusicHomeSource(val storageKey: String) {
    Telegram("telegram"),
    Provider("provider");

    companion object {
        fun fromKey(key: String?): MusicHomeSource = entries.firstOrNull { it.storageKey == key } ?: Telegram
    }
}

/** Remembers the chosen tab of the music screen between launches. */
internal object MusicHomeSourceStore {
    private const val PREFS = "music_home"
    private const val KEY_SOURCE = "source"

    fun read(context: Context): MusicHomeSource = MusicHomeSource.fromKey(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SOURCE, null)
    )

    fun write(context: Context, source: MusicHomeSource) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SOURCE, source.storageKey).apply()
    }
}
