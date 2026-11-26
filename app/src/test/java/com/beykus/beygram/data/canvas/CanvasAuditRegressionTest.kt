package com.beykus.beygram.data.canvas

import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.music.TrackIdentity
import com.beykus.beygram.playback.PlaybackItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CanvasAuditRegressionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun yandexIdentity(trackId: String = "101") = TrackIdentity.fromPlaybackItem(
        PlaybackItem(
            key = "ym:$trackId",
            kind = MediaKind.Audio,
            path = "",
            title = "Song",
            performer = "Artist",
            durationSeconds = 180,
            accountId = "acc-1",
            secret = false,
        )
    )

    @Test
    fun meteredNetworkSkipsDownloadAndPrefetchWhenRespectDataSaverIsOn() = runBlocking {
        var fetchCalls = 0
        val repo = CanvasRepository(
            diskCache = CanvasDiskCache(tempFolder.newFolder("canvas-cache")),
            trackLookup = { null },
            trackFetcher = {
                fetchCalls++
                ProviderTrack(
                    trackId = "101",
                    title = "Song",
                    artists = listOf("Artist"),
                    albumTitle = "Album",
                    durationMs = 180_000L,
                    coverUrl = null,
                    isAvailable = true,
                    backgroundVideoUri = "https://example.com/canvas.mp4",
                    backgroundVideoId = "v1",
                )
            },
            isDataSaverActive = { false },
            isMeteredNetwork = { true },
        )
        val settings = CanvasSourceSettings(mode = BgCanvasMode.Card, respectDataSaver = true)
        val states = repo.observeCanvas(yandexIdentity(), settings).toList()
        repo.prefetch(yandexIdentity(), settings)

        assertEquals(0, fetchCalls)
        assertEquals(listOf(CanvasLoadState.Unavailable(CanvasUnavailableReason.DataSaver)), states)
    }

    @Test
    fun cancellationExceptionFromTrackFetcherIsRethrownNotSwallowed() = runBlocking {
        val repo = CanvasRepository(
            diskCache = CanvasDiskCache(tempFolder.newFolder("canvas-cancel")),
            trackLookup = { null },
            trackFetcher = { throw CancellationException("caller cancelled") },
        )
        val settings = CanvasSourceSettings(mode = BgCanvasMode.Card, respectDataSaver = false)
        var rethrown = false
        try {
            repo.observeCanvas(yandexIdentity(), settings).toList()
        } catch (_: CancellationException) {
            rethrown = true
        }
        assertTrue(rethrown)
    }
}
