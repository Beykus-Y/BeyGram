package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageUsageTest {
    @Test fun selectionDefaultsToCategoriesThatHaveFiles() {
        val usage = StorageUsage(mapOf(StorageCategory.Photos to 10L, StorageCategory.Music to 0L), databaseBytes = 5L)
        assertEquals(setOf(StorageCategory.Photos), usage.categoriesWithFiles())
        assertEquals(10L, usage.filesBytes)
        assertEquals(15L, usage.totalBytes)
    }
}
