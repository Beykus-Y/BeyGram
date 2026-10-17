package com.beykus.beygram.data.presence

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Как показывать время последнего входа собеседника: «5 минут назад» или дата и время. */
enum class LastSeenStyle { Relative, Exact }

/** App-private setting for the "last seen" text. It does not touch the TDLib database. */
class LastSeenSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _style = MutableStateFlow(readStyle())
    val style: StateFlow<LastSeenStyle> = _style.asStateFlow()

    fun setStyle(style: LastSeenStyle) {
        preferences.edit().putString(KEY_STYLE, style.name).apply()
        _style.value = style
    }

    private fun readStyle(): LastSeenStyle {
        val saved = preferences.getString(KEY_STYLE, null)
        return LastSeenStyle.entries.firstOrNull { it.name == saved } ?: LastSeenStyle.Relative
    }

    private companion object {
        const val PREFERENCES = "last-seen-settings"
        const val KEY_STYLE = "style"
    }
}
