package com.beykus.beygram.data.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Настройки памяти. Хранятся в обычных настройках приложения, а не в базе TDLib: их читают и экран,
 * и фоновая проверка кэша, поэтому нужен один экземпляр на процесс.
 */
class StorageSettingsStore private constructor(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<StorageSettings> = _settings.asStateFlow()

    @Synchronized
    fun update(transform: (StorageSettings) -> StorageSettings) {
        val next = transform(_settings.value)
        check(preferences.edit()
            .putString(KEY_SIZE_LIMIT, next.sizeLimit.name)
            .putString(KEY_RETENTION, next.retention.name)
            .commit()) { "Не удалось сохранить настройки памяти" }
        _settings.value = next
    }

    private fun readSettings() = StorageSettings(
        sizeLimit = preferences.getString(KEY_SIZE_LIMIT, null).enumOr(StorageSizeLimit.FiveGigabytes),
        retention = preferences.getString(KEY_RETENTION, null).enumOr(StorageRetention.Forever)
    )

    private inline fun <reified T : Enum<T>> String?.enumOr(fallback: T): T =
        this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback

    companion object {
        private const val PREFERENCES = "storage-settings"
        private const val KEY_SIZE_LIMIT = "size_limit"
        private const val KEY_RETENTION = "retention"

        @Volatile private var instance: StorageSettingsStore? = null

        fun getInstance(context: Context): StorageSettingsStore =
            instance ?: synchronized(this) {
                instance ?: StorageSettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
