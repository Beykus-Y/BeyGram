package com.beykus.beygram.domain.repository

import com.beykus.beygram.domain.model.CachePolicy
import com.beykus.beygram.domain.model.StorageCategory
import com.beykus.beygram.domain.model.StorageUsage

/** Занятое место и очистка загруженных файлов. Ни один метод не трогает переписку. */
interface StorageRepository {
    /** Занятое место по категориям и размер переписки. */
    suspend fun storageUsage(): Result<StorageUsage>

    /**
     * Удаляет файлы выбранных категорий по одной категории за раз. [onProgress] получает число готовых категорий
     * и общее число. Возвращает, сколько байт освобождено.
     */
    suspend fun clearStorage(categories: Set<StorageCategory>, onProgress: (done: Int, total: Int) -> Unit): Result<Long>

    /** Приводит файлы к лимитам: лишнее удаляется. Возвращает, сколько байт освобождено. */
    suspend fun applyCachePolicy(policy: CachePolicy): Result<Long>
}
