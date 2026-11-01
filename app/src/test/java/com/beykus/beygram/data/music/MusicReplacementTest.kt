package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicReplacementTest {
    private fun ref(
        message: Long,
        title: String = "Song",
        performer: String = "Band",
        duration: Int = 200,
        hash: String? = null,
        account: String = TEST_ACCOUNT
    ): MusicTrackRef {
        val origin = PlaybackOrigin(chatId = 1L, messageId = message, accountId = account)
        return MusicTrackRef(
            trackKey = musicTrackKey(origin, null, "item-$message"),
            title = title,
            performer = performer,
            durationSeconds = duration,
            origin = origin,
            audioHash = hash
        )
    }

    private fun entry(itemId: String, track: MusicTrackRef, addedAt: Long) = PlaylistTrack(
        itemId = itemId,
        title = track.title,
        performer = track.performer,
        album = "",
        durationSeconds = track.durationSeconds,
        origin = track.origin,
        addedAt = addedAt,
        audioHash = track.audioHash
    )

    private fun playlist(id: String, vararg tracks: PlaylistTrack) = MusicPlaylist(id = id, title = id, tracks = tracks.toList())

    private fun stat(track: MusicTrackRef, lastPlayedAt: Long?) =
        MusicListenStats(track = track, playCount = 1, lastPlayedAt = lastPlayedAt)

    private fun library(playlists: List<MusicPlaylist> = emptyList(), stats: List<MusicListenStats> = emptyList()) =
        MusicLibrary(
            accountId = TEST_ACCOUNT,
            playlists = listOf(favoritesPlaylist(0L)) + playlists,
            stats = stats.associateBy { it.trackKey }
        )

    @Test
    fun sameTitleAndPerformerWithinThreeSecondsMatch() {
        assertTrue(isMusicReplacement(ref(1, duration = 200), ref(2, duration = 203)))
        assertTrue(isMusicReplacement(ref(1, duration = 200), ref(2, duration = 197)))
    }

    @Test
    fun aLengthGapOfFourSecondsDoesNotMatchWithoutTheSameFile() {
        assertFalse(isMusicReplacement(ref(1, duration = 200), ref(2, duration = 204)))
    }

    @Test
    fun theSameAudioHashMatchesEvenWhenTheLengthDiffers() {
        assertTrue(isMusicReplacement(ref(1, duration = 200, hash = "h1"), ref(2, duration = 260, hash = "h1")))
    }

    @Test
    fun differentAudioHashesDoNotMatchByThemselves() {
        assertFalse(isMusicReplacement(ref(1, duration = 200, hash = "h1"), ref(2, duration = 260, hash = "h2")))
    }

    @Test
    fun unknownLengthsNeverMatchByLength() {
        assertFalse(isMusicReplacement(ref(1, duration = 0), ref(2, duration = 0)))
        assertFalse(isMusicReplacement(ref(1, duration = 0), ref(2, duration = 2)))
    }

    @Test
    fun titlesAndPerformersCompareAfterNormalization() {
        val missing = ref(1, title = "Song (Live)!", performer = "Ёлка")
        assertTrue(isMusicReplacement(missing, ref(2, title = "song  live", performer = "елка")))
        assertFalse(isMusicReplacement(ref(1, title = "Song"), ref(2, title = "Song 2")))
        assertFalse(isMusicReplacement(ref(1, performer = "A"), ref(2, performer = "B")))
    }

    @Test
    fun blankTitlesNeverMatch() {
        assertFalse(isMusicReplacement(ref(1, title = "  "), ref(2, title = "  ")))
    }

    @Test
    fun normalizationKeepsLettersAndDigitsOnly() {
        assertEquals("track 12 abc", normalizeMusicMatchText("  Track—12 (ABC)! "))
        assertEquals("", normalizeMusicMatchText(" ... "))
    }

    @Test
    fun candidatesComeFromPlaylistsNewestFirstThenStatsAndAppearOnce() {
        val missing = ref(1)
        val newer = ref(2)
        val older = ref(3)
        val listedAgain = ref(2)
        val fromStats = ref(4)
        val library = library(
            playlists = listOf(
                playlist("p1", entry("e1", missing, addedAt = 1), entry("e2", older, addedAt = 10), entry("e3", newer, addedAt = 20)),
                playlist("p2", entry("e4", listedAgain, addedAt = 5))
            ),
            stats = listOf(stat(fromStats, lastPlayedAt = 100L))
        )
        val found = replacementCandidates(library, missing).map { it.trackKey }
        assertEquals(listOf(newer.trackKey, older.trackKey, fromStats.trackKey), found)
    }

    @Test
    fun candidatesFromAnotherAccountOrWithoutOriginAreLeftOut() {
        val missing = ref(1)
        val foreign = ref(5, account = "other")
        val noOrigin = MusicTrackRef(trackKey = "hash:h", title = "Song", performer = "Band", durationSeconds = 200, origin = null)
        val library = library(
            playlists = listOf(playlist("p1", entry("e1", foreign, addedAt = 1))),
            stats = listOf(stat(noOrigin, lastPlayedAt = 5L))
        )
        assertTrue(replacementCandidates(library, missing).isEmpty())
    }

    @Test
    fun theMissingTrackNeverReplacesItself() {
        val missing = ref(1)
        val library = library(playlists = listOf(playlist("p1", entry("e1", missing, addedAt = 1))))
        assertTrue(replacementCandidates(library, missing).isEmpty())
    }

    @Test
    fun tracksWithAnotherTitleAreNotCandidates() {
        val missing = ref(1, title = "Song")
        val library = library(playlists = listOf(playlist("p1", entry("e1", ref(2, title = "Other"), addedAt = 1))))
        assertTrue(replacementCandidates(library, missing).isEmpty())
    }

    @Test
    fun candidatesAreCappedAtTheLimit() {
        val missing = ref(1)
        val tracks = (2L..(MUSIC_REPLACEMENT_MAX_CANDIDATES + 20).toLong()).map { entry("e$it", ref(it), addedAt = it) }
        val library = library(playlists = listOf(playlist("p1", *tracks.toTypedArray())))
        assertEquals(MUSIC_REPLACEMENT_MAX_CANDIDATES, replacementCandidates(library, missing).size)
    }

    @Test
    fun aCandidateAlreadyListedInThePlaylistIsLeftOut() {
        val missing = ref(1)
        val listedHere = ref(2)
        val elsewhere = ref(3)
        val target = playlist("p1", entry("m", missing, addedAt = 1), entry("e2", listedHere, addedAt = 2))
        val library = library(playlists = listOf(target, playlist("p2", entry("e3", elsewhere, addedAt = 5))))
        assertEquals(
            listOf(elsewhere.trackKey),
            replacementCandidatesFor(library, target, missingItemId = "m", missing = missing).map { it.trackKey }
        )
    }
}
