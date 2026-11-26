package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MusicResourceOwnershipTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun item(idx: Int, path: String = "/cache/track_$idx.mp3") = PlaybackItem(
        key = "ym:$idx",
        accountId = "legacy",
        kind = MediaKind.Audio,
        path = path,
        title = "Track $idx",
        performer = "Artist",
        durationSeconds = 180,
    )

    @Test
    fun activeMusicOwnershipPathsBoundsLongQueueToWindow() {
        val queue = (0 until 150).map { item(it) }
        val state = PlaybackState(
            item = queue[100],
            queue = queue,
            queueIndex = 100,
        )
        val paths = activeMusicOwnershipPaths(
            state = state,
            interrupted = listOf(item(999, "/cache/interrupted.mp3")),
            bgMixPaths = setOf("/cache/bgmix_tail.mp3"),
            behindCount = 1,
            aheadCount = 3,
        )
        // [99..103] = 5 items + interrupted + bgMixPaths = 7 paths total, not 150.
        assertEquals(7, paths.size)
        assertTrue("/cache/track_99.mp3" in paths)
        assertTrue("/cache/track_100.mp3" in paths)
        assertTrue("/cache/track_103.mp3" in paths)
        assertTrue("/cache/interrupted.mp3" in paths)
        assertTrue("/cache/bgmix_tail.mp3" in paths)
        assertFalse("/cache/track_0.mp3" in paths)
        assertFalse("/cache/track_98.mp3" in paths)
        assertFalse("/cache/track_104.mp3" in paths)
    }

    @Test
    fun lazyTrackResolverEvictOutsideRemovesCompletedEntriesOutsideKeep() = runBlocking {
        val discarded = mutableListOf<String>()
        val resolver = object : LazyTrackResolver<String>("test:", maxConcurrentFetches = 2) {
            override val restricted: Boolean = false
            override fun pathOf(track: String): String = track
            override fun discardCopy(path: String) {
                discarded += path
                File(path).delete()
            }
            override fun handOverCopy(path: String) = Unit
            fun wire(f1: File, f2: File) {
                setFetch { key ->
                    val f = if (key == "test:1") f1 else f2
                    Result.success(f.absolutePath)
                }
            }
        }
        val f1 = tempFolder.newFile("t1.mp3").apply { writeText("1") }
        val f2 = tempFolder.newFile("t2.mp3").apply { writeText("2") }
        resolver.wire(f1, f2)
        resolver.request("test:1")
        resolver.request("test:2")

        // Wait briefly for IO coroutines to populate entries.
        val deadline = System.currentTimeMillis() + 3_000L
        while (System.currentTimeMillis() < deadline &&
            (resolver.resolvedPath("test:1") == null || resolver.resolvedPath("test:2") == null)
        ) {
            Thread.sleep(20L)
        }
        assertEquals(f1.absolutePath, resolver.resolvedPath("test:1"))
        assertEquals(f2.absolutePath, resolver.resolvedPath("test:2"))

        val changed = resolver.evictOutside(keep = setOf("test:2"))
        assertTrue(changed)
        assertNull(resolver.resolvedPath("test:1"))
        assertEquals(f2.absolutePath, resolver.resolvedPath("test:2"))
        assertTrue(f1.absolutePath in discarded)
        assertFalse(f1.exists())
        assertTrue(f2.exists())
    }

    @Test
    fun pruneCopiesProtectsLiveInUseAndReservedFilesAndDeletesOldOnes() {
        val dir = tempFolder.newFolder("music-yandex")
        val oldFile = File(dir, "old.mp3").apply { writeText("old") }
        val liveFile = File(dir, "live.mp3").apply { writeText("live") }
        val inUseFile = File(dir, "inuse.mp3").apply { writeText("inuse") }
        val reservedFile = File(dir, "reserved.mp3").apply { writeText("reserved") }

        YandexAudioDownloader.reserved += reservedFile.absolutePath
        try {
            YandexTrackResolver.pruneCopies(
                dir = dir,
                keepPaths = setOf(liveFile.absolutePath, inUseFile.absolutePath) + YandexAudioDownloader.reserved,
            )
            assertFalse(oldFile.exists())
            assertTrue(liveFile.exists())
            assertTrue(inUseFile.exists())
            assertTrue(reservedFile.exists())
        } finally {
            YandexAudioDownloader.reserved -= reservedFile.absolutePath
        }
    }

    @Test
    fun cancellingDownloadDisconnectsBlockedHttpReadPromptly() = runBlocking {
        val dir = tempFolder.newFolder("stalled-http")
        val readStarted = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val disconnectCalled = AtomicBoolean(false)

        val fakeConnection = object : HttpURLConnection(URL("https://example.com/track.mp3")) {
            override fun connect() = Unit
            override fun usingProxy(): Boolean = false
            override fun getResponseCode(): Int = HTTP_OK
            override fun getContentLengthLong(): Long = 1_048_576L
            override fun getInputStream(): InputStream = object : InputStream() {
                override fun read(): Int {
                    readStarted.countDown()
                    disconnected.await(15, TimeUnit.SECONDS)
                    throw IOException("disconnected")
                }
            }
            override fun disconnect() {
                disconnectCalled.set(true)
                disconnected.countDown()
            }
        }

        val startedAt = System.currentTimeMillis()
        val job = launch(Dispatchers.IO) {
            runCatching {
                YandexAudioDownloader.download(
                    link = YandexAudioLink("https://example.com/track.mp3", "mp3"),
                    dir = dir,
                    cancelJob = coroutineContext[Job],
                    openConnection = { fakeConnection },
                )
            }
        }
        assertTrue(readStarted.await(3, TimeUnit.SECONDS))
        job.cancel()
        job.join()
        val elapsedMs = System.currentTimeMillis() - startedAt
        assertTrue(disconnectCalled.get())
        assertTrue("Expected prompt abort (<2000ms), took ${elapsedMs}ms", elapsedMs < 2_000L)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        assertTrue(YandexAudioDownloader.reserved.none { it.startsWith(dir.absolutePath) })
    }
}
