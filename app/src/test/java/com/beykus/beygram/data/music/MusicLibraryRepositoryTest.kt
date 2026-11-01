package com.beykus.beygram.data.music

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MusicLibraryRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val clock = FakeClock()
    private val persistence = FakeMusicPersistence()
    private val repo = repositoryFor(persistence, clock)

    private fun MusicLibraryRepository.playlist(id: String): MusicPlaylist = library.value.playlist(id)!!

    private fun titles(repository: MusicLibraryRepository, playlistId: String): List<String> =
        repository.playlist(playlistId).tracks.map { it.title }

    @Test
    fun createPlaylistStoresTrimmedTextAndTimestamps() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        clock.now = 5_000L
        val created = repo.createPlaylist("  Morning  ", "  calm songs  ").doneValue()
        assertEquals("Morning", created.title)
        assertEquals("calm songs", created.description)
        assertEquals(5_000L, created.createdAt)
        assertEquals(0L, created.revision)
        assertEquals(2, repo.library.value.playlists.size)
        assertTrue(repo.library.value.playlists.first().isFavorites)
    }

    @Test
    fun blankTitleIsRefusedAndNothingIsWritten() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val savesBefore = persistence.saves
        assertEquals(MusicFailure.InvalidTitle, repo.createPlaylist("   ").failureOf())
        assertEquals(savesBefore, persistence.saves)
        assertEquals(1, repo.library.value.playlists.size)
    }

    @Test
    fun longTitlesAreCutToTheLimitWithoutSplittingEmoji() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val title = repo.createPlaylist("🎵".repeat(200)).doneValue().title
        assertEquals("🎵".repeat(MUSIC_MAX_TITLE_CHARS), title)
    }

    @Test
    fun longDescriptionIsCutToItsLimit() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val id = repo.createPlaylist("Notes").doneValue().id
        repo.updateDescription(id, "я".repeat(MUSIC_MAX_DESCRIPTION_CHARS + 50)).doneValue()
        assertEquals("я".repeat(MUSIC_MAX_DESCRIPTION_CHARS), repo.playlist(id).description)
    }

    @Test
    fun playlistCountIncludesFavoritesAndStopsAtTheLimit() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        repeat(MUSIC_MAX_PLAYLISTS - 1) { index -> repo.createPlaylist("List $index").doneValue() }
        assertEquals(MUSIC_MAX_PLAYLISTS, repo.library.value.playlists.size)
        assertEquals(MusicFailure.LimitReached, repo.createPlaylist("one too many").failureOf())
    }

    @Test
    fun addTracksSkipsRepeatedOriginsInsideOneBatchAndAcrossBatches() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val id = repo.createPlaylist("Set").doneValue().id
        val first = repo.addTracks(
            id,
            listOf(testTrack(message = 10), testTrack(message = 10), testTrack(message = 11))
        ).doneValue()
        assertEquals(AddTracksResult(added = 2, duplicates = 1, rejected = 0), first)
        val second = repo.addTracks(id, listOf(testTrack(message = 11), testTrack(message = 12))).doneValue()
        assertEquals(AddTracksResult(added = 1, duplicates = 1, rejected = 0), second)
        assertEquals(listOf(10L, 11L, 12L), repo.playlist(id).tracks.map { it.origin!!.messageId })
    }

    @Test
    fun tracksFromAnotherAccountAreRejected() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val id = repo.createPlaylist("Mine").doneValue().id
        val result = repo.addTracks(id, listOf(testTrack(message = 1, account = "other"))).doneValue()
        assertEquals(AddTracksResult(added = 0, duplicates = 0, rejected = 1), result)
        assertTrue(repo.playlist(id).tracks.isEmpty())
    }

    @Test
    fun tracksStopAtThePerPlaylistLimit() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val id = repo.createPlaylist("Huge").doneValue().id
        val batch = (1..(MUSIC_MAX_TRACKS_PER_PLAYLIST + 1)).map { testTrack(message = it.toLong()) }
        assertEquals(
            AddTracksResult(added = MUSIC_MAX_TRACKS_PER_PLAYLIST, duplicates = 0, rejected = 1),
            repo.addTracks(id, batch).doneValue()
        )
        assertEquals(MUSIC_MAX_TRACKS_PER_PLAYLIST, repo.playlist(id).tracks.size)
    }

    @Test
    fun movingAndRemovingKeepTheRestInOrder() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val id = repo.createPlaylist("Order").doneValue().id
        repo.addTracks(
            id,
            listOf(testTrack(message = 1, title = "a"), testTrack(message = 2, title = "b"), testTrack(message = 3, title = "c"))
        ).doneValue()
        repo.moveTrack(id, 0, 2).doneValue()
        assertEquals(listOf("b", "c", "a"), titles(repo, id))
        val removedId = repo.playlist(id).tracks[1].itemId
        repo.removeTrack(id, removedId).doneValue()
        assertEquals(listOf("b", "a"), titles(repo, id))
        assertEquals(MusicFailure.NotFound, repo.removeTrack(id, "missing").failureOf())
        assertEquals(MusicFailure.InvalidPosition, repo.moveTrack(id, 0, 5).failureOf())
    }

    @Test
    fun duplicateGetsNewIdsAndTheCallerSuffix() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val sourceId = repo.createPlaylist("Rock").doneValue().id
        repo.addTracks(sourceId, listOf(testTrack(message = 1), testTrack(message = 2))).doneValue()
        val copy = repo.duplicatePlaylist(sourceId, "(copy)").doneValue()
        assertEquals("Rock (copy)", copy.title)
        assertNotEquals(sourceId, copy.id)
        assertEquals(0L, copy.revision)
        assertEquals(listOf(1L, 2L), copy.tracks.map { it.origin!!.messageId })
        val sourceEntryIds = repo.playlist(sourceId).tracks.map { it.itemId }
        assertTrue(copy.tracks.none { it.itemId in sourceEntryIds })
        assertEquals(3, repo.library.value.playlists.size)
    }

    @Test
    fun favoritesCannotBeRenamedOrDeleted() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        assertEquals(MusicFailure.ProtectedPlaylist, repo.deletePlaylist(MUSIC_FAVORITES_PLAYLIST_ID).failureOf())
        assertEquals(MusicFailure.ProtectedPlaylist, repo.renamePlaylist(MUSIC_FAVORITES_PLAYLIST_ID, "Other").failureOf())
        assertNotNull(repo.library.value.favorites)
    }

    @Test
    fun favoritesToggleByOriginAndReportTheNewState() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val song = testTrack(message = 7)
        assertEquals(true, repo.toggleFavorite(song).doneValue())
        assertTrue(repo.isFavorite(song.origin!!))
        assertEquals(1, repo.library.value.favorites!!.tracks.size)
        assertEquals(false, repo.toggleFavorite(song).doneValue())
        assertFalse(repo.isFavorite(song.origin!!))
        assertTrue(repo.library.value.favorites!!.tracks.isEmpty())
    }

    @Test
    fun sortingIsAppliedOnceAndAddingResetsTheMode() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val id = repo.createPlaylist("Sorted").doneValue().id
        repo.addTracks(
            id,
            listOf(testTrack(message = 1, title = "Charlie"), testTrack(message = 2, title = "alpha"), testTrack(message = 3, title = "Bravo"))
        ).doneValue()
        assertEquals(listOf("Charlie", "alpha", "Bravo"), titles(repo, id))
        repo.sortPlaylist(id, MusicSortMode.Title).doneValue()
        assertEquals(listOf("alpha", "Bravo", "Charlie"), titles(repo, id))
        assertEquals(MusicSortMode.Title, repo.playlist(id).sortMode)
        repo.addTracks(id, listOf(testTrack(message = 4, title = "Aardvark"))).doneValue()
        assertEquals(MusicSortMode.Manual, repo.playlist(id).sortMode)
        assertEquals(listOf("alpha", "Bravo", "Charlie", "Aardvark"), titles(repo, id))
    }

    @Test
    fun everyChangeBumpsRevisionAndUpdatedAtButNoOpsDoNot() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        clock.now = 1_000L
        val id = repo.createPlaylist("Rev").doneValue().id
        clock.now = 2_000L
        repo.renamePlaylist(id, "Rev 2").doneValue()
        assertEquals(1L, repo.playlist(id).revision)
        assertEquals(2_000L, repo.playlist(id).updatedAt)
        clock.now = 3_000L
        repo.addTrack(id, testTrack(message = 1)).doneValue()
        assertEquals(2L, repo.playlist(id).revision)
        assertEquals(3_000L, repo.playlist(id).updatedAt)
        clock.now = 4_000L
        repo.renamePlaylist(id, "Rev 2").doneValue()
        assertEquals(2L, repo.playlist(id).revision)
        assertEquals(3_000L, repo.playlist(id).updatedAt)
    }

    @Test
    fun aFailedWriteLeavesMemoryAndDiskUnchanged() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        persistence.failSaves = true
        assertEquals(MusicFailure.StorageUnavailable, repo.createPlaylist("Lost").failureOf())
        assertEquals(1, repo.library.value.playlists.size)
        assertTrue(persistence.stored.isEmpty())
    }

    @Test
    fun anUnreadableStoredLibraryRefusesEveryChange() = runTest {
        persistence.loadOverride = MusicLibraryLoad.Unavailable("schema 9")
        repo.setActiveAccount(TEST_ACCOUNT)
        assertEquals(MusicFailure.StorageUnavailable, repo.createPlaylist("Blocked").failureOf())
        assertEquals(0, persistence.saves)
    }

    @Test
    fun eachAccountHasItsOwnLibrary() = runTest {
        repo.setActiveAccount("a")
        repo.createPlaylist("Only A").doneValue()
        repo.setActiveAccount("b")
        assertEquals(listOf(MUSIC_FAVORITES_PLAYLIST_ID), repo.library.value.playlists.map { it.id })
        repo.createPlaylist("Only B").doneValue()
        repo.setActiveAccount("a")
        assertEquals(listOf("Only A"), repo.library.value.playlists.filterNot { it.isFavorites }.map { it.title })
        assertEquals("a", repo.activeAccountId)
    }

    @Test
    fun clearAccountDeletesItsStoredLibrary() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        repo.createPlaylist("Gone").doneValue()
        repo.clearAccount(TEST_ACCOUNT).doneValue()
        assertEquals(listOf(MUSIC_FAVORITES_PLAYLIST_ID), repo.library.value.playlists.map { it.id })
        assertFalse(persistence.stored.containsKey(TEST_ACCOUNT))
    }

    @Test
    fun recordingPlaysCountsAndKeepsOneHistoryEntryPerTrack() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val first = testTrack(message = 1).toTrackRef()
        val second = testTrack(message = 2).toTrackRef()
        repo.recordPlay(first, 100L).doneValue()
        repo.recordPlay(second, 200L).doneValue()
        repo.recordPlay(first, 300L).doneValue()
        val library = repo.library.value
        val stats = library.stats.getValue(first.trackKey)
        assertEquals(2, stats.playCount)
        assertEquals(300L, stats.lastPlayedAt)
        assertEquals(
            listOf(MusicHistoryEntry(first.trackKey, 300L), MusicHistoryEntry(second.trackKey, 200L)),
            library.recent
        )
    }

    @Test
    fun historyKeepsTheNewest200Tracks() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        for (message in 1..(MUSIC_MAX_RECENT_ENTRIES + 5)) {
            repo.recordPlay(testTrack(message = message.toLong()).toTrackRef(), message.toLong()).doneValue()
        }
        val recent = repo.library.value.recent
        assertEquals(MUSIC_MAX_RECENT_ENTRIES, recent.size)
        assertEquals(205L, recent.first().playedAt)
        assertEquals(6L, recent.last().playedAt)
    }

    @Test
    fun playlistCoversMustLiveInsideTheCoverDirectory() = runTest {
        val root = tempFolder.newFolder("covers")
        val covers = repositoryFor(persistence, clock, coverRoot = root)
        covers.setActiveAccount(TEST_ACCOUNT)
        val id = covers.createPlaylist("Cover").doneValue().id
        val inside = File(root, "cover.jpg").absolutePath
        assertEquals(MusicFailure.InvalidCover, covers.setCover(id, "/etc/passwd").failureOf())
        assertEquals(MusicFailure.InvalidCover, covers.setCover(id, File(root, "../escape.jpg").path).failureOf())
        assertEquals(MusicFailure.InvalidCover, covers.setCover(id, "relative.jpg").failureOf())
        covers.setCover(id, inside).doneValue()
        assertEquals(inside, covers.playlist(id).coverPath)
        covers.setCover(id, null).doneValue()
        assertNull(covers.playlist(id).coverPath)
    }

    @Test
    fun coversAreRefusedWhenNoCoverDirectoryIsConfigured() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val id = repo.createPlaylist("NoCovers").doneValue().id
        assertEquals(MusicFailure.InvalidCover, repo.setCover(id, File(tempFolder.root, "cover.jpg").path).failureOf())
    }
}
