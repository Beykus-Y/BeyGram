package com.beykus.beygram.data.lyrics

import com.beykus.beygram.data.music.provider.LyricLine
import com.beykus.beygram.data.music.provider.MusicProvider
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.ProviderAlbum
import com.beykus.beygram.data.music.provider.ProviderAlbumTracks
import com.beykus.beygram.data.music.provider.ProviderAudio
import com.beykus.beygram.data.music.provider.ProviderLyrics
import com.beykus.beygram.data.music.provider.ProviderPlaylist
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.domain.lyrics.LyricsFetchResult
import com.beykus.beygram.domain.lyrics.LyricsSourceId
import com.beykus.beygram.domain.lyrics.LyricsSyncMode
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.music.TrackIdentity
import com.beykus.beygram.playback.PlaybackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexLyricsSourceTest {

    private class FakeProvider(
        private val response: ProviderResult<ProviderLyrics>,
    ) : MusicProvider {
        override val state: StateFlow<MusicProviderState> = MutableStateFlow(MusicProviderState.SignedOut)
        override suspend fun search(query: String, limit: Int): ProviderResult<List<ProviderTrack>> = ProviderResult.Failure(MusicProviderFailure.Unavailable)
        override suspend fun likedTracks(): ProviderResult<List<ProviderTrack>> = ProviderResult.Failure(MusicProviderFailure.Unavailable)
        override suspend fun setLiked(trackId: String, liked: Boolean): ProviderResult<Unit> = ProviderResult.Failure(MusicProviderFailure.Unavailable)
        override suspend fun playlists(): ProviderResult<List<ProviderPlaylist>> = ProviderResult.Failure(MusicProviderFailure.Unavailable)
        override suspend fun playlistTracks(kind: String, ownerUid: String?): ProviderResult<List<ProviderTrack>> = ProviderResult.Failure(MusicProviderFailure.Unavailable)
        override suspend fun lyrics(trackId: String): ProviderResult<ProviderLyrics> = response
        override suspend fun audio(trackId: String): ProviderResult<ProviderAudio> = ProviderResult.Failure(MusicProviderFailure.Unavailable)
    }

    private fun yandexIdentity() = TrackIdentity.fromPlaybackItem(
        PlaybackItem(key = "ym:4242", kind = MediaKind.Audio, path = "", title = "Track", durationSeconds = 120)
    )

    @Test
    fun adaptsSuccessAndFailuresProperly() = runBlocking {
        val okSource = YandexLyricsSource {
            FakeProvider(ProviderResult.Success(ProviderLyrics(listOf(LyricLine(1_000L, "Hello")), null, "Copyright")))
        }
        assertTrue(okSource.supports(yandexIdentity()))
        val ok = okSource.fetch(yandexIdentity()) as LyricsFetchResult.Found
        assertEquals(LyricsSourceId.YandexMusic, ok.document.attribution.sourceId)
        assertEquals(LyricsSyncMode.LineSynced, ok.document.syncMode)
        assertFalse(ok.document.attribution.isExternalLookup)

        val notFoundSource = YandexLyricsSource { FakeProvider(ProviderResult.Failure(MusicProviderFailure.NotFound)) }
        assertTrue(notFoundSource.fetch(yandexIdentity()) is LyricsFetchResult.NotFound)

        val authSource = YandexLyricsSource { FakeProvider(ProviderResult.Failure(MusicProviderFailure.SessionExpired)) }
        assertTrue(authSource.fetch(yandexIdentity()) is LyricsFetchResult.AuthRequired)
    }
}
