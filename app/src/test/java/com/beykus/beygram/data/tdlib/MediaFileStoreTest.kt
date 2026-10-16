package com.beykus.beygram.data.tdlib

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
        store.fail(4, "offline")
        assertTrue(store.beginDownload(4))
        assertNull(store.observe(4).value.error)
    }

    @Test fun progressIsBoundedAndUnknownSizeDoesNotDivideByZero() {
        assertEquals(0f, MediaFileState(1).downloadProgress)
        assertEquals(1f, MediaFileState(1, size = 10, downloadedBytes = 20).downloadProgress)
        assertEquals(0.5f, MediaFileState(1, size = 100, uploadedBytes = 50).uploadProgress)
    }
}
