package com.beykus.beygram.data.tdlib

import com.beykus.beygram.data.media.TrafficUsageSession
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.MediaFileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileStoreEvictionTest {
    /** Files whose state was read again after being dropped; the repository answers each one with a TDLib GetFile. */
    private val refreshed = mutableListOf<Int>()

    private fun store(traffic: TrafficUsageSession = TrafficUsageSession(), maxEntries: Int = 8) =
        MediaFileStore(traffic, maxEntries) { refreshed += it }

    private fun flood(store: MediaFileStore, from: Int, count: Int = 100) {
        for (id in from until from + count) store.register(MediaFileState(id, size = 10))
    }

    @Test fun massRegistrationStaysWithinTheLimitAndKeepsTheNewestFile() {
        val store = store(maxEntries = 16)
        flood(store, from = 1_000, count = 5_000)
        assertTrue(store.entryCount() <= 16)
        assertNotNull(store.snapshot(1_000 + 4_999))
        assertNull(store.snapshot(1_000))
    }

    @Test fun repeatedCreationAndUseStaysWithinTheLimit() {
        val store = store(maxEntries = 16)
        repeat(20) { round ->
            flood(store, from = round * 1_000, count = 500)
            for (id in round * 1_000 until round * 1_000 + 500) store.cancel(id)
        }
        assertTrue(store.entryCount() <= 16)
    }

    @Test fun activeDownloadIsKept() {
        val store = store()
        assertTrue(store.beginDownload(1))
        flood(store, from = 100)
        assertTrue(store.observe(1).value.isDownloading)
        assertNotNull(store.snapshot(1))
    }

    @Test fun partlyDownloadedFileKeepsItsResumePoint() {
        val store = store()
        store.update(MediaFileState(1, size = 100, downloadedBytes = 40, downloadPath = "/partial", downloadedPrefixBytes = 40))
        flood(store, from = 100)
        assertEquals("/partial", store.snapshot(1)?.downloadPath)
        assertEquals(40L, store.snapshot(1)?.downloadedPrefixBytes)
    }

    @Test fun failedDownloadKeepsItsErrorUntilTheUserRetries() {
        val store = store()
        assertTrue(store.beginDownload(2))
        store.fail(2, UiText.Raw("offline"))
        flood(store, from = 100)
        assertEquals("offline", (store.snapshot(2)?.error as UiText.Raw).text)
    }

    @Test fun collectedFileIsKeptUntilTheCollectionEnds() = runBlocking {
        val store = store()
        store.register(MediaFileState(1, size = 10))
        val collection = launch(Dispatchers.Unconfined) { store.observe(1).collect { } }
        flood(store, from = 100)
        assertNotNull(store.snapshot(1))
        collection.cancelAndJoin()
        flood(store, from = 500)
        assertNull(store.snapshot(1))
    }

    @Test fun droppedFileIsReadAgainThroughTheRefreshHook() {
        val store = store()
        store.register(MediaFileState(1, size = 100))
        flood(store, from = 100)
        assertNull(store.snapshot(1))
        assertEquals(0L, store.observe(1).value.size)
        assertEquals(listOf(1), refreshed)
        // TDLib answers the refresh with the file's current state.
        store.update(MediaFileState(1, size = 100, localPath = "/photo.jpg", downloadedBytes = 100))
        assertEquals("/photo.jpg", store.observe(1).value.localPath)
        assertEquals(listOf(1), refreshed)
    }

    @Test fun droppedCompletedFileIsNotCountedAgainWhenItIsRequestedBeforeTheRefresh() {
        val traffic = TrafficUsageSession()
        val store = store(traffic)
        store.update(MediaFileState(5, size = 100, localPath = "/cached.jpg", downloadedBytes = 100))
        flood(store, from = 100)
        assertNull(store.snapshot(5))
        // An avatar asks for its file again before TDLib has answered the refresh.
        assertTrue(store.beginDownload(5))
        // TDLib reports the file as it already is: nothing was downloaded now.
        store.update(MediaFileState(5, size = 100, localPath = "/cached.jpg", downloadedBytes = 100))
        assertEquals(0L, traffic.snapshot.value.totalBytes)
        assertFalse(traffic.snapshot.value.hasActiveDownloads)
        assertEquals("/cached.jpg", store.observe(5).value.localPath)
    }

    @Test fun historySnapshotCannotRollBackAFileThatWasReadAgainAfterADrop() {
        val store = store()
        store.register(MediaFileState(1, size = 100))
        flood(store, from = 100)
        store.observe(1).value
        store.update(MediaFileState(1, size = 100, localPath = "/new.jpg", downloadedBytes = 100))
        // An older history page is registered after TDLib's newer state.
        store.register(MediaFileState(1, size = 100))
        assertEquals("/new.jpg", store.observe(1).value.localPath)
    }

    @Test fun historySnapshotDoesNotReplaceAStartedDownload() {
        val store = store()
        assertTrue(store.beginDownload(2))
        store.register(MediaFileState(2, size = 100))
        assertTrue(store.observe(2).value.isDownloading)
    }

    @Test fun retryAfterACancelledDownloadStartsAgainAfterADrop() {
        val store = store()
        assertTrue(store.beginDownload(3))
        store.cancel(3)
        flood(store, from = 100)
        assertTrue(store.beginDownload(3))
        assertTrue(store.observe(3).value.isDownloading)
    }

    @Test fun aRefreshThatSaysIdleDoesNotClearADownloadThatStartedBeforeIt() {
        val traffic = TrafficUsageSession()
        val store = store(traffic)
        store.register(MediaFileState(7, size = 100))
        flood(store, from = 100)
        // Reading the dropped file again asks TDLib for its state; the download below starts before the answer.
        assertEquals(0L, store.observe(7).value.size)
        assertEquals(listOf(7), refreshed)
        assertTrue(store.beginDownload(7))
        // The answer was produced before the download began, so it describes an idle file without data.
        store.refresh(MediaFileState(7, size = 100))
        assertTrue(store.observe(7).value.isDownloading)
        // The download's own update then reports progress, and the transfer is counted.
        store.update(MediaFileState(7, size = 100, isDownloading = true, downloadedBytes = 10))
        assertEquals(10L, store.observe(7).value.downloadedBytes)
        assertTrue(traffic.snapshot.value.hasActiveDownloads)
    }

    @Test fun aRunningUploadIsNotClearedByAnIdleRefresh() {
        val store = store()
        store.update(MediaFileState(8, size = 100, isUploading = true, uploadedBytes = 40))
        store.refresh(MediaFileState(8, size = 100))
        assertTrue(store.observe(8).value.isUploading)
        assertEquals(40L, store.observe(8).value.uploadedBytes)
    }

    @Test fun aRefreshThatBringsTheFileIsAppliedEvenWhileADownloadIsRequested() {
        val store = store()
        store.register(MediaFileState(9, size = 100))
        flood(store, from = 100)
        assertEquals(0L, store.observe(9).value.size)
        assertTrue(store.beginDownload(9))
        store.refresh(MediaFileState(9, size = 100, localPath = "/done.jpg", downloadedBytes = 100))
        assertEquals("/done.jpg", store.observe(9).value.localPath)
        assertFalse(store.observe(9).value.isDownloading)
    }
}
