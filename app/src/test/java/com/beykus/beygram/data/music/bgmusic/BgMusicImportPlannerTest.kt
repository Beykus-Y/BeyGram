package com.beykus.beygram.data.music.bgmusic

import com.beykus.beygram.data.music.MUSIC_FAVORITES_PLAYLIST_ID
import com.beykus.beygram.data.music.MusicPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BgMusicImportPlannerTest {

    private val source = "6f1d2c3a-4b5e-4f60-8a7b-9c0d1e2f3a4b"

    private fun local(id: String, revision: Long, sourceId: String? = null) =
        MusicPlaylist(id = id, title = "Local", revision = revision, sourcePlaylistId = sourceId)

    @Test
    fun unknownFileIsANewPlaylist() {
        val library = listOf(local("local-1", 1L))

        assertEquals(BgMusicImportDecision.NewPlaylist, BgMusicImportPlanner.decide(source, 1L, library))
        assertNull(BgMusicImportPlanner.matchingCopy(source, library))
    }

    @Test
    fun favoritesNeverMatchAFile() {
        val favorites = MusicPlaylist(id = MUSIC_FAVORITES_PLAYLIST_ID, title = "Любимые", revision = 9L)

        assertEquals(
            BgMusicImportDecision.NewPlaylist,
            BgMusicImportPlanner.decide(MUSIC_FAVORITES_PLAYLIST_ID, 9L, listOf(favorites)),
        )
        assertNull(BgMusicImportPlanner.matchingCopy(MUSIC_FAVORITES_PLAYLIST_ID, listOf(favorites)))
    }

    @Test
    fun importedCopyMatchesTheFileItCameFrom() {
        val copy = local("local-copy", 3L, sourceId = source)

        assertEquals(BgMusicImportDecision.SameRevision(3L), BgMusicImportPlanner.decide(source, 3L, listOf(copy)))
        assertEquals(copy, BgMusicImportPlanner.matchingCopy(source, listOf(copy)))
    }

    @Test
    fun newerFileOffersAnUpdateOfTheHighestRevisionCopy() {
        val older = local("copy-a", 2L, sourceId = source)
        val newest = local("copy-b", 5L, sourceId = source)

        assertEquals(
            BgMusicImportDecision.NewerRevision(5L),
            BgMusicImportPlanner.decide(source, 6L, listOf(older, newest)),
        )
        assertEquals("copy-b", BgMusicImportPlanner.matchingCopy(source, listOf(older, newest))?.id)
    }

    @Test
    fun olderFileIsReportedAsOlder() {
        assertEquals(
            BgMusicImportDecision.OlderRevision(5L),
            BgMusicImportPlanner.decide(source, 4L, listOf(local("copy", 5L, sourceId = source))),
        )
    }

    @Test
    fun idsMatchIgnoringCaseAndSurroundingSpaces() {
        assertEquals(
            BgMusicImportDecision.SameRevision(5L),
            BgMusicImportPlanner.decide(" ${source.uppercase()} ", 5L, listOf(local("copy", 5L, sourceId = source))),
        )
    }

    @Test
    fun editsMadeInAnImportedCopyDoNotChangeWhatItMatches() {
        val copy = MusicPlaylist(
            id = "local-copy",
            title = "Local",
            revision = 12L,
            sourcePlaylistId = source,
            sourceRevision = 3L,
        )

        assertEquals(BgMusicImportDecision.SameRevision(3L), BgMusicImportPlanner.decide(source, 3L, listOf(copy)))
        assertEquals(BgMusicImportDecision.NewerRevision(3L), BgMusicImportPlanner.decide(source, 4L, listOf(copy)))
        assertEquals(BgMusicImportDecision.OlderRevision(3L), BgMusicImportPlanner.decide(source, 2L, listOf(copy)))
    }

    @Test
    fun ownPlaylistIsComparedByItsOwnRevision() {
        assertEquals(
            BgMusicImportDecision.NewerRevision(12L),
            BgMusicImportPlanner.decide("own-playlist", 13L, listOf(local("own-playlist", 12L))),
        )
    }

    @Test
    fun originalPlaylistMatchesItsOwnId() {
        assertEquals(
            BgMusicImportDecision.NewerRevision(2L),
            BgMusicImportPlanner.decide(source, 3L, listOf(local(source, 2L))),
        )
    }
}
