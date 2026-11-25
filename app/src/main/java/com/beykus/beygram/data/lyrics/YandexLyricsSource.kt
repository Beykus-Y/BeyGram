package com.beykus.beygram.data.lyrics

import com.beykus.beygram.data.music.provider.MusicProvider
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.domain.lyrics.LyricsFetchResult
import com.beykus.beygram.domain.lyrics.LyricsSource
import com.beykus.beygram.domain.lyrics.LyricsSourceId
import com.beykus.beygram.domain.lyrics.toLyricsDocument
import com.beykus.beygram.domain.music.TrackAudioSource
import com.beykus.beygram.domain.music.TrackIdentity

/**
 * Adapter from BeyGram's existing [MusicProvider.lyrics] (Yandex Music) to [LyricsSource].
 */
class YandexLyricsSource(
    private val provider: () -> MusicProvider?,
) : LyricsSource {

    override val id: LyricsSourceId = LyricsSourceId.YandexMusic
    override val isExternalNetwork: Boolean = false

    override fun supports(identity: TrackIdentity): Boolean =
        identity.audioSource == TrackAudioSource.YandexMusic && !identity.providerTrackId.isNullOrBlank()

    override suspend fun fetch(identity: TrackIdentity): LyricsFetchResult {
        val trackId = identity.providerTrackId ?: return LyricsFetchResult.NotFound
        val activeProvider = provider() ?: return LyricsFetchResult.AuthRequired
        return when (val result = activeProvider.lyrics(trackId)) {
            is ProviderResult.Success -> {
                val doc = result.value.toLyricsDocument()
                if (doc != null) LyricsFetchResult.Found(doc, matchScore = 1f)
                else LyricsFetchResult.NotFound
            }
            is ProviderResult.Failure -> when (result.failure) {
                MusicProviderFailure.NotFound -> LyricsFetchResult.NotFound
                MusicProviderFailure.NotSignedIn,
                MusicProviderFailure.SessionExpired -> LyricsFetchResult.AuthRequired
                MusicProviderFailure.RateLimited,
                MusicProviderFailure.Network,
                MusicProviderFailure.Unavailable -> LyricsFetchResult.Unavailable(retryable = true)
                MusicProviderFailure.SubscriptionRequired,
                MusicProviderFailure.ServiceChanged,
                MusicProviderFailure.Unknown -> LyricsFetchResult.Unavailable(retryable = false)
            }
        }
    }
}
