package com.beykus.beygram.data.tdlib

import com.beykus.beygram.data.media.TrafficOrigin
import com.beykus.beygram.data.media.TrafficUsageSession
import com.beykus.beygram.domain.model.MediaFileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileStoreTrafficTest {
    private fun store(traffic: TrafficUsageSession) = MediaFileStore(traffic)

    @Test
    fun automaticDownloadCountsProgressOnce() {
        val traffic = TrafficUsageSession()
        val store = store(traffic)

        assertTrue(store.beginAutoDownload(1))
        store.update(MediaFileState(1, size = 100, downloadedBytes = 40, isDownloading = true))
        store.update(MediaFileState(1, size = 100, downloadedBytes = 40, isDownloading = true))
        store.update(MediaFileState(1, size = 100, downloadedBytes = 100, isDownloading = true))
        store.update(MediaFileState(1, size = 100, downloadedBytes = 100, localPath = "/file"))

        val snapshot = traffic.snapshot.value
        assertEquals(100L, snapshot.autoBytes)
        assertEquals(0L, snapshot.manualBytes)
        assertFalse(snapshot.hasActiveDownloads)
    }

    @Test
    fun manualTapTakesOverAnAutomaticTransfer() {
        val traffic = TrafficUsageSession()
        val store = store(traffic)

        assertTrue(store.beginAutoDownload(2))
        store.update(MediaFileState(2, size = 100, downloadedBytes = 40, isDownloading = true))
        assertFalse(store.beginDownload(2))
        store.update(MediaFileState(2, size = 100, downloadedBytes = 100, isDownloading = true))

        val snapshot = traffic.snapshot.value
        assertEquals(40L, snapshot.autoBytes)
        assertEquals(60L, snapshot.manualBytes)
        assertEquals(TrafficOrigin.Manual, snapshot.activeOrigins[2])
    }

    @Test
    fun pausedManualTransferStaysManualWhenItResumes() {
        val traffic = TrafficUsageSession()
        val store = store(traffic)

        store.beginDownload(6)
        store.update(MediaFileState(6, size = 100, downloadedBytes = 40, isDownloading = true))
        // TDLib reports a pause while the connection is unstable: the transfer leaves the active map.
        store.update(MediaFileState(6, size = 100, downloadedBytes = 40, isDownloading = false))
        store.update(MediaFileState(6, size = 100, downloadedBytes = 60, isDownloading = true))

        val snapshot = traffic.snapshot.value
        assertEquals(0L, snapshot.autoBytes)
        assertEquals(60L, snapshot.manualBytes)
        assertEquals(TrafficOrigin.Manual, snapshot.activeOrigins[6])
    }

    @Test
    fun cancellingKeepsBytesAlreadyDownloaded() {
        val traffic = TrafficUsageSession()
        val store = store(traffic)

        store.beginAutoDownload(3)
        store.update(MediaFileState(3, size = 100, downloadedBytes = 40, isDownloading = true))
        store.cancel(3)

        val cancelled = traffic.snapshot.value
        assertEquals(40L, cancelled.autoBytes)
        assertFalse(cancelled.hasActiveDownloads)

        store.beginDownload(3)
        store.update(MediaFileState(3, size = 100, downloadedBytes = 80, isDownloading = true))
        assertEquals(40L, traffic.snapshot.value.autoBytes)
        assertEquals(40L, traffic.snapshot.value.manualBytes)
    }

    @Test
    fun backgroundPreloadWithoutARequestIsCountedAsAutomatic() {
        val traffic = TrafficUsageSession()
        val store = store(traffic)

        store.update(MediaFileState(4, size = 100, downloadedBytes = 30, isDownloading = true))

        val snapshot = traffic.snapshot.value
        assertEquals(30L, snapshot.autoBytes)
        assertEquals(TrafficOrigin.Auto, snapshot.activeOrigins[4])
    }

    @Test
    fun historySnapshotAndCompletedFileDoNotStartCounting() {
        val traffic = TrafficUsageSession()
        val store = store(traffic)

        store.register(MediaFileState(5, size = 100, downloadedBytes = 100, localPath = "/cached"))
        assertFalse(store.beginAutoDownload(5))

        assertEquals(0L, traffic.snapshot.value.totalBytes)
        assertFalse(traffic.snapshot.value.hasActiveDownloads)
    }
}
