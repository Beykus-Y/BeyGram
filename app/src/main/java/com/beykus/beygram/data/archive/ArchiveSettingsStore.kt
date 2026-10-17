package com.beykus.beygram.data.archive

import android.content.Context
import com.beykus.beygram.domain.model.ArchiveSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Хранит настройки в том же файле, что и скрытие архива, чтобы их читали и репозиторий, и уведомления. */
class ArchiveSettingsStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("chats", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<ArchiveSettings> = _state.asStateFlow()

    private fun read() = ArchiveSettings(
        returnOnNewMessage = preferences.getBoolean(KEY_RETURN_ON_MESSAGE, false),
        archiveSound = preferences.getBoolean(KEY_ARCHIVE_SOUND, false)
    )

    fun update(value: ArchiveSettings) {
        preferences.edit().putBoolean(KEY_RETURN_ON_MESSAGE, value.returnOnNewMessage).putBoolean(KEY_ARCHIVE_SOUND, value.archiveSound).apply()
        _state.value = value
    }

    companion object {
        private const val KEY_RETURN_ON_MESSAGE = "archive_return_on_message"
        private const val KEY_ARCHIVE_SOUND = "archive_sound"
        @Volatile private var instance: ArchiveSettingsStore? = null

        fun get(context: Context): ArchiveSettingsStore = instance ?: synchronized(this) {
            instance ?: ArchiveSettingsStore(context).also { instance = it }
        }
    }
}
