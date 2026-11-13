package com.beykus.beygram.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexDownloadGrowingTest {
    private val file = File("/cache/music-yandex/copy.mp3")

    @Test
    fun noReaderBeforeTheStartBytesAreOnDisk() {
        val download = YandexDownload()
        assertNull(download.growingCopy())
        download.start(file, total = 9_000_000L)
        download.advance(YANDEX_PLAY_START_BYTES - 1)
        assertNull(download.growingCopy())
    }

    @Test
    fun readerFollowsTheFileWhileItIsWritten() {
        val download = YandexDownload()
        download.start(file, total = 9_000_000L)
        download.advance(YANDEX_PLAY_START_BYTES)

        val copy = assertNotNull(download.growingCopy())
        assertEquals(file.absolutePath, copy.path)
        assertEquals(9_000_000L, copy.growing.totalSize)
        assertTrue(copy.growing.advertiseLength)
        assertTrue(copy.growing.isDownloading())
        assertFalse(copy.growing.isComplete())
        assertEquals(YANDEX_PLAY_START_BYTES, copy.growing.readablePrefix())
    }

    @Test
    fun finishedDownloadCompletesTheReader() {
        val download = YandexDownload()
        download.start(file, total = 9_000_000L)
        download.advance(9_000_000L)
        download.finish()

        val copy = assertNotNull(download.growingCopy())
        assertTrue(copy.growing.isComplete())
        assertFalse(copy.growing.isDownloading())
        assertEquals(file.absolutePath, copy.growing.completedPath())
    }

    @Test
    fun unknownLengthDoesNotAdvertiseOne() {
        val download = YandexDownload()
        download.start(file, total = -1L)
        download.advance(YANDEX_PLAY_START_BYTES)
        assertFalse(assertNotNull(download.growingCopy()).growing.advertiseLength)
    }

    @Test
    fun failedDownloadHasNoReaderAndIsNotDownloading() {
        val download = YandexDownload()
        download.start(file, total = 9_000_000L)
        download.advance(YANDEX_PLAY_START_BYTES)
        download.fail()
        assertNull(download.growingCopy())
    }
}
