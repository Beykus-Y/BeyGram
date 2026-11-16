package com.beykus.beygram.data.music

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicTempoTest {
    private val clock = FakeClock()
    private val persistence = FakeMusicPersistence()
    private val repo = repositoryFor(persistence, clock)

    private fun MusicLibraryRepository.storedPlaylist(id: String): MusicPlaylist = library.value.playlist(id)!!

    /** Stats of [track] that hold [bpm], as the library keeps them after a trusted analysis. */
    private fun statsWith(track: PlaylistTrack, bpm: Double?): MusicListenStats =
        MusicListenStats(track = track.toTrackRef(), bpm = bpm)

    private fun titles(tracks: List<PlaylistTrack>): List<String> = tracks.map { it.title }

    @Test
    fun tempoSortPutsTheSlowestFirstAndTracksWithoutATempoLast() {
        val slow = testTrack(message = 1, title = "Slow")
        val unknownA = testTrack(message = 2, title = "Unknown A")
        val fast = testTrack(message = 3, title = "Fast")
        val unknownB = testTrack(message = 4, title = "Unknown B")
        val middle = testTrack(message = 5, title = "Middle")
        val tempos = mapOf(slow.trackKey to 90.0, fast.trackKey to 140.0, middle.trackKey to 120.0)
        val sorted = sortedByTempo(listOf(fast, unknownA, slow, unknownB, middle)) { tempos[it.trackKey] }
        assertEquals(listOf("Slow", "Middle", "Fast", "Unknown A", "Unknown B"), titles(sorted))
    }

    @Test
    fun equalTemposAndTracksWithoutATempoKeepTheirRelativeOrder() {
        val a = testTrack(message = 1, title = "A")
        val b = testTrack(message = 2, title = "B")
        val c = testTrack(message = 3, title = "C")
        val d = testTrack(message = 4, title = "D")
        val tempos = mapOf(b.trackKey to 128.0, d.trackKey to 128.0)
        // B and D share a tempo: D stays first because it comes first in the list. A and C have none, in list order.
        assertEquals(listOf("D", "B", "C", "A"), titles(sortedByTempo(listOf(d, c, b, a)) { tempos[it.trackKey] }))
        assertEquals(listOf("B", "D", "A", "C"), titles(sortedByTempo(listOf(b, a, d, c)) { tempos[it.trackKey] }))
    }

    @Test
    fun aPlaylistWithoutAnyKnownTempoKeepsItsOrder() {
        val tracks = listOf(testTrack(message = 3, title = "C"), testTrack(message = 1, title = "A"), testTrack(message = 2, title = "B"))
        assertEquals(tracks, sortedByTempo(tracks) { null })
    }

    @Test
    fun theTempoSortUsesTheTemposStoredInTheStats() {
        val slow = testTrack(message = 1, title = "Slow")
        val fast = testTrack(message = 2, title = "Fast")
        val untimed = testTrack(message = 3, title = "Untimed")
        val stats = mapOf(
            slow.trackKey to statsWith(slow, 96.0),
            fast.trackKey to statsWith(fast, 174.0),
            untimed.trackKey to statsWith(untimed, null)
        )
        assertEquals(listOf("Slow", "Fast", "Untimed"), titles(sortedTracks(listOf(fast, untimed, slow), MusicSortMode.Tempo, stats)))
    }

    @Test
    fun theTempoSortIsOfferedOnlyWhenTwoTracksOfThePlaylistHaveAKnownTempo() {
        val one = testTrack(message = 1)
        val two = testTrack(message = 2)
        val three = testTrack(message = 3)
        val outside = testTrack(message = 9)
        val playlist = MusicPlaylist(id = "p", title = "P", tracks = listOf(one, two, three))
        assertFalse(tempoSortOffered(playlist, emptyMap()))
        assertFalse(tempoSortOffered(playlist, mapOf(one.trackKey to statsWith(one, 120.0))))
        assertTrue(tempoSortOffered(playlist, mapOf(one.trackKey to statsWith(one, 120.0), two.trackKey to statsWith(two, 130.0))))
        // A tempo of a track that is not in this playlist does not count.
        assertFalse(
            tempoSortOffered(playlist, mapOf(one.trackKey to statsWith(one, 120.0), outside.trackKey to statsWith(outside, 130.0)))
        )
        // An entry without a tempo does not count either.
        assertFalse(tempoSortOffered(playlist, mapOf(one.trackKey to statsWith(one, 120.0), two.trackKey to statsWith(two, null))))
    }

    @Test
    fun aStoredTempoSortsThePlaylistAndSurvivesALaterPlay() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val id = repo.createPlaylist("Workout").doneValue().id
        // The playlist starts in an order that the tempo sort has to change.
        repo.addTracks(
            id,
            listOf(
                testTrack(message = 2, title = "Fast"),
                testTrack(message = 3, title = "Untimed"),
                testTrack(message = 1, title = "Slow")
            )
        ).doneValue()
        repo.recordTempo(testTrack(message = 2).toTrackRef(), 150.0).doneValue()
        repo.recordTempo(testTrack(message = 1).toTrackRef(), 96.0).doneValue()
        repo.recordPlay(testTrack(message = 2).toTrackRef(), 500L).doneValue()

        val fastStats = repo.library.value.stats.getValue(testTrack(message = 2).trackKey)
        assertEquals(150.0, fastStats.bpm!!, 0.0)
        assertEquals(1, fastStats.playCount)

        repo.sortPlaylist(id, MusicSortMode.Tempo).doneValue()
        assertEquals(MusicSortMode.Tempo, repo.storedPlaylist(id).sortMode)
        assertEquals(listOf("Slow", "Fast", "Untimed"), repo.storedPlaylist(id).tracks.map { it.title })
    }

    @Test
    fun aTempoOfAnotherAccountIsRefusedAndNothingIsWritten() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val saves = persistence.saves
        val foreign = testTrack(message = 7, account = "someone-else").toTrackRef()
        assertEquals(MusicFailure.ForeignAccount, repo.recordTempo(foreign, 120.0).failureOf())
        assertNull(repo.library.value.stats[foreign.trackKey])
        assertEquals(saves, persistence.saves)
    }

    @Test
    fun aTrackWithoutATelegramOriginCannotKeepATempo() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val local = PlaylistTrack(
            itemId = "local",
            title = "Local",
            performer = "",
            album = "",
            durationSeconds = 0,
            origin = null,
            addedAt = 0L,
            audioHash = "abc"
        ).toTrackRef()
        assertEquals(MusicFailure.InvalidTrack, repo.recordTempo(local, 120.0).failureOf())
        assertTrue(repo.library.value.stats.isEmpty())
    }

    @Test
    fun anUnchangedOrInvalidTempoWritesNothing() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val ref = testTrack(message = 1).toTrackRef()
        repo.recordTempo(ref, 120.0).doneValue()
        val saves = persistence.saves
        repo.recordTempo(ref, 120.0).doneValue()
        repo.recordTempo(ref, 0.0).doneValue()
        repo.recordTempo(ref, Double.NaN).doneValue()
        repo.recordTempo(ref, 1_000.0).doneValue()
        assertEquals(saves, persistence.saves)
        assertEquals(120.0, repo.library.value.stats.getValue(ref.trackKey).bpm!!, 0.0)
    }

    @Test
    fun aTempoOnlyEntryIsNotOfferedAsAReplacement() {
        val missing = testTrack(message = 1, title = "Song", durationSeconds = 200).toTrackRef()
        val tempoOnly = testTrack(message = 2, title = "Song", durationSeconds = 200)
        val played = testTrack(message = 3, title = "Song", durationSeconds = 200)
        val library = MusicLibrary(
            accountId = TEST_ACCOUNT,
            playlists = listOf(favoritesPlaylist(1L)),
            stats = mapOf(
                tempoOnly.trackKey to MusicListenStats(track = tempoOnly.toTrackRef(), bpm = 120.0),
                played.trackKey to MusicListenStats(track = played.toTrackRef(), playCount = 1, lastPlayedAt = 5L)
            )
        )
        assertEquals(listOf(played.trackKey), replacementCandidates(library, missing).map { it.trackKey })
    }
}
