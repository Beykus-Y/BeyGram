package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.CachePolicy
import com.beykus.beygram.domain.model.StorageCategory
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCleanupRulesTest {
    @Test fun fileTypesFallIntoTheCategoriesShownToTheUser() {
        assertEquals(StorageCategory.Photos, storageCategoryOf(TdApi.FileTypePhoto()))
        assertEquals(StorageCategory.Photos, storageCategoryOf(TdApi.FileTypeSelfDestructingPhoto()))
        assertEquals(StorageCategory.Videos, storageCategoryOf(TdApi.FileTypeVideo()))
        assertEquals(StorageCategory.Videos, storageCategoryOf(TdApi.FileTypeAnimation()))
        assertEquals(StorageCategory.Files, storageCategoryOf(TdApi.FileTypeDocument()))
        assertEquals(StorageCategory.VoiceAndVideoNotes, storageCategoryOf(TdApi.FileTypeVoiceNote()))
        assertEquals(StorageCategory.VoiceAndVideoNotes, storageCategoryOf(TdApi.FileTypeVideoNote()))
        assertEquals(StorageCategory.Music, storageCategoryOf(TdApi.FileTypeAudio()))
        assertEquals(StorageCategory.Other, storageCategoryOf(TdApi.FileTypeSticker()))
        assertEquals(StorageCategory.Other, storageCategoryOf(TdApi.FileTypeThumbnail()))
        assertEquals(StorageCategory.Other, storageCategoryOf(TdApi.FileTypeUnknown()))
    }

    @Test fun policyWithoutLimitsAsksTdlibForNothing() {
        assertNull(CachePolicy(maxBytes = null, maxAgeSeconds = null).optimizeRequest(arrayOf<TdApi.FileType>(TdApi.FileTypePhoto())))
    }

    @Test fun sizeLimitLeavesAgeAndCountUnlimitedAndCountsEveryGivenType() {
        val types = arrayOf<TdApi.FileType>(TdApi.FileTypePhoto(), TdApi.FileTypeSticker())
        val request = CachePolicy(maxBytes = 5 * GIGABYTE, maxAgeSeconds = null).optimizeRequest(types)!!
        assertEquals(5 * GIGABYTE, request.size)
        assertEquals(Int.MAX_VALUE, request.ttl)
        assertEquals(Int.MAX_VALUE, request.count)
        assertEquals(-1, request.immunityDelay)
        assertArrayEquals(types, request.fileTypes)
        assertTrue(request.returnDeletedFileStatistics)
    }

    @Test fun ageLimitDoesNotCutBySize() {
        val request = CachePolicy(maxBytes = null, maxAgeSeconds = 7 * 24 * 60 * 60).optimizeRequest(arrayOf<TdApi.FileType>(TdApi.FileTypePhoto()))!!
        assertEquals((1L shl 53) - 1, request.size)
        assertEquals(604_800, request.ttl)
    }

    @Test fun clearingRemovesEverythingOfTheGivenTypes() {
        val types = arrayOf<TdApi.FileType>(TdApi.FileTypePhoto(), TdApi.FileTypePhotoStory())
        val request = clearFileTypesRequest(types)
        assertEquals(0L, request.size)
        assertEquals(0, request.ttl)
        assertEquals(0, request.count)
        assertEquals(0, request.immunityDelay)
        assertArrayEquals(types, request.fileTypes)
        assertTrue(request.returnDeletedFileStatistics)
    }

    private companion object {
        const val GIGABYTE = 1024L * 1024L * 1024L
    }
}
