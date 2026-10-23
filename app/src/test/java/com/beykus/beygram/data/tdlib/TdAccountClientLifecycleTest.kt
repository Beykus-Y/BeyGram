package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TdAccountClientLifecycleTest {
    private fun withPrivateFiles(block: (File) -> Unit) {
        val temp = Files.createTempDirectory("tdlib-client-owner-").toFile()
        try { block(temp) } finally { temp.deleteRecursively() }
    }

    @Test fun existingLegacyClientUsesOriginalDatabaseAndMediaFiles() = withPrivateFiles { app ->
        val original = app.resolve("tdlib/database/td.bin").apply {
            parentFile!!.mkdirs()
            writeText("existing auth")
        }
        var close: (() -> Unit)? = null
        val paths = TdAccountClientLifecycle(app).open("legacy") { directories, onClosed ->
            close = onClosed
            directories
        }
        assertEquals(app.resolve("tdlib/database").canonicalFile, paths.database)
        assertEquals(app.resolve("tdlib/files").canonicalFile, paths.files)
        close!!.invoke()
        assertEquals("existing auth", original.readText())
    }

    @Test fun twoOwnersCannotOpenSameDatabaseEvenWithSeparateFactoryInstances() = withPrivateFiles { app ->
        val first = TdAccountClientLifecycle(app)
        val second = TdAccountClientLifecycle(app)
        var terminal: (() -> Unit)? = null
        first.open("legacy") { _, nativeClosed ->
            terminal = nativeClosed
            Any()
        }
        try {
            try {
                second.open("legacy") { _, _ -> Any() }
                fail("TDLib database was opened twice")
            } catch (_: IllegalStateException) { }
        } finally { terminal!!.invoke() }
        second.open("legacy") { _, nativeClosed -> nativeClosed(); Any() }
    }

    @Test fun logoutOrCancelWithoutNativeCloseNeverReleasesLease() = withPrivateFiles { app ->
        val sessions = TdAccountClientLifecycle(app)
        var nativeClosed: (() -> Unit)? = null
        sessions.open("legacy") { _, terminal -> nativeClosed = terminal; Any() }
        // No callback is fired by a UI cancel or by AuthorizationStateLoggingOut.
        try {
            sessions.open("legacy") { _, _ -> Any() }
            fail("Lease released before AuthorizationStateClosed")
        } catch (_: IllegalStateException) { }
        nativeClosed!!.invoke()
        sessions.open("legacy") { _, terminal -> terminal(); Any() }
    }

    @Test fun duplicateStaleCloseCannotReleaseLaterClientLease() = withPrivateFiles { app ->
        val sessions = TdAccountClientLifecycle(app)
        var firstClose: (() -> Unit)? = null
        sessions.open("legacy") { _, terminal -> firstClose = terminal; Any() }
        firstClose!!.invoke()
        var secondClose: (() -> Unit)? = null
        sessions.open("legacy") { _, terminal -> secondClose = terminal; Any() }
        firstClose!!.invoke() // repeated async closed event from the first session
        try {
            sessions.open("legacy") { _, _ -> Any() }
            fail("Old closure unexpectedly released the replacement client")
        } catch (_: IllegalStateException) { }
        secondClose!!.invoke()
    }

    @Test fun factoryFailureReleasesDatabaseForRetry() = withPrivateFiles { app ->
        val sessions = TdAccountClientLifecycle(app)
        try {
            sessions.open("legacy") { _, _ -> throw IllegalArgumentException("could not initialize native client") }
            fail("Failure must propagate")
        } catch (e: IllegalArgumentException) {
            assertEquals("could not initialize native client", e.message)
        }
        var closed = false
        sessions.open("legacy") { _, terminal -> terminal(); closed = true; Any() }
        assertTrue(closed)
    }

    @Test fun twoDistinctAccountIdsCanBeLeasedTogetherWithoutSharedPaths() = withPrivateFiles { app ->
        val sessions = TdAccountClientLifecycle(app)
        var primaryClose: (() -> Unit)? = null
        var secondaryClose: (() -> Unit)? = null
        val primary = sessions.open("legacy") { paths, terminal ->
            primaryClose = terminal
            paths
        }
        val secondary = sessions.open("a-secondary") { paths, terminal ->
            secondaryClose = terminal
            paths
        }
        assertNotEquals(primary.database, secondary.database)
        assertNotEquals(primary.files, secondary.files)
        assertTrue(secondary.database.path.startsWith(app.resolve("tdlib-accounts").canonicalPath))
        secondaryClose!!.invoke()
        primaryClose!!.invoke()
    }
}
