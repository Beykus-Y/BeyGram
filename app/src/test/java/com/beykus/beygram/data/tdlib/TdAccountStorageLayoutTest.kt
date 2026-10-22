package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
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

    @Test fun duplicateDatabaseLeaseIsRejectedEvenForDifferentPathSpelling() = withTemporaryFilesDir { app ->
        val db = TdAccountStorageLayout(app).forAccount("a-001").database
        val leases = TdAccountDatabaseLeases()
        val first = leases.acquire(db)
        try {
            try {
                leases.acquire(File(db, "../database"))
                fail("Two TDLib clients must never open the same database")
            } catch (_: IllegalStateException) { }
        } finally { first.close(); first.close() }
        leases.acquire(db).close()
    }
}
