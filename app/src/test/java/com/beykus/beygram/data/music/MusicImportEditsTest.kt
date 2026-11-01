package com.beykus.beygram.data.music

import com.beykus.beygram.data.music.bgmusic.BgMusicExportIdentity
import com.beykus.beygram.data.music.bgmusic.BgMusicImportDecision
import com.beykus.beygram.data.music.bgmusic.BgMusicImportPlanner
import com.beykus.beygram.data.music.bgmusic.BgMusicLibraryMapping
import com.beykus.beygram.playback.PlaybackOrigin
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class MusicImportEditsTest {
    private val clock = FakeClock()
    private val persistence = FakeMusicPersistence()
    private val repo = repositoryFor(persistence, clock)
    private val sourceUuid = "99999999-8888-4777-8666-555555555555"

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun origin(message: Long, account: String = TEST_ACCOUNT) =
        PlaybackOrigin(chatId = 7L, messageId = message, accountId = account)

    private fun entry(itemId: String, title: String, origin: PlaybackOrigin?) = PlaylistTrack(
        itemId = itemId,
        title = title,
        performer = "Performer",
        album = "Album",
        durationSeconds = 100,
        origin = origin,
        addedAt = 0L,
    )

    /** A playlist as read from a file: [revision] is the file's revision, and the copy's own revision stays 0. */
    private fun snapshot(
        vararg tracks: PlaylistTrack,
        title: String = "  Imported  ",
        revision: Long = 7L,
        source: String? = "source-1",
        cover: String? = null,
    ) = MusicPlaylist(
        id = "",
        title = title,
        description = "desc",
        tracks = tracks.toList(),
        coverPath = cover,
        sourcePlaylistId = source,
        sourceRevision = revision,
    )

    /** The planner's answer for a file of playlist "source-1" at [revision], against the library of [repository]. */
    private fun decide(revision: Long, repository: MusicLibraryRepository = repo): BgMusicImportDecision =
        BgMusicImportPlanner.decide("source-1", revision, repository.library.value.playlists)

    /** A repository whose ids are random UUIDs, as the app's are, so the copies it makes export under a UUID of their own. */
    private fun uuidRepository(): MusicLibraryRepository = MusicLibraryRepository(
        persistence = FakeMusicPersistence(),
        coverRoot = null,
        clock = { clock.now },
        newId = { UUID.randomUUID().toString() },
        ioDispatcher = Dispatchers.Unconfined
    )

    @Test
    fun importKeepsOrderRevisionAndSourceAndDropsForeignOrigins() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)

        val copy = repo.importPlaylistCopy(
            snapshot(
                entry("t-1", "One", origin(10)),
                entry("t-2", "Two", origin(11, account = "someone-else")),
                entry("t-3", "Three", null),
            ),
        ).doneValue()

        assertEquals("id-1", copy.id)
        assertEquals("Imported", copy.title)
        assertEquals(0L, copy.revision)
        assertEquals(7L, copy.sourceRevision)
        assertEquals("source-1", copy.sourcePlaylistId)
        assertEquals(listOf("One", "Two", "Three"), copy.tracks.map { it.title })
        assertEquals(listOf(origin(10), null, null), copy.tracks.map { it.origin })
        assertNotNull(repo.library.value.playlist(copy.id))
    }

    @Test
    fun repeatedOriginIsSkippedInsteadOfDuplicated() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)

        val copy = repo.importPlaylistCopy(
            snapshot(
                entry("t-1", "One", origin(10)),
                entry("t-2", "Again", origin(10)),
                entry("t-3", "Other", origin(11)),
            ),
        ).doneValue()

        assertEquals(listOf("One", "Other"), copy.tracks.map { it.title })
    }

    @Test
    fun repeatedEntryIdsGetFreshOnes() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)

        val copy = repo.importPlaylistCopy(
            snapshot(entry("same", "A", null), entry("same", "B", null)),
        ).doneValue()

        assertEquals("same", copy.tracks[0].itemId)
        assertNotEquals("same", copy.tracks[1].itemId)
    }

    @Test
    fun replaceKeepsTheLocalIdAndTakesTheFilesContent() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val copy = repo.importPlaylistCopy(snapshot(entry("t-1", "One", origin(10)), revision = 3L)).doneValue()

        repo.replaceImportedPlaylist(copy.id, snapshot(entry("t-9", "Nine", null), title = "Renamed", revision = 8L)).doneValue()

        val replaced = repo.library.value.playlist(copy.id)!!
        assertEquals("Renamed", replaced.title)
        assertEquals(1L, replaced.revision)
        assertEquals(8L, replaced.sourceRevision)
        assertEquals(1L, replaced.revisionAtSource)
        assertEquals("source-1", replaced.sourcePlaylistId)
        assertEquals(listOf("t-9"), replaced.tracks.map { it.itemId })
        assertEquals(listOf("Nine"), replaced.tracks.map { it.title })
    }

    @Test
    fun favoritesCannotBeReplacedByAFile() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)

        val outcome = repo.replaceImportedPlaylist(MUSIC_FAVORITES_PLAYLIST_ID, snapshot(entry("t-1", "One", null)))

        assertEquals(MusicFailure.ProtectedPlaylist, outcome.failureOf())
    }

    @Test
    fun blankTitleAndUnknownTargetAreRefused() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)

        assertEquals(MusicFailure.InvalidTitle, repo.importPlaylistCopy(snapshot(title = "   ")).failureOf())
        assertEquals(MusicFailure.NotFound, repo.replaceImportedPlaylist("missing", snapshot()).failureOf())
    }

    @Test
    fun importedCoverIsStoredInTheSameWriteAndTheFileIsStillRecognised() = runTest {
        val root = tempFolder.newFolder("covers")
        val covers = repositoryFor(FakeMusicPersistence(), clock, coverRoot = root)
        covers.setActiveAccount(TEST_ACCOUNT)
        val cover = File(root, "imported.jpg").apply { writeText("pixels") }

        val copy = covers.importPlaylistCopy(
            snapshot(entry("t-1", "One", origin(10)), revision = 3L, cover = cover.absolutePath),
        ).doneValue()

        val stored = covers.library.value.playlist(copy.id)!!
        assertEquals(cover.absolutePath, stored.coverPath)
        assertEquals(0L, stored.revision)
        // The same file, opened again, is already saved: the cover did not bump the revision the file is compared with.
        assertEquals(BgMusicImportDecision.SameRevision(3L), decide(3L, covers))
    }

    @Test
    fun importWithACoverOutsideTheLibraryIsRefused() = runTest {
        val root = tempFolder.newFolder("covers")
        val covers = repositoryFor(FakeMusicPersistence(), clock, coverRoot = root)
        covers.setActiveAccount(TEST_ACCOUNT)
        val outside = File(tempFolder.root, "outside.jpg").apply { writeText("x") }

        assertEquals(MusicFailure.InvalidCover, covers.importPlaylistCopy(snapshot(cover = outside.absolutePath)).failureOf())
        assertTrue(covers.library.value.playlists.none { it.sourcePlaylistId != null })
    }

    @Test
    fun localEditsNeverHideANewerFileAndAnUpdateReplacesThemToo() = runTest {
        repo.setActiveAccount(TEST_ACCOUNT)
        val copy = repo.importPlaylistCopy(snapshot(entry("t-1", "One", null), revision = 3L)).doneValue()
        repo.renamePlaylist(copy.id, "My version").doneValue()
        repo.addTrack(copy.id, testTrack(message = 9L, title = "Mine")).doneValue()

        assertEquals(BgMusicImportDecision.SameRevision(3L), decide(3L))
        assertEquals(BgMusicImportDecision.NewerRevision(3L), decide(4L))

        // The update replaces the saved tracks, the track added locally included.
        repo.replaceImportedPlaylist(copy.id, snapshot(entry("t-2", "Two", null), revision = 4L)).doneValue()

        assertEquals(listOf("Two"), repo.library.value.playlist(copy.id)!!.tracks.map { it.title })
        assertEquals(BgMusicImportDecision.SameRevision(4L), decide(4L))
    }

    @Test
    fun replacingWithAFileWithoutACoverKeepsTheSavedOne() = runTest {
        val root = tempFolder.newFolder("covers")
        val covers = repositoryFor(FakeMusicPersistence(), clock, coverRoot = root)
        covers.setActiveAccount(TEST_ACCOUNT)
        val cover = File(root, "saved.jpg").apply { writeText("pixels") }
        val copy = covers.importPlaylistCopy(
            snapshot(entry("t-1", "One", null), revision = 3L, cover = cover.absolutePath),
        ).doneValue()

        covers.replaceImportedPlaylist(copy.id, snapshot(entry("t-2", "Two", null), revision = 4L)).doneValue()

        assertEquals(cover.absolutePath, covers.library.value.playlist(copy.id)?.coverPath)
        assertTrue(cover.exists())
    }

    @Test
    fun exportFollowsTheCopyThroughImportEditUpdateAndEdit() = runTest {
        val repository = uuidRepository()
        repository.setActiveAccount(TEST_ACCOUNT)
        val copy = repository.importPlaylistCopy(
            snapshot(entry("t-1", "One", null), revision = 3L, source = sourceUuid),
        ).doneValue()
        fun exported() = BgMusicLibraryMapping.exportIdentity(
            repository.library.value.playlist(copy.id)!!,
            freshId = { error("a copy keeps its UUID id, so it never needs a fresh one") },
        )

        assertEquals(BgMusicExportIdentity(sourceUuid, 3L), exported())
        repository.renamePlaylist(copy.id, "My version").doneValue()
        assertEquals(BgMusicExportIdentity(copy.id, 1L), exported())
        repository.replaceImportedPlaylist(
            copy.id,
            snapshot(entry("t-2", "Two", null), revision = 4L, source = sourceUuid),
        ).doneValue()
        assertEquals(BgMusicExportIdentity(sourceUuid, 4L), exported())
        repository.addTrack(copy.id, testTrack(message = 9L, title = "Mine")).doneValue()
        assertEquals(BgMusicExportIdentity(copy.id, 3L), exported())
    }
}
