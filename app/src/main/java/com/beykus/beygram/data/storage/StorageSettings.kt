package com.beykus.beygram.data.storage

import com.beykus.beygram.domain.model.CachePolicy

private const val BYTES_PER_GIGABYTE = 1024L * 1024L * 1024L
private const val SECONDS_PER_DAY = 24 * 60 * 60

/** Лимит размера загруженных файлов. [gigabytes] = null — без ограничения. */
enum class StorageSizeLimit(val gigabytes: Int?) {
    OneGigabyte(1), TwoGigabytes(2), FiveGigabytes(5), TenGigabytes(10), Unlimited(null)
}

/** Срок хранения файла после последнего открытия. [days] = null — файлы не удаляются по сроку. */
enum class StorageRetention(val days: Int?) {
    ThreeDays(3), Week(7), Month(30), Forever(null)
}

/** По умолчанию кэш держим в пределах 10 ГБ, а по сроку ничего не удаляем. */
data class StorageSettings(
    val sizeLimit: StorageSizeLimit = StorageSizeLimit.TenGigabytes,
    val retention: StorageRetention = StorageRetention.Forever
) {
    fun policy(): CachePolicy = CachePolicy(
        maxBytes = sizeLimit.gigabytes?.let { it * BYTES_PER_GIGABYTE },
        maxAgeSeconds = retention.days?.let { it * SECONDS_PER_DAY }
    )
}
