package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TdAccountRegistryTest {
    private fun withAppFiles(block: (File) -> Unit) {
        val app = Files.createTempDirectory("beygram-registry-").toFile()
        try { block(app) } finally { app.deleteRecursively() }
    }

    @Test fun existingLegacyAuthorizationPathsAndBytesSurviveFirstBoot() = withAppFiles { app ->
        val dbFile = app.resolve("tdlib/database/td.bin").apply {
            parentFile!!.mkdirs()
            writeText("legacy-auth-do-not-migrate")
        }
        val legacyCache = app.resolve("tdlib/files/photo.bin").apply {
            parentFile!!.mkdirs()
            writeText("legacy-media")
        }
        val before = TdAccountStorageLayout(app).forAccount("legacy")
        val first = TdAccountRegistry(app).loadOrCreate()
        val after = TdAccountStorageLayout(app).forAccount(first.activeAccountId)
        assertEquals(listOf("legacy"), first.accountIds)
        assertEquals("legacy", first.activeAccountId)
        assertEquals(before, after)
        assertEquals("legacy-auth-do-not-migrate", dbFile.readText())
        assertEquals("legacy-media", legacyCache.readText())
        assertTrue(dbFile.isFile)
        assertTrue(legacyCache.isFile)
    }

    @Test fun repeatedLaunchDoesNotResetOrRewriteAccounts() = withAppFiles { app ->
        val registry = TdAccountRegistry(app)
        val (prepared, id) = registry.prepareSecondaryAccount()
        val committed = app.resolve("tdlib-accounts/.registry-v1")
        val before = committed.readBytes().toList()
        val restarted = TdAccountRegistry(app).loadOrCreate()
        assertEquals(prepared, restarted)
        assertEquals(listOf("legacy", id), restarted.accountIds)
        assertEquals(before, committed.readBytes().toList())
    }

    @Test fun futureSecondaryUsesDifferentPathsWithoutCreatingDatabase() = withAppFiles { app ->
        val registry = TdAccountRegistry(app)
        val (_, id1) = registry.prepareSecondaryAccount()
        val (_, id2) = registry.prepareSecondaryAccount()
        val layout = TdAccountStorageLayout(app)
        assertNotEquals(id1, id2)
        assertNotEquals(layout.forAccount(id1).database, layout.forAccount(id2).database)
        assertNotEquals(layout.forAccount("legacy").database, layout.forAccount(id1).database)
        assertFalse(layout.forAccount(id1).database.exists())
        assertFalse(layout.forAccount(id2).database.exists())
    }

    @Test fun selectedAccountPersistsButCannotReferenceUnregisteredId() = withAppFiles { app ->
        val registry = TdAccountRegistry(app)
        val (_, id) = registry.prepareSecondaryAccount()
        registry.selectAccount(id)
        assertEquals(id, TdAccountRegistry(app).loadOrCreate().activeAccountId)
        try {
            registry.selectAccount("a-not-registered")
            fail("Unknown account must not become active")
        } catch (_: IllegalArgumentException) { }
        assertEquals(id, TdAccountRegistry(app).loadOrCreate().activeAccountId)
        registry.selectAccount("legacy")
        assertEquals("legacy", TdAccountRegistry(app).loadOrCreate().activeAccountId)
    }

    @Test fun interruptedAtomicWriteNeverBecomesAuthoritative() = withAppFiles { app ->
        val registry = TdAccountRegistry(app)
        val original = registry.prepareSecondaryAccount().first
        app.resolve("tdlib-accounts/.registry-v1.pending").writeText(
            "beygram-accounts-v1\nactive=a-unknown\naccounts=legacy,a-unknown\n"
        )
        assertEquals(original, TdAccountRegistry(app).loadOrCreate())
        val prepared = TdAccountRegistry(app).prepareSecondaryAccount()
        assertEquals(3, prepared.first.accountIds.size)
        assertFalse(app.resolve("tdlib-accounts/.registry-v1.pending").exists())
    }

    @Test fun malformedCommittedRegistryIsNotSilentlyReset() = withAppFiles { app ->
        val registry = TdAccountRegistry(app)
        registry.prepareSecondaryAccount()
        val file = app.resolve("tdlib-accounts/.registry-v1")
        val bytes = "CORRUPTED DO NOT OVERWRITE"
        file.writeText(bytes)
        try {
            registry.loadOrCreate()
            fail("Corruption cannot reset secondary accounts")
        } catch (_: IllegalArgumentException) { }
        assertEquals(bytes, file.readText())
        assertFalse(app.resolve("tdlib-accounts/.registry-v1.pending").exists())
    }

    @Test fun tamperedActiveAccountOrDuplicateIdsAreRejected() = withAppFiles { app ->
        val registry = TdAccountRegistry(app)
        registry.loadOrCreate()
        val file = app.resolve("tdlib-accounts/.registry-v1")
        for (text in listOf(
            "beygram-accounts-v1\nactive=a-unknown\naccounts=legacy\n",
            "beygram-accounts-v1\nactive=legacy\naccounts=legacy,legacy\n",
            "beygram-accounts-v1\nactive=legacy\naccounts=legacy,../../elsewhere\n"
        )) {
            file.writeText(text)
            try {
                registry.loadOrCreate()
                fail("Invalid registry was accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun symlinkedRegistryRootCannotEscapeApplicationDirectory() = withAppFiles { app ->
        val outside = Files.createTempDirectory("beygram-external-registry-").toFile()
        try {
            Files.createSymbolicLink(app.resolve("tdlib-accounts").toPath(), outside.toPath())
            try {
                TdAccountRegistry(app).loadOrCreate()
                fail("Registry must not follow external symlinks")
            } catch (_: IllegalArgumentException) { }
            assertFalse(outside.resolve(".registry-v1").exists())
        } finally { outside.deleteRecursively() }
    }

    @Test fun concurrentRegistrationsDoNotLoseUpdates() = withAppFiles { app ->
        val pool = Executors.newFixedThreadPool(4)
        val go = CountDownLatch(1)
        try {
            val registry = TdAccountRegistry(app)
            val futures = (0 until 4).map {
                pool.submit<String> {
                    go.await()
                    registry.prepareSecondaryAccount().second
                }
            }
            go.countDown()
            val ids = futures.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(4, ids.toSet().size)
            assertEquals(5, TdAccountRegistry(app).loadOrCreate().accountIds.size)
        } finally { pool.shutdownNow() }
    }
}
