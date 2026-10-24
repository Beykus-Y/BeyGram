package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TdAccountStorageLayoutTest {
    private fun withTemporaryFilesDir(block: (File) -> Unit) {
        val path = Files.createTempDirectory("beygram-accounts-").toFile()
        try { block(path) } finally { path.deleteRecursively() }
    }

    @Test fun legacyKeepsExistingTdlibPathsWithoutMovingFiles() = withTemporaryFilesDir { app ->
        val original = app.resolve("tdlib/database").apply { mkdirs() }.resolve("td.bin")
        original.writeText("keep-existing-session")
        val layout = TdAccountStorageLayout(app)
        val paths = layout.forAccount("legacy")
        assertEquals(app.resolve("tdlib/database").canonicalFile, paths.database)
        assertEquals(app.resolve("tdlib/files").canonicalFile, paths.files)
        assertEquals("keep-existing-session", original.readText())
        assertTrue(original.isFile)
    }

    @Test fun differentAccountIdsNeverShareDatabaseOrFilesDirectory() = withTemporaryFilesDir { app ->
        val layout = TdAccountStorageLayout(app)
        val first = layout.forAccount("a-111")
        val second = layout.forAccount("a-222")
        val legacy = layout.forAccount("legacy")
        assertNotEquals(first.database, second.database)
        assertNotEquals(first.files, second.files)
        assertNotEquals(first.database, legacy.database)
        assertNotEquals(first.files, legacy.files)
        assertNotEquals(first.database, first.files)
        assertTrue(first.database.toPath().startsWith(app.resolve("tdlib-accounts").toPath()))
    }

    @Test fun unsafeIdsCannotEscapeStorageOrUseReservedLegacySpelling() = withTemporaryFilesDir { app ->
        val layout = TdAccountStorageLayout(app)
        listOf("../tdlib", "/data/data/secret", ".", "..", "two/parts", "bad\\name", "")
            .forEach { candidate ->
                try { layout.forAccount(candidate); fail("Allowed unsafe account ID: $candidate") }
                catch (_: IllegalArgumentException) { }
            }
        assertNotEquals(layout.forAccount("legacy").database, layout.forAccount("Legacy").database)
    }

    @Test fun newlyAllocatedIdsAreUniqueOpaqueAndHavePrivatePaths() = withTemporaryFilesDir { app ->
        val layout = TdAccountStorageLayout(app)
        val ids = (0 until 200).map { layout.newSecondaryId() }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it == "legacy" })
        ids.forEach { assertTrue(layout.forAccount(it).database.path.startsWith(app.canonicalPath)) }
    }

    @Test fun existingSymlinkAliasCannotReuseLegacyOrSiblingAccountDirectory() = withTemporaryFilesDir { app ->
        val layout = TdAccountStorageLayout(app)
        app.resolve("tdlib").mkdirs()
        val secondaryRoot = app.resolve("tdlib-accounts").apply { mkdirs() }
        val alias = secondaryRoot.resolve("a-legacy")
        java.nio.file.Files.createSymbolicLink(alias.toPath(), java.nio.file.Paths.get("../tdlib"))
        try {
            try { layout.forAccount("a-legacy"); fail("Symlink to legacy must be refused") }
            catch (_: IllegalArgumentException) {}
        } finally { alias.delete() }

        secondaryRoot.resolve("a-real").mkdirs()
        val siblingAlias = secondaryRoot.resolve("a-fake")
        java.nio.file.Files.createSymbolicLink(siblingAlias.toPath(), java.nio.file.Paths.get("a-real"))
        try {
            try { layout.forAccount("a-fake"); fail("Symlink to another account must be refused") }
            catch (_: IllegalArgumentException) {}
        } finally { siblingAlias.delete() }
    }

    @Test fun separateLeaseManagerInstancesStillGuardOneProcessDatabase() = withTemporaryFilesDir { app ->
        val db = TdAccountStorageLayout(app).forAccount("a-shared").database
        val first = TdAccountDatabaseLeases().acquire(db)
        try {
            try { TdAccountDatabaseLeases().acquire(db); fail("Separate owners must not double-open TDLib") }
            catch (_: IllegalStateException) {}
        } finally { first.releaseAfterClientClosed() }
        TdAccountDatabaseLeases().acquire(db).releaseAfterClientClosed()
    }

    @Test fun duplicateDatabaseLeaseIsRejectedEvenForDifferentPathSpelling() = withTemporaryFilesDir { app ->
        val db = TdAccountStorageLayout(app).forAccount("a-001").database
        val leases = TdAccountDatabaseLeases()
        val first = leases.acquire(db)
        try {
            try {
                leases.acquire(File(db, "../database"))
                fail("Two TDLib clients must never open the same database")
            } catch (_: IllegalStateException) { }
        } finally { first.releaseAfterClientClosed(); first.releaseAfterClientClosed() }
        leases.acquire(db).releaseAfterClientClosed()
    }

    @Test fun sharedAccountIdRuleAcceptsSafeIdsUpToSixtyFourCharacters() {
        assertTrue(TdAccountStorageLayout.isValidAccountId("legacy"))
        assertTrue(TdAccountStorageLayout.isValidAccountId("a_B-9"))
        assertTrue(TdAccountStorageLayout.isValidAccountId("a-" + "x".repeat(62)))
        assertFalse(TdAccountStorageLayout.isValidAccountId("a-" + "x".repeat(63)))
        assertFalse(TdAccountStorageLayout.isValidAccountId(""))
        assertFalse(TdAccountStorageLayout.isValidAccountId("../tdlib"))
        assertFalse(TdAccountStorageLayout.isValidAccountId("two/parts"))
        assertFalse(TdAccountStorageLayout.isValidAccountId("a b"))
    }
}
