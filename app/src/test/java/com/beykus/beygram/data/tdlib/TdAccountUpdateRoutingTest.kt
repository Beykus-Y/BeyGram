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
    private fun withRegistry(block: (TdAccountRegistry) -> Unit) {
        val dir = Files.createTempDirectory("beygram-routes-").toFile()
        try {
            val registry = TdAccountRegistry(dir)
            registry.loadOrCreate()
            block(registry)
        } finally { dir.deleteRecursively() }
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
        val legacy = mutableListOf<TdAccountUpdateRouter.TdAccountRoutedUpdate>()
        val secondary = mutableListOf<TdAccountUpdateRouter.TdAccountRoutedUpdate>()
        val original = router.register("legacy") { legacy.add(it) }
        val other = router.register(secondaryId) { secondary.add(it) }
        registry.selectAccount(secondaryId) // UI switches while the original client is alive
        original.updateHandler.onResult(TdApi.Ok())
        other.updateHandler.onResult(TdApi.Ok())
        assertEquals(listOf("legacy"), legacy.map { it.accountId })
        assertEquals(listOf(secondaryId), secondary.map { it.accountId })
        assertNotEquals(original.generation, other.generation)
        assertEquals(setOf("legacy", secondaryId), router.registeredSessions())
        original.onNativeClosed()
        other.onNativeClosed()
    }

    @Test fun closedGenerationCannotDeliverIntoReplacementClient() = withRegistry { registry ->
        val router = TdAccountUpdateRouter(registry)
        val firstEvents = mutableListOf<TdAccountUpdateRouter.TdAccountRoutedUpdate>()
        val first = router.register("legacy") { firstEvents.add(it) }
        first.updateHandler.onResult(TdApi.Ok())
        first.updateHandler.onResult(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
        assertEquals(2, firstEvents.size)
        assertFalse("legacy" in router.registeredSessions())

        val nextEvents = mutableListOf<TdAccountUpdateRouter.TdAccountRoutedUpdate>()
        val next = router.register("legacy") { nextEvents.add(it) }
        first.updateHandler.onResult(TdApi.Ok())
        first.onNativeClosed() // late duplicate close must not detach a replacement
        next.updateHandler.onResult(TdApi.Ok())
        assertEquals(2, firstEvents.size)
        assertEquals(1, nextEvents.size)
        assertTrue(next.generation > first.generation)
        assertTrue("legacy" in router.registeredSessions())
        next.onNativeClosed()
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
        assertTrue(router.registeredSessions().isEmpty())
    }

    @Test fun closingOneAccountDoesNotUnregisterAnother() = withRegistry { registry ->
        val (_, second) = registry.prepareSecondaryAccount()
        val router = TdAccountUpdateRouter(registry)
        var legacyCount = 0
        var otherCount = 0
        val a = router.register("legacy") { legacyCount++ }
        val b = router.register(second) { otherCount++ }
        a.updateHandler.onResult(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
        b.updateHandler.onResult(TdApi.Ok())
        assertEquals(1, legacyCount)
        assertEquals(1, otherCount)
        assertEquals(setOf(second), router.registeredSessions())
        b.onNativeClosed()
    }

    @Test fun terminalStateDetachesEvenIfDispatchThrows() = withRegistry { registry ->
        val router = TdAccountUpdateRouter(registry)
        val owner = router.register("legacy") { throw IllegalStateException("subscriber failed") }
        try {
            owner.updateHandler.onResult(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
            fail("Expected handler failure")
        } catch (_: IllegalStateException) { }
        assertTrue(router.registeredSessions().isEmpty())
    }
}
