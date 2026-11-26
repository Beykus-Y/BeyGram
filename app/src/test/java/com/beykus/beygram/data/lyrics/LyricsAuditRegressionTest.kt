package com.beykus.beygram.data.lyrics

import com.beykus.beygram.domain.lyrics.LyricsAttribution
import com.beykus.beygram.domain.lyrics.LyricsDocument
import com.beykus.beygram.domain.lyrics.LyricsFetchResult
import com.beykus.beygram.domain.lyrics.LyricsLoadState
import com.beykus.beygram.domain.lyrics.LyricsSource
import com.beykus.beygram.domain.lyrics.LyricsSourceId
import com.beykus.beygram.domain.lyrics.LyricsSyncMode
import com.beykus.beygram.domain.lyrics.RichLyricLine
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.music.TrackIdentity
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsAuditRegressionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun publicTelegramTrack(accountId: String, key: String) = TrackIdentity.fromPlaybackItem(
        PlaybackItem(
            key = key,
            kind = MediaKind.Audio,
            path = "",
            title = "Artist — Track",
            trackTitle = "Track",
            performer = "Artist",
            durationSeconds = 180,
            origin = PlaybackOrigin(1L, 2L, accountId),
            accountId = accountId,
        ),
        protectedChat = false,
    )

    @Test
    fun retryable429CreatesBoundedBackoffNegativeCache() = runBlocking {
        var nowMs = 1_000L
        var calls = 0
        val source = LrcLibLyricsSource(
            http = LrcLibHttpClient { _, _ ->
                calls++
                LrcLibHttpResponse(code = 429, body = "", retryAfterSeconds = 45L)
            },
            isExternalLookupEnabled = { true },
        )
        val repo = LyricsRepository(
            sources = listOf(source),
            diskCache = null,
            now = { nowMs },
        )
        val settings = LyricsSourceSettings(
            externalLookupEnabled = true,
            embeddedTagsEnabled = false,
            yandexEnabled = false,
            lrcLibEnabled = true,
        )
        val id = publicTelegramTrack("acc-1", "tg:1")
        val first = repo.observeLyrics(id, settings).toList()
        assertTrue(first.last() is LyricsLoadState.Unavailable)
        assertEquals(1, calls)

        // Within 45s Retry-After window: served from negative backoff cache without hitting LRCLIB again.
        nowMs += 30_000L
        val second = repo.observeLyrics(id, settings).toList()
        assertTrue(second.last() is LyricsLoadState.Unavailable)
        assertEquals(1, calls)

        // Past 45s Retry-After window: allowed to retry.
        nowMs += 20_000L
        repo.observeLyrics(id, settings).toList()
        assertEquals(2, calls)
    }

    @Test
    fun clearAccountCacheOffMainClearsOnlyTargetAccountEntries() = runBlocking {
        val disk = LyricsDiskCache(tempFolder.newFolder("lyrics-v1"))
        val doc = LyricsDocument(
            syncMode = LyricsSyncMode.Plain,
            lines = listOf(RichLyricLine(startMs = 0L, text = "Hello")),
            plainText = "Hello",
            attribution = LyricsAttribution(LyricsSourceId.LrcLib, "LRCLIB"),
        )
        val id1 = publicTelegramTrack("acc-1", "tg:10")
        val id2 = publicTelegramTrack("acc-2", "tg:20")
        var calls = 0
        val source = object : LyricsSource {
            override val id = LyricsSourceId.LrcLib
            override val isExternalNetwork = false
            override fun supports(identity: TrackIdentity) = true
            override suspend fun fetch(identity: TrackIdentity): LyricsFetchResult {
                calls++
                return LyricsFetchResult.Found(doc, 1f)
            }
        }
        val repo = LyricsRepository(sources = listOf(source), diskCache = disk, now = { 10_000L })
        val settings = LyricsSourceSettings(externalLookupEnabled = true)

        repo.observeLyrics(id1, settings).toList()
        repo.observeLyrics(id2, settings).toList()
        assertEquals(2, calls)

        repo.clearAccountCacheOffMain("acc-1")
        assertNull(disk.get("acc-1|tg:10|v${LyricsDocument.CURRENT_FORMAT_VERSION}", 10_000L))
        assertNotNull(disk.get("acc-2|tg:20|v${LyricsDocument.CURRENT_FORMAT_VERSION}", 10_000L))

        // acc-2 stays cached, acc-1 must re-fetch.
        repo.observeLyrics(id2, settings).toList()
        assertEquals(2, calls)
        repo.observeLyrics(id1, settings).toList()
        assertEquals(3, calls)
    }
}
