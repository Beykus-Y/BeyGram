package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackOrigin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicImportEditsTest {
    private val clock = FakeClock()
    private val persistence = FakeMusicPersistence()
    private val repo = repositoryFor(persistence, clock)

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

    private fun snapshot(
        vararg tracks: PlaylistTrack,
        title: String = "  Imported  ",
        revision: Long = 7L,
        source: String? = "source-1",
    ) = MusicPlaylist(
        id = "",
        title = title,
        description = "desc",
        tracks = tracks.toList(),
        revision = revision,
        sourcePlaylistId = source,
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
        assertEquals(7L, copy.revision)
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
        assertEquals(8L, replaced.revision)
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
}
