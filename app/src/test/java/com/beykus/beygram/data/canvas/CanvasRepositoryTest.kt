package com.beykus.beygram.data.canvas

import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.music.TrackIdentity
import com.beykus.beygram.playback.PlaybackItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class CanvasRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun yandexIdentity(accountId: String = "acc1", secret: Boolean = false) =
        TrackIdentity.fromPlaybackItem(
            PlaybackItem(
                key = "ym:777",
                kind = MediaKind.Audio,
                path = "",
                title = "Track",
                durationSeconds = 180,
                accountId = accountId,
                secret = secret,
            )
        )

    private fun trackWithVideo(uri: String = "https://cdn.example/clip.mp4") = ProviderTrack(
        trackId = "777",
        title = "Track",
        artists = listOf("Artist"),
        albumTitle = null,
        durationMs = 180_000L,
        coverUrl = null,
        isAvailable = true,
        backgroundVideoUri = uri,
        backgroundVideoId = "vid777",
    )

    @Test
    fun downloadsCachesAndReusesCanvasWithoutRedownloading() = runBlocking {
        val downloads = AtomicInteger(0)
        val cache = CanvasDiskCache(
            rootDir = tempFolder.root,
            downloader = CanvasDownloader { _, target, _ ->
                downloads.incrementAndGet()
                val bytes = ByteArray(1024) { 1 }
                target.writeBytes(bytes)
                bytes.size.toLong()
            },
        )
        val repo = CanvasRepository(
            diskCache = cache,
            trackLookup = { trackWithVideo() },
        )
        val settings = CanvasSourceSettings(mode = BgCanvasMode.Card)
        val states1 = repo.observeCanvas(yandexIdentity(), settings).toList()
        assertTrue(states1.last() is CanvasLoadState.Ready)
        assertEquals(1, downloads.get())

        val states2 = repo.observeCanvas(yandexIdentity(), settings).toList()
        assertTrue(states2.last() is CanvasLoadState.Ready)
        assertEquals(1, downloads.get())
    }

    @Test
    fun rejectsPrivateMediaDataSaverAndClearCacheRace() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val cache = CanvasDiskCache(
            rootDir = tempFolder.root,
            downloader = CanvasDownloader { _, target, _ ->
                gate.await()
                target.writeBytes(ByteArray(256) { 2 })
                256L
            },
        )
        val repo = CanvasRepository(
            diskCache = cache,
            trackLookup = { trackWithVideo() },
            isDataSaverActive = { false },
        )
        val settings = CanvasSourceSettings(mode = BgCanvasMode.Card)

        assertEquals(
            CanvasLoadState.Unavailable(CanvasUnavailableReason.RestrictedPrivacy),
            repo.observeCanvas(yandexIdentity(secret = true), settings).toList().last(),
        )

        val job = launch { repo.observeCanvas(yandexIdentity(), settings).toList() }
        delay(20)
        repo.clearCacheOffMain()
        gate.complete(Unit)
        job.join()

        assertEquals(null, cache.getIfFresh("acc1|canvas|vid777"))

        val dataSaverRepo = CanvasRepository(
            diskCache = cache,
            trackLookup = { trackWithVideo() },
            isDataSaverActive = { true },
        )
        assertEquals(
            CanvasLoadState.Unavailable(CanvasUnavailableReason.DataSaver),
            dataSaverRepo.observeCanvas(yandexIdentity(), settings).toList().last(),
        )
    }

    @Test(expected = IOException::class)
    fun copyBoundedRejectsOversizedCanvas() {
        val target = tempFolder.newFile("oversized.mp4")
        CanvasDiskCache.copyBounded(ByteArrayInputStream(ByteArray(2048)), target, maxBytes = 1024L)
    }

    @Test
    fun httpsRedirectChainAllowsHttpsAndRejectsHttpDowngradeOrExcessRedirects() = runBlocking {
        val out = tempFolder.newFile("redirected.mp4")
        val bytes = ByteArray(128) { 7 }

        val written = CanvasDiskCache.downloadWithHttpsRedirects(
            initialUrl = "https://cdn.example/start",
            target = out,
            maxBytes = 1024L,
            openConnection = { url ->
                when (url.toString()) {
                    "https://cdn.example/start" -> fakeConnection(
                        url = url,
                        code = 302,
                        location = "/hop1.mp4",
                    )
                    "https://cdn.example/hop1.mp4" -> fakeConnection(
                        url = url,
                        code = 307,
                        location = "https://media.example/final.mp4",
                    )
                    "https://media.example/final.mp4" -> fakeConnection(
                        url = url,
                        code = 200,
                        body = bytes,
                    )
                    else -> error("Unexpected URL: $url")
                }
            },
        )
        assertEquals(128L, written)
        assertEquals(128L, out.length())

        val downgradeError = runCatching {
            CanvasDiskCache.downloadWithHttpsRedirects(
                initialUrl = "https://cdn.example/start",
                target = tempFolder.newFile("downgrade.mp4"),
                maxBytes = 1024L,
                openConnection = { url ->
                    fakeConnection(url = url, code = 302, location = "http://insecure.example/clip.mp4")
                },
            )
        }.exceptionOrNull()
        assertTrue(downgradeError is IOException)

        val hopCount = AtomicInteger(0)
        val tooManyError = runCatching {
            CanvasDiskCache.downloadWithHttpsRedirects(
                initialUrl = "https://cdn.example/loop0",
                target = tempFolder.newFile("loop.mp4"),
                maxBytes = 1024L,
                openConnection = { url ->
                    val n = hopCount.incrementAndGet()
                    fakeConnection(url = url, code = 302, location = "https://cdn.example/loop$n")
                },
            )
        }.exceptionOrNull()
        assertTrue(tooManyError is IOException)
    }

    @Test
    fun concurrentDownloadsForSameKeyDeduplicateSafelyWithoutMutexRace() = runBlocking {
        val downloads = AtomicInteger(0)
        val cache = CanvasDiskCache(
            rootDir = tempFolder.root,
            downloader = CanvasDownloader { _, target, _ ->
                downloads.incrementAndGet()
                delay(25)
                target.writeBytes(ByteArray(256) { 3 })
                256L
            },
        )

        val results = (1..8).map {
            async { cache.getOrDownload("acc1|canvas|race", "https://cdn.example/race.mp4") }
        }.awaitAll()

        assertEquals(1, downloads.get())
        assertTrue(results.all { it != null && it.length() == 256L })
    }

    @Test
    fun separatesTtlFromLruAccessOrder() = runBlocking {
        val clock = AtomicLong(1_000L)
        val cache = CanvasDiskCache(
            rootDir = tempFolder.root,
            maxTotalBytes = 250L,
            maxFileBytes = 200L,
            positiveTtlMs = 5_000L,
            now = { clock.get() },
            downloader = CanvasDownloader { _, target, _ ->
                target.writeBytes(ByteArray(100) { 4 })
                100L
            },
        )

        // Download A at t=1000, B at t=2000
        assertNotNull(cache.getOrDownload("keyA", "https://cdn.example/a.mp4"))
        clock.set(2_000L)
        assertNotNull(cache.getOrDownload("keyB", "https://cdn.example/b.mp4"))

        // Access A at t=3000 -> A is now MRU, B is LRU
        clock.set(3_000L)
        assertNotNull(cache.getIfFresh("keyA"))

        // Download C at t=4000 -> total=300 > 250 -> should evict B (LRU), keep A
        clock.set(4_000L)
        assertNotNull(cache.getOrDownload("keyC", "https://cdn.example/c.mp4"))
        assertNotNull(cache.getIfFresh("keyA"))
        assertNull(cache.getIfFresh("keyB"))

        // Advance to t=6000 -> A was created at t=1000 (age=5000 >= TTL), even though accessed at t=4000!
        clock.set(6_000L)
        assertNull(cache.getIfFresh("keyA"))
        // C was created at t=4000 (age=2000 < TTL), so C is still fresh.
        assertNotNull(cache.getIfFresh("keyC"))
    }

    @Test
    fun negativeCacheIsCappedAt128Entries() = runBlocking {
        val cache = CanvasDiskCache(
            rootDir = tempFolder.root,
            downloader = CanvasDownloader { _, _, _ -> throw IOException("network fail") },
        )
        val repo = CanvasRepository(
            diskCache = cache,
            trackLookup = { key ->
                val id = key.removePrefix("ym:")
                trackWithVideo().copy(trackId = id, backgroundVideoId = "vid_$id")
            },
        )
        val settings = CanvasSourceSettings(mode = BgCanvasMode.Card)

        for (i in 1..160) {
            val identity = TrackIdentity.fromPlaybackItem(
                PlaybackItem(
                    key = "ym:$i",
                    kind = MediaKind.Audio,
                    path = "",
                    title = "Track $i",
                    durationSeconds = 100,
                    accountId = "acc1",
                )
            )
            repo.observeCanvas(identity, settings).toList()
        }

        assertEquals(CanvasRepository.MAX_NEGATIVE_ENTRIES, repo.negativeCacheSize())
    }

    @Test
    fun restoresMetadataAfterRestartAndReportsRedactedDiagnostics() = runBlocking {
        val cache = CanvasDiskCache(
            rootDir = tempFolder.root,
            downloader = CanvasDownloader { _, target, _ ->
                target.writeBytes(ByteArray(512) { 5 })
                512L
            },
        )
        val repo = CanvasRepository(
            diskCache = cache,
            trackLookup = { null },
            trackFetcher = { trackId ->
                assertEquals("777", trackId)
                trackWithVideo("https://secret-cdn.example/clip.mp4?token=abc123")
            },
        )
        val settings = CanvasSourceSettings(mode = BgCanvasMode.Card)
        val states = repo.observeCanvas(yandexIdentity(), settings).toList()

        assertTrue(states.any { it is CanvasLoadState.Loading })
        assertTrue(states.last() is CanvasLoadState.Ready)

        repo.recordFirstFrame()
        repo.recordPlaybackError("ERROR_CODE_DECODER_INIT_FAILED")

        val diag = repo.diagnosticsSummary()
        assertNotNull(diag)
        assertTrue(diag!!.contains("loads=1"))
        assertTrue(diag.contains("dlOk=1"))
        assertTrue(diag.contains("restored=1"))
        assertTrue(diag.contains("firstFrame=1"))
        assertTrue(diag.contains("playErr=1"))
        assertTrue(diag.contains("lastErr=ERROR_CODE_DECODER_INIT_FAILED"))
        assertFalse(diag.contains("http"))
        assertFalse(diag.contains("token"))
        assertFalse(diag.contains("abc123"))
        assertFalse(diag.contains(tempFolder.root.absolutePath))
    }

    private fun fakeConnection(
        url: URL,
        code: Int,
        location: String? = null,
        body: ByteArray = ByteArray(0),
    ): HttpURLConnection = object : HttpURLConnection(url) {
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun connect() {}
        override fun getResponseCode(): Int = code
        override fun getHeaderField(name: String?): String? =
            if (name.equals("Location", ignoreCase = true)) location else null
        override fun getContentLengthLong(): Long = body.size.toLong()
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
    }
}
