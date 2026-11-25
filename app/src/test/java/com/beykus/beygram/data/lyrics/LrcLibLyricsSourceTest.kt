package com.beykus.beygram.data.lyrics

import com.beykus.beygram.domain.lyrics.LyricsFetchResult
import com.beykus.beygram.domain.lyrics.LyricsSourceId
import com.beykus.beygram.domain.lyrics.LyricsSyncMode
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.music.TrackIdentity
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcLibLyricsSourceTest {

    private fun publicTelegramTrack() = TrackIdentity.fromPlaybackItem(
        PlaybackItem(
            key = "tg|acc1|chat:10:message:20|Audio|30",
            kind = MediaKind.Audio,
            path = "",
            title = "M83 — Midnight City",
            trackTitle = "Midnight City",
            performer = "M83",
            album = "Hurry Up",
            durationSeconds = 243,
            origin = PlaybackOrigin(10L, 20L, "acc1"),
        )
    )

    @Test
    fun fetchesSyncedLyricsFromGetEndpointAndSendsUserAgent() = runBlocking {
        var seenUrl = ""
        var seenUa = ""
        val source = LrcLibLyricsSource(
            http = LrcLibHttpClient { url, ua ->
                seenUrl = url
                seenUa = ua
                LrcLibHttpResponse(
                    200,
                    """{"trackName":"Midnight City","artistName":"M83","duration":243.0,"instrumental":false,"syncedLyrics":"[00:10.00]Waiting in a car","plainLyrics":"Waiting in a car"}""",
                )
            }
        )
        val result = source.fetch(publicTelegramTrack()) as LyricsFetchResult.Found
        assertEquals(LyricsSourceId.LrcLib, result.document.attribution.sourceId)
        assertEquals(LyricsSyncMode.LineSynced, result.document.syncMode)
        assertTrue(result.document.attribution.isExternalLookup)
        assertTrue(seenUrl.startsWith("https://lrclib.net/api/get?"))
        assertTrue(seenUa.contains("BeyGram"))
        assertFalse(seenUrl.contains("acc1"))
        assertFalse(seenUrl.contains("chat"))
    }

    @Test
    fun fallsBackToSearchOn404AndRejectsLiveRemix() = runBlocking {
        var calls = 0
        val source = LrcLibLyricsSource(
            http = LrcLibHttpClient { _, _ ->
                calls += 1
                if (calls == 1) {
                    LrcLibHttpResponse(404, "")
                } else {
                    LrcLibHttpResponse(
                        200,
                        """[
                          {"trackName":"Midnight City (Live)","artistName":"M83","duration":243.0,"syncedLyrics":"[00:01.00]Live"},
                          {"trackName":"Midnight City","artistName":"M83","duration":243.0,"plainLyrics":"Studio plain"}
                        ]""",
                    )
                }
            }
        )
        val result = source.fetch(publicTelegramTrack()) as LyricsFetchResult.Found
        assertEquals(2, calls)
        assertEquals(LyricsSyncMode.Plain, result.document.syncMode)
        assertEquals("Studio plain", result.document.plainText)
    }

    @Test
    fun blocksProtectedChats() = runBlocking {
        val protectedTrack = TrackIdentity.fromPlaybackItem(
            PlaybackItem(
                key = "tg|acc1|chat:10:message:20|Audio|30",
                kind = MediaKind.Audio,
                path = "",
                title = "Secret Song",
                durationSeconds = 180,
            ),
            protectedChat = true,
        )
        val source = LrcLibLyricsSource(http = LrcLibHttpClient { _, _ -> error("must not call network") })
        assertFalse(source.supports(protectedTrack))
        assertTrue(source.fetch(protectedTrack) is LyricsFetchResult.NotFound)
    }
}
