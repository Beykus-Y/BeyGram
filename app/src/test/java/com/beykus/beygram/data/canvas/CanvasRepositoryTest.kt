package com.beykus.beygram.data.canvas

import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.music.TrackIdentity
import com.beykus.beygram.playback.PlaybackItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

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
}
