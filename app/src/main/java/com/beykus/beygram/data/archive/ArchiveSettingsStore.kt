package com.beykus.beygram.data.archive

import android.content.Context
import com.beykus.beygram.domain.model.ArchiveSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared UI architecture, but settings belong to a specific Telegram account.
 * The original login retains its exact historical preferences filename.
 */
internal fun archivePreferencesName(accountId: String): String {
    require(accountId.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
    return if (accountId == "legacy") "chats" else "chats_$accountId"
}
class ArchiveSettingsStore private constructor(context: Context, accountId: String) {
    private val preferences = context.applicationContext.getSharedPreferences(
        archivePreferencesName(accountId), Context.MODE_PRIVATE
    )
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
        private val instances = mutableMapOf<String, ArchiveSettingsStore>()

        fun releaseAccount(accountId: String) {
            if (accountId == "legacy") return
            synchronized(instances) { instances.remove(accountId) }
        }

        fun get(
            context: Context,
            accountId: String = "legacy"
        ): ArchiveSettingsStore = synchronized(instances) {
            instances.getOrPut(accountId) { ArchiveSettingsStore(context.applicationContext, accountId) }
        }
    }
}
