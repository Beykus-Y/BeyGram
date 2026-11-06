package com.beykus.beygram.data.music

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A library whose clear failed, or never ran because the process ended first, must not be shown to the next selection. */
class MusicPendingClearTest {
    private val persistence = FakeMusicPersistence()
    private val pendingClears = MemoryMusicPendingClears()
    private val repository = repositoryFor(persistence, FakeClock(), pendingClears = pendingClears)

    @Test
    fun aFailedOwedClearKeepsTheLibraryHiddenAndTheMark() = runTest {
        repository.setActiveAccount(TEST_ACCOUNT)
        repository.createPlaylist("Old mix").doneValue()
        assertTrue(repository.markClearOwed(TEST_ACCOUNT))
        persistence.failClears = true

        repository.setActiveAccount(TEST_ACCOUNT)

        assertTrue(repository.library.value.playlists.none { it.title == "Old mix" })
        assertTrue(repository.isLoaded.value)
        assertTrue(repository.storageError.value)
        assertTrue(pendingClears.isMarked(TEST_ACCOUNT))
        assertEquals(MusicFailure.StorageUnavailable, repository.createPlaylist("New").failureOf())
    }

    @Test
    fun aLaterSelectionClearsTheOwedLibraryAndThenStartsEmpty() = runTest {
        repository.setActiveAccount(TEST_ACCOUNT)
        repository.createPlaylist("Old mix").doneValue()
        repository.markClearOwed(TEST_ACCOUNT)
        persistence.failClears = true
        repository.setActiveAccount(TEST_ACCOUNT)

        persistence.failClears = false
        repository.setActiveAccount(TEST_ACCOUNT)

        assertFalse(pendingClears.isMarked(TEST_ACCOUNT))
        assertFalse(repository.storageError.value)
        assertTrue(repository.library.value.playlists.none { it.title == "Old mix" })
        assertFalse(persistence.stored.containsKey(TEST_ACCOUNT))
        repository.createPlaylist("New").doneValue()
        assertEquals(listOf("New"), repository.library.value.playlists.filterNot { it.isFavorites }.map { it.title })
    }

    @Test
    fun aRestartedRepositoryClearsAnOwedLibraryBeforeShowingIt() = runTest {
        repository.setActiveAccount(TEST_ACCOUNT)
        repository.createPlaylist("Old mix").doneValue()
        // The process ends here, before the clear that logging out started could run.
        repository.markClearOwed(TEST_ACCOUNT)

        val restarted = repositoryFor(persistence, FakeClock(), pendingClears = pendingClears)
        restarted.setActiveAccount(TEST_ACCOUNT)

        assertTrue(restarted.library.value.playlists.none { it.title == "Old mix" })
        assertFalse(restarted.storageError.value)
        assertFalse(persistence.stored.containsKey(TEST_ACCOUNT))
        assertFalse(pendingClears.isMarked(TEST_ACCOUNT))
    }

    @Test
    fun theOwedClearRunsWhenNoSelectionHasDoneItYet() = runTest {
        repository.setActiveAccount(TEST_ACCOUNT)
        repository.createPlaylist("Old mix").doneValue()
        repository.markClearOwed(TEST_ACCOUNT)

        repository.clearOwed(TEST_ACCOUNT).doneValue()

        assertFalse(pendingClears.isMarked(TEST_ACCOUNT))
        assertFalse(persistence.stored.containsKey(TEST_ACCOUNT))
        assertTrue(repository.library.value.playlists.none { it.title == "Old mix" })
    }

    @Test
    fun aClearOwedAfterASelectionLoadedANewLibraryLeavesItAlone() = runTest {
        repository.setActiveAccount(TEST_ACCOUNT)
        repository.createPlaylist("Old mix").doneValue()
        repository.markClearOwed(TEST_ACCOUNT)
        // The selection clears the owed library first, and then the account gets a new one.
        repository.setActiveAccount(TEST_ACCOUNT)
        repository.createPlaylist("Kept").doneValue()

        // The background clear started by the logout runs now. Its mark is gone, so the new library stays.
        repository.clearOwed(TEST_ACCOUNT).doneValue()

        assertTrue(repository.library.value.playlists.any { it.title == "Kept" })
        assertTrue(persistence.stored.getValue(TEST_ACCOUNT).playlists.any { it.title == "Kept" })
    }

    @Test
    fun nothingIsWrittenToAnAccountWhoseClearIsOwed() = runTest {
        repository.setActiveAccount(TEST_ACCOUNT)
        repository.markClearOwed(TEST_ACCOUNT)

        assertEquals(MusicFailure.StorageUnavailable, repository.createPlaylist("Late").failureOf())
        assertFalse(persistence.stored.containsKey(TEST_ACCOUNT))
    }

    @Test
    fun aRemovalThatDidNotReachTheDiskKeepsTheAccountOwedAndUnwritable() = runTest {
        val marks = FlakyRemovalPendingClears()
        val repository = repositoryFor(persistence, FakeClock(), pendingClears = marks)
        repository.setActiveAccount(TEST_ACCOUNT)
        repository.createPlaylist("Old mix").doneValue()
        repository.markClearOwed(TEST_ACCOUNT)
        marks.failRemovals = true

        assertEquals(MusicFailure.StorageUnavailable, repository.clearOwed(TEST_ACCOUNT).failureOf())

        // The library is gone, but the mark is still on disk: nothing may be saved while a restart would clear it again.
        assertEquals(MusicFailure.StorageUnavailable, repository.createPlaylist("Late").failureOf())
        assertFalse(persistence.stored.containsKey(TEST_ACCOUNT))
        assertTrue(marks.onDisk.contains(TEST_ACCOUNT))

        marks.failRemovals = false
        repository.setActiveAccount(TEST_ACCOUNT)

        assertFalse(marks.onDisk.contains(TEST_ACCOUNT))
        assertFalse(repository.storageError.value)
        repository.createPlaylist("New").doneValue()
        assertTrue(persistence.stored.getValue(TEST_ACCOUNT).playlists.any { it.title == "New" })
    }
}

/**
 * Marks as SharedPreferences keeps them: a change shows at once in memory, and reaches the disk only when its commit succeeds.
 * [failRemovals] makes a removal fail the way a failed commit does.
 */
private class FlakyRemovalPendingClears : MusicPendingClears {
    val onDisk = HashSet<String>()
    private val inMemory = HashSet<String>()
    var failRemovals = false

    override fun isMarked(accountId: String): Boolean = accountId in inMemory

    override fun mark(accountId: String): Boolean {
        inMemory.add(accountId)
        onDisk.add(accountId)
        return true
    }

    override fun unmark(accountId: String): Boolean {
        inMemory.remove(accountId)
        if (failRemovals) return false
        onDisk.remove(accountId)
        return true
    }
}
