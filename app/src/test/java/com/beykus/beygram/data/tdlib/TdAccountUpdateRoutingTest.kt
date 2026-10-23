package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TdAccountUpdateRoutingTest {
    private fun awaitCondition(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue(description, condition())
    }

    private fun withRegistry(block: (TdAccountRegistry) -> Unit) {
        val dir = Files.createTempDirectory("beygram-routes-").toFile()
        try {
            val registry = TdAccountRegistry(dir)
            registry.loadOrCreate()
            block(registry)
        } finally { dir.deleteRecursively() }
    }

    @Test fun appRouterLockDoesNotBlockConcurrentSyntheticAccountCallbacks() = withRegistry { registry ->
        val first = registry.prepareSecondaryAccount().second
        val second = registry.prepareSecondaryAccount().second
        val router = TdAccountUpdateRouter(registry)
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val delivered = java.util.concurrent.CountDownLatch(1)
        val one = router.register(first) { entered.countDown(); release.await(5, java.util.concurrent.TimeUnit.SECONDS) }
        val two = router.register(second) { delivered.countDown() }
        val thread = Thread { one.updateHandler.onResult(TdApi.UpdateOption()) }
        thread.start()
        try {
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            two.updateHandler.onResult(TdApi.UpdateOption())
            assertTrue(delivered.await(2, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            release.countDown()
            thread.join(3_000)
        }
    }

    @Test fun distinctAccountsNeverMixSameChatMessageAndFileIds() = withRegistry { registry ->
        val (_, secondaryId) = registry.prepareSecondaryAccount()
        assertNotEquals(
            TdAccountChatKey("legacy", 42),
            TdAccountChatKey(secondaryId, 42)
        )
        assertNotEquals(
            TdAccountMessageKey("legacy", 42, 100),
            TdAccountMessageKey(secondaryId, 42, 100)
        )
        assertNotEquals(
            TdAccountFileKey("legacy", 7),
            TdAccountFileKey(secondaryId, 7)
        )
    }

    @Test fun callbacksRemainBoundToCreationAccountAfterActiveAccountSwitch() = withRegistry { registry ->
        val (_, secondaryId) = registry.prepareSecondaryAccount()
        val router = TdAccountUpdateRouter(registry)
        val legacy = java.util.concurrent.CopyOnWriteArrayList<TdAccountUpdateRouter.TdAccountRoutedUpdate>()
        val secondary = java.util.concurrent.CopyOnWriteArrayList<TdAccountUpdateRouter.TdAccountRoutedUpdate>()
        val original = router.register("legacy") { legacy.add(it) }
        val other = router.register(secondaryId) { secondary.add(it) }
        registry.selectAccount(secondaryId) // UI switches while the original client is alive
        original.updateHandler.onResult(TdApi.Ok())
        other.updateHandler.onResult(TdApi.Ok())
        awaitCondition("Both account updates delivered") { legacy.size == 1 && secondary.size == 1 }
        assertEquals(listOf("legacy"), legacy.map { it.accountId })
        assertEquals(listOf(secondaryId), secondary.map { it.accountId })
        assertNotEquals(original.generation, other.generation)
        assertEquals(setOf("legacy", secondaryId), router.registeredSessions())
        original.onNativeClosed()
        other.onNativeClosed()
        awaitCondition("Both workers retired") { router.registeredSessions().isEmpty() }
    }

    @Test fun closedGenerationCannotDeliverIntoReplacementClient() = withRegistry { registry ->
        val router = TdAccountUpdateRouter(registry)
        val firstEvents = java.util.concurrent.CopyOnWriteArrayList<TdAccountUpdateRouter.TdAccountRoutedUpdate>()
        val first = router.register("legacy") { firstEvents.add(it) }
        first.updateHandler.onResult(TdApi.Ok())
        first.updateHandler.onResult(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
        awaitCondition("Closed generation drained") {
            firstEvents.size == 2 && "legacy" !in router.registeredSessions()
        }
        assertEquals(2, firstEvents.size)
        assertFalse("legacy" in router.registeredSessions())

        val nextEvents = java.util.concurrent.CopyOnWriteArrayList<TdAccountUpdateRouter.TdAccountRoutedUpdate>()
        val next = router.register("legacy") { nextEvents.add(it) }
        first.updateHandler.onResult(TdApi.Ok())
        first.onNativeClosed() // late duplicate close must not detach a replacement
        next.updateHandler.onResult(TdApi.Ok())
        awaitCondition("Replacement receives update") { nextEvents.size == 1 }
        assertEquals(2, firstEvents.size)
        assertEquals(1, nextEvents.size)
        assertTrue(next.generation > first.generation)
        assertTrue("legacy" in router.registeredSessions())
        next.onNativeClosed()
        awaitCondition("Replacement worker retired") { router.registeredSessions().isEmpty() }
    }

    @Test fun duplicateAndUnknownAccountRegistrationIsRejected() = withRegistry { registry ->
        val router = TdAccountUpdateRouter(registry)
        try {
            router.register("a-not-in-registry") { }
            fail("Must refuse an unregistered account")
        } catch (_: IllegalArgumentException) { }
        val original = router.register("legacy") { }
        try {
            router.register("legacy") { }
            fail("Must not register two handlers for one account")
        } catch (_: IllegalStateException) { }
        original.onNativeClosed()
        awaitCondition("Owner retired") { router.registeredSessions().isEmpty() }
    }

    @Test fun closingOneAccountDoesNotUnregisterAnother() = withRegistry { registry ->
        val (_, second) = registry.prepareSecondaryAccount()
        val router = TdAccountUpdateRouter(registry)
        val legacyCount = java.util.concurrent.atomic.AtomicInteger()
        val otherCount = java.util.concurrent.atomic.AtomicInteger()
        val a = router.register("legacy") { legacyCount.incrementAndGet() }
        val b = router.register(second) { otherCount.incrementAndGet() }
        a.updateHandler.onResult(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
        b.updateHandler.onResult(TdApi.Ok())
        awaitCondition("Close and second update delivered") {
            legacyCount.get() == 1 && otherCount.get() == 1 &&
                router.registeredSessions() == setOf(second)
        }
        assertEquals(1, legacyCount.get())
        assertEquals(1, otherCount.get())
        assertEquals(setOf(second), router.registeredSessions())
        b.onNativeClosed()
        awaitCondition("Second worker retired") { router.registeredSessions().isEmpty() }
    }

    @Test fun terminalStateDetachesEvenIfDispatchThrows() = withRegistry { registry ->
        val router = TdAccountUpdateRouter(registry)
        val owner = router.register("legacy") { throw IllegalStateException("subscriber failed") }
        owner.updateHandler.onResult(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
        // Callback errors are logged by the worker and cannot strand the lease.
        awaitCondition("Worker retired after a callback exception") {
            router.registeredSessions().isEmpty()
        }
    }
    @Test fun sharedNativeCallbackThreadIsNotBlockedBySlowSecondaryAccount() = withRegistry { registry ->
        val firstId = registry.prepareSecondaryAccount().second
        val secondId = registry.prepareSecondaryAccount().second
        val router = TdAccountUpdateRouter(registry)
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val otherDelivered = java.util.concurrent.CountDownLatch(1)
        val first = router.register(firstId) {
            started.countDown()
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
        }
        val second = router.register(secondId) { otherDelivered.countDown() }
        try {
            // Both synthetic calls deliberately run on the SAME native caller thread.
            // The first must return before its slow repository callback completes.
            first.updateHandler.onResult(TdApi.Ok())
            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS))
            second.updateHandler.onResult(TdApi.Ok())
            assertTrue(otherDelivered.await(2, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            release.countDown()
            first.onNativeClosed()
            second.onNativeClosed()
            awaitCondition("Account workers shut down") { router.registeredSessions().isEmpty() }
        }
    }

    @Test fun closeWaitsForAllQueuedAccountUpdatesBeforeLeaseRetirement() = withRegistry { registry ->
        val router = TdAccountUpdateRouter(registry)
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val processed = java.util.concurrent.CopyOnWriteArrayList<Int>()
        val retired = java.util.concurrent.CountDownLatch(1)
        val registration = router.register("legacy", onAfterClosed = { retired.countDown() }) {
            processed.add(processed.size + 1)
            if (processed.size == 1) {
                started.countDown()
                release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
        try {
            registration.updateHandler.onResult(TdApi.Ok())
            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS))
            registration.updateHandler.onResult(TdApi.Ok())
            registration.updateHandler.onResult(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
            assertTrue("Must retain lease while update processing is blocked",
                "legacy" in router.registeredSessions())
            assertEquals(1L, retired.count)
        } finally {
            release.countDown()
        }
        assertTrue("Retirement must follow queued updates",
            retired.await(3, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(listOf(1, 2, 3), processed.toList())
        assertTrue(router.registeredSessions().isEmpty())
    }

}
