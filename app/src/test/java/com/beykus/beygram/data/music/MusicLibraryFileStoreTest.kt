package com.beykus.beygram.data.music

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MusicLibraryFileStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun store(): MusicLibraryFileStore = MusicLibraryFileStore(File(tempFolder.root, "music_library"))

    private fun library(accountId: String, title: String) = MusicLibrary(
        accountId = accountId,
        playlists = listOf(
            favoritesPlaylist(1L),
            MusicPlaylist(id = "playlist-$title", title = title, createdAt = 1L, updatedAt = 1L)
        )
    )

    @Test
    fun fileNamesAreHashedAndSeparatePerAccount() {
        val store = store()
        val first = store.fileFor("account-one")
        val second = store.fileFor("account-two")
        assertNotEquals(first.name, second.name)
        assertTrue(first.name.matches(Regex("[0-9a-f]{64}\\.json")))
        assertFalse(first.name.contains("account-one"))
        assertEquals(first.name, store.fileFor("account-one").name)
        assertEquals(store.fileFor("account-one").parentFile, store.fileFor("account-two").parentFile)
    }

    @Test
    fun saveThenLoadReturnsTheSameLibraryAndLeavesNoTemporaryFile() = runTest {
        val store = store()
        val original = library("acc", "Mix")
        store.save(original)
        assertEquals(MusicLibraryLoad.Loaded(original), store.load("acc"))
        val names = store.fileFor("acc").parentFile!!.list()!!.toList()
        assertTrue(names.none { it.endsWith(".tmp") })
    }

    @Test
    fun anotherAccountNeverSeesTheLibrary() = runTest {
        val store = store()
        store.save(library("acc", "Mix"))
        assertEquals(MusicLibraryLoad.Empty, store.load("other"))
    }

    @Test
    fun corruptFileIsMovedAsideAndNotOverwritten() = runTest {
        val store = store()
        val file = store.fileFor("acc")
        file.parentFile!!.mkdirs()
        file.writeText("{broken")
        assertEquals(MusicLibraryLoad.Empty, store.load("acc"))
        assertFalse(file.exists())
        val backup = file.parentFile!!.listFiles()!!.single { it.name.endsWith(".bak") }
        assertEquals("{broken", backup.readText())
    }

    @Test
    fun newerSchemaIsUnavailableAndStaysOnDisk() = runTest {
        val store = store()
        val file = store.fileFor("acc")
        file.parentFile!!.mkdirs()
        val future = """{"schemaVersion": 2, "accountId": "acc", "playlists": []}"""
        file.writeText(future)
        assertTrue(store.load("acc") is MusicLibraryLoad.Unavailable)
        assertEquals(future, file.readText())
    }

    @Test
    fun fileThatBelongsToAnotherAccountIsNotReadAsThisOne() = runTest {
        val store = store()
        val file = store.fileFor("acc")
        file.parentFile!!.mkdirs()
        file.writeText(MusicLibraryJson.encode(library("someone-else", "Mix")))
        assertEquals(MusicLibraryLoad.Empty, store.load("acc"))
        assertFalse(file.exists())
    }

    @Test
    fun clearRemovesOnlyTheAccountIncludingItsBackups() = runTest {
        val store = store()
        store.save(library("acc", "Mix"))
        store.save(library("other", "Keep"))
        val file = store.fileFor("acc")
        file.writeText("{broken")
        assertEquals(MusicLibraryLoad.Empty, store.load("acc"))
        store.clear("acc")
        assertEquals(MusicLibraryLoad.Empty, store.load("acc"))
        assertEquals(MusicLibraryLoad.Loaded(library("other", "Keep")), store.load("other"))
        assertTrue(file.parentFile!!.listFiles()!!.none { it.name.startsWith(musicLibraryFileStem("acc")) })
    }
}
