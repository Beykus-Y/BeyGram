package com.beykus.beygram.data.lyrics

import com.beykus.beygram.domain.lyrics.LyricWord
import com.beykus.beygram.domain.lyrics.LyricsAttribution
import com.beykus.beygram.domain.lyrics.LyricsDocument
import com.beykus.beygram.domain.lyrics.LyricsFetchResult
import com.beykus.beygram.domain.lyrics.LyricsLoadState
import com.beykus.beygram.domain.lyrics.LyricsSource
import com.beykus.beygram.domain.lyrics.LyricsSourceId
import com.beykus.beygram.domain.lyrics.LyricsSyncMode
import com.beykus.beygram.domain.lyrics.LyricsUnavailableReasonV2
import com.beykus.beygram.domain.lyrics.RichLyricLine
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.music.TrackIdentity
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

class LyricsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun publicTelegramTrack(accountId: String = "acc1", protectedChat: Boolean? = false) =
        TrackIdentity.fromPlaybackItem(
            PlaybackItem(
                key = "tg|$accountId|chat:1:message:2|Audio|3",
                kind = MediaKind.Audio,
                path = "",
                title = "M83 — Midnight City",
                trackTitle = "Midnight City",
                performer = "M83",
                durationSeconds = 243,
                origin = PlaybackOrigin(1L, 2L, accountId),
                accountId = accountId,
            ),
            protectedChat = protectedChat,
        )

    private fun doc(id: LyricsSourceId, mode: LyricsSyncMode, text: String, external: Boolean): LyricsDocument {
        val line = RichLyricLine(
            startMs = if (mode == LyricsSyncMode.Plain) null else 1_000L,
            text = text,
            words = if (mode == LyricsSyncMode.WordSynced) {
                listOfNotNull(LyricWord.validated(text, 1_000L, 1_500L, 0, text.length))
            } else emptyList(),
        )
        return LyricsDocument(
            syncMode = mode,
            lines = listOf(line),
            plainText = if (mode == LyricsSyncMode.Plain) text else null,
            attribution = LyricsAttribution(id, id.name, isExternalLookup = external),
        )
    }

    @Test
    fun emitsEarlyPlainResultAndUpgradesToWordSynced() = runBlocking {
        val fastPlain = object : LyricsSource {
            override val id = LyricsSourceId.EmbeddedTags
            override val isExternalNetwork = false
            override fun supports(identity: TrackIdentity) = true
            override suspend fun fetch(identity: TrackIdentity): LyricsFetchResult {
                delay(10)
                return LyricsFetchResult.Found(doc(id, LyricsSyncMode.Plain, "Plain", false), 0.5f)
            }
        }
        val slowWord = object : LyricsSource {
            override val id = LyricsSourceId.LrcLib
            override val isExternalNetwork = true
            override fun supports(identity: TrackIdentity) = true
            override suspend fun fetch(identity: TrackIdentity): LyricsFetchResult {
                delay(60)
                return LyricsFetchResult.Found(doc(id, LyricsSyncMode.WordSynced, "Synced", true), 0.9f)
            }
        }

        val repo = LyricsRepository(listOf(fastPlain, slowWord), diskCache = LyricsDiskCache(tempFolder.root))
        val states = repo.observeLyrics(publicTelegramTrack(), LyricsSourceSettings(externalLookupEnabled = true)).toList()

        assertTrue(states.first() is LyricsLoadState.Loading)
        val readyStates = states.filterIsInstance<LyricsLoadState.Ready>()
        assertEquals(2, readyStates.size)
        assertEquals(LyricsSyncMode.Plain, readyStates[0].document.syncMode)
        assertTrue(readyStates[0].isUpgrading)
        assertEquals(LyricsSyncMode.WordSynced, readyStates[1].document.syncMode)
        assertFalse(readyStates[1].isUpgrading)
    }

    @Test
    fun cachesHitsToDiskRespectsTtlNegativeCacheAndAccountIsolation() = runBlocking {
        var clock = 1_000_000L
        val calls = AtomicInteger(0)
        val source = object : LyricsSource {
            override val id = LyricsSourceId.LrcLib
            override val isExternalNetwork = true
            override fun supports(identity: TrackIdentity) = true
            override suspend fun fetch(identity: TrackIdentity): LyricsFetchResult {
                val n = calls.incrementAndGet()
                return if (n == 1) LyricsFetchResult.NotFound
                else LyricsFetchResult.Found(doc(id, LyricsSyncMode.LineSynced, "Cached ${identity.accountId}", true), 0.9f)
            }
        }
        val disk = LyricsDiskCache(tempFolder.root)
        val repo = LyricsRepository(listOf(source), diskCache = disk, now = { clock })
        val settings = LyricsSourceSettings(externalLookupEnabled = true)
        val trackAcc1 = publicTelegramTrack(accountId = "acc1")

        // First call -> NotFound (negative cached)
        val first = repo.observeLyrics(trackAcc1, settings).toList().last()
        assertEquals(LyricsLoadState.Unavailable(LyricsUnavailableReasonV2.NoText), first)
        assertEquals(1, calls.get())

        // Second call within negative TTL -> served from negative cache without hitting source
        val second = repo.observeLyrics(trackAcc1, settings).toList().last()
        assertEquals(LyricsLoadState.Unavailable(LyricsUnavailableReasonV2.NoText), second)
        assertEquals(1, calls.get())

        // Force refresh invalidates negative cache and fetches fresh lyrics
        val refreshed = repo.observeLyrics(trackAcc1, settings, forceRefresh = true).toList().last() as LyricsLoadState.Ready
        assertEquals("Cached acc1", refreshed.document.lines[0].text)
        assertEquals(2, calls.get())

        // Different account does not reuse acc1 cache
        val acc2Result = repo.observeLyrics(publicTelegramTrack(accountId = "acc2"), settings).toList().last() as LyricsLoadState.Ready
        assertEquals("Cached acc2", acc2Result.document.lines[0].text)
        assertEquals(3, calls.get())

        // New repo instance reads from disk cache without hitting source
        val repoFromDisk = LyricsRepository(listOf(source), diskCache = disk, now = { clock })
        val diskHit = repoFromDisk.observeLyrics(trackAcc1, settings).toList().last() as LyricsLoadState.Ready
        assertEquals("Cached acc1", diskHit.document.lines[0].text)
        assertEquals(3, calls.get())
    }

    @Test
    fun reportsExternalDisabledAndRestrictedPrivacyAppropriately() = runBlocking {
        val lrcLib = object : LyricsSource {
            override val id = LyricsSourceId.LrcLib
            override val isExternalNetwork = true
            override fun supports(identity: TrackIdentity) = true
            override suspend fun fetch(identity: TrackIdentity) = error("must not fetch")
        }
        val repo = LyricsRepository(listOf(lrcLib))

        val disabled = repo.observeLyrics(publicTelegramTrack(), LyricsSourceSettings(externalLookupEnabled = false)).toList().last()
        assertEquals(LyricsLoadState.Unavailable(LyricsUnavailableReasonV2.ExternalLookupDisabled), disabled)

        val unknownPrivacy = repo.observeLyrics(publicTelegramTrack(protectedChat = null), LyricsSourceSettings(externalLookupEnabled = true)).toList().last()
        assertEquals(LyricsLoadState.Unavailable(LyricsUnavailableReasonV2.RestrictedPrivacy), unknownPrivacy)
    }
}
