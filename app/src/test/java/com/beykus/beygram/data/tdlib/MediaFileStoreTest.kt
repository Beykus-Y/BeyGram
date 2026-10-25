package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.MediaFileState
import org.junit.Assert.*
import org.junit.Test

class MediaFileStoreTest {
    @Test fun staleHistoryCannotReplaceCompletedDownload() {
        val store = MediaFileStore()
        store.register(MediaFileState(1, size = 100))
        store.update(MediaFileState(1, size = 100, localPath = "/photo.jpg", downloadedBytes = 100))
        store.register(MediaFileState(1, size = 100))
        assertEquals("/photo.jpg", store.observe(1).value.localPath)
        assertFalse(store.beginDownload(1))
    }

    @Test fun concurrentRequestsShareDownloadAndFailureAllowsRetry() {
        val store = MediaFileStore()
        assertTrue(store.beginDownload(4))
        assertFalse(store.beginDownload(4))
        store.fail(4, UiText.Raw("offline"))
        assertTrue(store.beginDownload(4))
        assertNull(store.observe(4).value.error)
    }

    @Test fun progressIsBoundedAndUnknownSizeDoesNotDivideByZero() {
        assertEquals(0f, MediaFileState(1).downloadProgress)
        assertEquals(1f, MediaFileState(1, size = 10, downloadedBytes = 20).downloadProgress)
        assertEquals(0.5f, MediaFileState(1, size = 100, uploadedBytes = 50).uploadProgress)
    }

    @Test fun cancelledAutomaticDownloadKeepsPartialStateAndAllowsManualRetry() {
        val store = MediaFileStore()
        store.update(MediaFileState(7, size = 100, downloadedBytes = 40, downloadPath = "/partial", downloadedPrefixBytes = 40))
        assertTrue(store.beginDownload(7))
        store.cancel(7)
        val cancelled = store.observe(7).value
        assertFalse(cancelled.isDownloading)
        assertNull(cancelled.error)
        assertEquals("/partial", cancelled.downloadPath)
        assertEquals(40L, cancelled.downloadedPrefixBytes)
        assertTrue(store.beginDownload(7))
    }

    @Test fun snapshotExposesTheSameStateAsObservers() {
        val store = MediaFileStore()
        store.update(MediaFileState(3, size = 10, isDownloading = true))
        assertEquals(store.observe(3).value, store.snapshot(3))
    }
}
