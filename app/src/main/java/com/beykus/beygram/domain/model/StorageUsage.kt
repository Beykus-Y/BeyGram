package com.beykus.beygram.domain.model

/** Категории занятого места, которые показываются и очищаются. Переписка к ним не относится. */
enum class StorageCategory { Photos, Videos, Files, VoiceAndVideoNotes, Music, Other }

/**
 * Место, которое занимает приложение. [bytesByCategory] — загруженные файлы: их можно удалить и скачать снова.
 * [databaseBytes] — переписка на устройстве: никакая очистка её не трогает.
 */
data class StorageUsage(
    val bytesByCategory: Map<StorageCategory, Long>,
    val databaseBytes: Long
) {
    val filesBytes: Long get() = bytesByCategory.values.sum()
    val totalBytes: Long get() = filesBytes + databaseBytes

    fun bytes(category: StorageCategory): Long = bytesByCategory[category] ?: 0L

    fun categoriesWithFiles(): Set<StorageCategory> = StorageCategory.entries.filterTo(mutableSetOf()) { bytes(it) > 0L }
}

/** Сколько байт файлов держать и сколько секунд файл живёт после последнего открытия. Null — без ограничения. */
data class CachePolicy(val maxBytes: Long?, val maxAgeSeconds: Int?)
