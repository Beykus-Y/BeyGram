package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TdAccountNativeClientOwnerTest {
    private data class FakeClient(val config: TdLibConfig, val handler: Client.ResultHandler) {
        fun emit(update: TdApi.Object) = handler.onResult(update)
        fun close() = emit(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
    }

    private fun withOwner(block: (TdAccountRegistry, TdAccountNativeClientOwner) -> Unit) {
        val root = Files.createTempDirectory("beygram-native-owner-").toFile()
        try {
            val registry = TdAccountRegistry(root)
            registry.loadOrCreate()
            val owner = TdAccountNativeClientOwner(
                registry = registry,
                lifecycle = TdAccountClientLifecycle(root),
                router = TdAccountUpdateRouter(registry),
                configForAccount = { id ->
                    val dir = TdAccountStorageLayout(root).forAccount(id)
                    TdLibConfig(123, "test-hash-not-a-real-credential",
                        dir.database.absolutePath, dir.files.absolutePath,
                        "en", "Unit Test", "0.0.0.0")
                }
            )
            block(registry, owner)
        } finally { root.deleteRecursively() }
    }

    @Test fun twoClientsHaveDistinctDirectoriesAndNeverCrossDeliver() = withOwner { registry, owner ->
        val (_, bId) = registry.prepareSecondaryAccount()
        val received = mutableListOf<TdAccountUpdateRouter.TdAccountRoutedUpdate>()
        val primary = owner.create("legacy", { received.add(it) }, ::FakeClient)
        val secondary = owner.create(bId, { received.add(it) }, ::FakeClient)
        assertNotEquals(primary.config.databaseDirectory, secondary.config.databaseDirectory)
        assertNotEquals(primary.config.filesDirectory, secondary.config.filesDirectory)
        registry.selectAccount(bId)
        primary.emit(TdApi.Ok())
        secondary.emit(TdApi.Ok())
        assertEquals(listOf("legacy", bId), received.map { it.accountId })
        primary.close()
        secondary.close()
    }

    @Test fun closedCallbackRunsOnlyAfterItsDatabaseLeaseIsReleased() = withOwner { _, owner ->
        var successor: FakeClient? = null
        val first = owner.create("legacy", {},
            onClosed = { successor = owner.create("legacy", {}, ::FakeClient) },
            clientFactory = ::FakeClient
        )
        first.close()
        assertTrue(successor != null)
        successor!!.close()
    }

    @Test fun duplicateSameAccountCannotBeOpenedBeforeNativeClosed() = withOwner { _, owner ->
        val first = owner.create("legacy", {}, ::FakeClient)
        try {
            owner.create("legacy", {}, ::FakeClient)
            fail("Second native client opened the same database")
        } catch (_: IllegalStateException) { }
        first.emit(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateLoggingOut()))
        try {
            owner.create("legacy", {}, ::FakeClient)
            fail("LoggingOut prematurely released database")
        } catch (_: IllegalStateException) { }
        first.close()
        val replacement = owner.create("legacy", {}, ::FakeClient)
        first.close() // duplicate late close of previous generation
        try {
            owner.create("legacy", {}, ::FakeClient)
            fail("Stale close released replacement lease")
        } catch (_: IllegalStateException) { }
        replacement.close()
    }

    @Test fun exceptionInClosedConsumerCannotStrandAccountLease() = withOwner { _, owner ->
        val primary = owner.create("legacy", { throw IllegalArgumentException("handler failed") }, ::FakeClient)
        try {
            primary.close()
            fail("Expected consumer error")
        } catch (_: IllegalArgumentException) { }
        owner.create("legacy", {}, ::FakeClient).close()
    }

    @Test fun failureInNativeFactoryReleasesRouteAndDatabaseLease() = withOwner { _, owner ->
        try {
            owner.create("legacy", {}, { _, _ -> throw IllegalStateException("native create failed") })
            fail("Expected native factory failure")
        } catch (_: IllegalStateException) { }
        owner.create("legacy", {}, ::FakeClient).close()
    }

    @Test fun unregisteredAccountRejectedWithoutRunningFactory() = withOwner { _, owner ->
        var called = false
        try {
            owner.create("a-unknown", {}, { config, handler ->
                called = true
                FakeClient(config, handler)
            })
            fail("Unknown account must not start")
        } catch (_: IllegalArgumentException) { }
        assertFalse(called)
    }

    @Test fun wrongAccountStorageConfigurationIsRefusedBeforeNativeCreate() {
        val root = Files.createTempDirectory("beygram-native-config-").toFile()
        try {
            val registry = TdAccountRegistry(root)
            registry.prepareSecondaryAccount()
            val owner = TdAccountNativeClientOwner(
                registry, TdAccountClientLifecycle(root), TdAccountUpdateRouter(registry)
            ) { _ ->
                val legacy = TdAccountStorageLayout(root).forAccount("legacy")
                TdLibConfig(123, "fake", legacy.database.path, legacy.files.path, "en", "test", "1")
            }
            var factoryCalled = false
            val secondary = registry.loadOrCreate().accountIds.last()
            try {
                owner.create(secondary, {}, { config, callback ->
                    factoryCalled = true
                    FakeClient(config, callback)
                })
                fail("An account must never adopt the legacy database")
            } catch (_: IllegalArgumentException) { }
            assertFalse(factoryCalled)
            // The failed mismatch must not leak the other session's database lease.
            val proper = TdAccountNativeClientOwner(
                registry, TdAccountClientLifecycle(root), TdAccountUpdateRouter(registry)
            ) { id ->
                val d = TdAccountStorageLayout(root).forAccount(id)
                TdLibConfig(123, "fake", d.database.path, d.files.path, "en", "test", "1")
            }
            val client = proper.create(secondary, {}, ::FakeClient)
            assertTrue(client.config.databaseDirectory.contains(secondary))
            client.close()
        } finally { root.deleteRecursively() }
    }
}
