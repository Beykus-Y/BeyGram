package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.MediaFileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaFileProgressTest {
    private val base = MediaFileState(id = 7, size = 1000, downloadedBytes = 100, downloadedPrefixBytes = 100, isDownloading = true)

    @Test fun progressOnlyDifferenceIsEqualAfterWithoutProgress() {
        val later = base.copy(downloadedBytes = 900, downloadedPrefixBytes = 900)
        assertEquals(base.withoutProgress(), later.withoutProgress())
    }

    @Test fun uploadProgressOnlyDifferenceIsEqualAfterWithoutProgress() {
        val early = MediaFileState(id = 7, size = 1000, uploadedBytes = 10, isUploading = true)
        val later = early.copy(uploadedBytes = 500)
        assertEquals(early.withoutProgress(), later.withoutProgress())
    }

    @Test fun statusFieldsAreKept() {
        val stripped = base.withoutProgress()
        assertEquals(base.size, stripped.size)
        assertEquals(base.isDownloading, stripped.isDownloading)
        assertEquals(0L, stripped.downloadedBytes)
    }

    @Test fun localPathChangeIsVisible() {
        assertNotEquals(base.withoutProgress(), base.copy(localPath = "/a.mp4").withoutProgress())
    }

    @Test fun downloadingFlagChangeIsVisible() {
        assertNotEquals(base.withoutProgress(), base.copy(isDownloading = false).withoutProgress())
    }

    @Test fun uploadingFlagChangeIsVisible() {
        assertNotEquals(base.withoutProgress(), base.copy(isUploading = true).withoutProgress())
    }

    @Test fun sizeChangeIsVisible() {
        assertNotEquals(base.withoutProgress(), base.copy(size = 2000).withoutProgress())
    }

    @Test fun errorChangeIsVisible() {
        assertNotEquals(base.withoutProgress(), base.copy(error = UiText.Raw("failed")).withoutProgress())
    }
}
