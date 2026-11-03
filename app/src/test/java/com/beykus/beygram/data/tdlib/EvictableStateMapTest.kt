package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EvictableStateMapTest {
    private class Harness(maxEntries: Int, val isBusy: (Int) -> Boolean = { false }) {
        val lock = Any()
        val evicted = mutableListOf<Int>()
        val created = mutableListOf<Int>()
        val map = EvictableStateMap<Int, String>(
            lock = lock,
            maxEntries = maxEntries,
            seed = { "seed$it" },
            isIdle = { key, _ -> !isBusy(key) },
            onEvicted = { evicted += it },
            onCreatedByRead = { created += it }
        )

        fun write(key: Int, value: String) = synchronized(lock) { map.stateLocked(key).value = value }
    }

    private fun flood(harness: Harness, from: Int, count: Int = 100) {
        for (key in from until from + count) harness.map.read(key)
    }

    @Test fun massCreationStaysWithinTheLimitAndKeepsTheNewestEntry() {
        val harness = Harness(maxEntries = 8)
        flood(harness, from = 1, count = 1_000)
        assertTrue(harness.map.size() <= 8)
        assertEquals(1_000, harness.map.size() + harness.evicted.size)
        assertTrue(harness.map.contains(1_000))
    }

    @Test fun busyEntriesAreNeverDroppedEvenPastTheLimit() {
        val harness = Harness(maxEntries = 8, isBusy = { it % 2 == 1 })
        flood(harness, from = 1, count = 100)
        for (key in 1..99 step 2) assertTrue("busy key $key was dropped", harness.map.contains(key))
        assertTrue((2..100 step 2).count { harness.map.contains(it) } <= 8)
    }

    @Test fun aCollectedEntryIsKeptUntilTheCollectionEnds() = runBlocking {
        val harness = Harness(maxEntries = 8)
        val handle = harness.map.observe(1)
        val collection = launch(Dispatchers.Unconfined) { handle.collect { } }
        flood(harness, from = 2, count = 200)
        assertTrue(harness.map.contains(1))
        collection.cancelAndJoin()
        flood(harness, from = 300, count = 200)
        assertFalse(harness.map.contains(1))
    }

    @Test fun aDroppedEntryIsRecreatedFromItsSeedAndTheHandleFollowsIt() {
        val harness = Harness(maxEntries = 8)
        val handle = harness.map.observe(1)
        harness.write(1, "written")
        assertEquals("written", handle.value)
        flood(harness, from = 100)
        assertFalse(harness.map.contains(1))
        assertEquals("seed1", handle.value)
        harness.write(1, "later")
        assertEquals("later", handle.value)
    }

    @Test fun aCollectionStartedAfterADropReceivesTheRecreatedEntry() = runBlocking {
        val harness = Harness(maxEntries = 8)
        val handle = harness.map.observe(1)
        val seen = mutableListOf<String>()
        val first = launch(Dispatchers.Unconfined) { handle.collect { seen += it } }
        first.cancelAndJoin()
        flood(harness, from = 100)
        assertFalse(harness.map.contains(1))
        val second = launch(Dispatchers.Unconfined) { handle.collect { seen += it } }
        harness.write(1, "fresh")
        second.cancelAndJoin()
        assertEquals("fresh", seen.last())
    }

    @Test fun readsAndCollectionsReportCreationButWritersDoNot() = runBlocking {
        val harness = Harness(maxEntries = 8)
        harness.map.read(1)
        harness.write(2, "written")
        assertEquals(listOf(1), harness.created)
        val collection = launch(Dispatchers.Unconfined) { harness.map.observe(3).collect { } }
        collection.cancelAndJoin()
        assertEquals(listOf(1, 3), harness.created)
        harness.map.read(2)
        assertEquals(listOf(1, 3), harness.created)
    }

    @Test fun peekDoesNotCreateAnEntry() {
        val harness = Harness(maxEntries = 8)
        val value = synchronized(harness.lock) { harness.map.peekLocked(5) }
        assertEquals(null, value)
        assertFalse(harness.map.contains(5))
        assertTrue(harness.created.isEmpty())
    }

    @Test fun limitBelowFourIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Harness(maxEntries = 3) }
    }

    @Test fun anIdleEntryIsDroppedAtOnceAndABusyOneIsNot() {
        val harness = Harness(maxEntries = 8, isBusy = { it == 2 })
        harness.map.read(1)
        harness.map.read(2)
        synchronized(harness.lock) {
            harness.map.removeIfIdleLocked(1)
            harness.map.removeIfIdleLocked(2)
        }
        assertFalse(harness.map.contains(1))
        assertTrue(harness.map.contains(2))
        assertEquals(listOf(1), harness.evicted)
    }

    @Test fun aCollectedEntryIsDroppedOnceTheCollectionEnds() = runBlocking {
        val harness = Harness(maxEntries = 8)
        val handle = harness.map.observe(1)
        val collection = launch(Dispatchers.Unconfined) { handle.collect { } }
        synchronized(harness.lock) { harness.map.removeIfIdleLocked(1) }
        assertTrue(harness.map.contains(1))
        collection.cancelAndJoin()
        synchronized(harness.lock) { harness.map.removeIfIdleLocked(1) }
        assertFalse(harness.map.contains(1))
    }

    @Test fun aCollectionWhoseStartFailsDoesNotPinItsEntry() = runBlocking {
        val lock = Any()
        val map = EvictableStateMap<Int, String>(
            lock = lock,
            maxEntries = 8,
            seed = { "seed" },
            isIdle = { _, _ -> true },
            onCreatedByRead = { error("refresh failed") }
        )
        val failure = runCatching { map.observe(1).collect { } }
        assertTrue(failure.isFailure)
        synchronized(lock) { map.removeIfIdleLocked(1) }
        assertFalse(map.contains(1))
    }
}
