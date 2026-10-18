package com.beykus.beygram.data.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Настройки памяти, которые меняет экран и читает фоновая проверка кэша. */
interface StorageSettingsSource {
    val settings: StateFlow<StorageSettings>

    /** Сохраняет новые настройки. Если запись не удалась, возвращает ошибку, а прежние настройки остаются. */
    fun update(transform: (StorageSettings) -> StorageSettings): Result<Unit>
}

/**
 * Настройки памяти. Хранятся в обычных настройках приложения, а не в базе TDLib: их читают и экран,
 * и фоновая проверка кэша, поэтому нужен один экземпляр на процесс.
 */
class StorageSettingsStore private constructor(context: Context) : StorageSettingsSource {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    override val settings: StateFlow<StorageSettings> = _settings.asStateFlow()

    @Synchronized
    override fun update(transform: (StorageSettings) -> StorageSettings): Result<Unit> {
        val previous = _settings.value
        val next = transform(previous)
        val saved = preferences.edit()
            .putString(KEY_SIZE_LIMIT, next.sizeLimit.name)
            .putString(KEY_RETENTION, next.retention.name)
            .commit()
        if (!saved) {
            // Запись на диск не удалась: возвращаем прежние значения, чтобы сохранённое и показанное не расходились.
            preferences.edit()
                .putString(KEY_SIZE_LIMIT, previous.sizeLimit.name)
                .putString(KEY_RETENTION, previous.retention.name)
                .commit()
            return Result.failure(IllegalStateException("Не удалось сохранить настройки памяти"))
        }
        _settings.value = next
        return Result.success(Unit)
    }

    private fun readSettings(): StorageSettings {
        val defaults = StorageSettings()
        return StorageSettings(
            sizeLimit = preferences.getString(KEY_SIZE_LIMIT, null).enumOr(defaults.sizeLimit),
            retention = preferences.getString(KEY_RETENTION, null).enumOr(defaults.retention)
        )
    }

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
