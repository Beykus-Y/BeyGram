package com.beykus.beygram.playback

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The downloader has a fixed number of slots. Rapid Next/Previous used to leave earlier playback requests holding both
 * slots while the newly selected row waited. These tests pin the priority/ownership rules that fix it.
 */
class LazyTrackResolverPriorityTest {
    /** Generous: CI runs thousands of tests in parallel, and the assertions do not depend on the wall time. */
    private val TEST_TIMEOUT_MS = 30_000L
    private fun resolver(slots: Int, fetch: suspend (String) -> Result<String?>) =
        object : LazyTrackResolver<String>("fake:", maxConcurrentFetches = slots) {
            override val restricted = false
            override fun pathOf(track: String): String = track
            override fun discardCopy(path: String) = Unit
            override fun handOverCopy(path: String) = Unit
            init {
                setFetch(fetch)
            }
        }

    @Test
    fun aPromotedWaitingFetchStartsBeforeAnOlderPrefetch() = runBlocking {
        val started = Collections.synchronizedList(mutableListOf<String>())
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val selectedStarted = CompletableDeferred<Unit>()
        val resolver = resolver(1) { key ->
            started += key
            when (key) {
                "fake:a" -> {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                "fake:c" -> selectedStarted.complete(Unit)
            }
            Result.success("file-$key")
        }
        try {
            resolver.prefetch("fake:a")
            withTimeout(TEST_TIMEOUT_MS) { firstStarted.await() }
            // b and c are queued behind the only slot; c is the row the player is opening now.
            resolver.prefetch("fake:b")
            resolver.prefetch("fake:c")
            resolver.request("fake:c")
            releaseFirst.complete(Unit)
            withTimeout(TEST_TIMEOUT_MS) { selectedStarted.await() }
            assertEquals("fake:c", started.toList().getOrNull(1))
        } finally {
            resolver.reset()
        }
    }

    @Test
    fun stalePlaybackRequestsFreeTheirSlotsForTheSelectedRow() = runBlocking {
        val startedA = CompletableDeferred<Unit>()
        val startedB = CompletableDeferred<Unit>()
        val selectedStarted = CompletableDeferred<Unit>()
        val cancelledA = CompletableDeferred<Unit>()
        val cancelledB = CompletableDeferred<Unit>()
        val resolver = resolver(2) { key ->
            when (key) {
                "fake:a" -> startedA.complete(Unit)
                "fake:b" -> startedB.complete(Unit)
                "fake:c" -> selectedStarted.complete(Unit)
            }
            try {
                awaitCancellation()
            } finally {
                when (key) {
                    "fake:a" -> cancelledA.complete(Unit)
                    "fake:b" -> cancelledB.complete(Unit)
                }
            }
        }
        try {
            // A and B were playback requests, but the user moved on before either opened.
            resolver.request("fake:a")
            resolver.request("fake:b")
            withTimeout(TEST_TIMEOUT_MS) { startedA.await(); startedB.await() }
            resolver.request("fake:c")
            assertFalse(selectedStarted.isCompleted)
            // The window now contains only C: nobody reads A or B, so their slots belong to the selected row.
            resolver.prioritize(LazyFetchWindow(playing = "fake:c"))
            withTimeout(TEST_TIMEOUT_MS) { cancelledA.await(); cancelledB.await() }
            withTimeout(TEST_TIMEOUT_MS) { selectedStarted.await() }
            assertTrue(resolver.fetchingKeys().contains("fake:c"))
        } finally {
            resolver.reset()
        }
    }

    @Test
    fun aGrowingReaderIsNotCancelledByANewerPlaybackRequest() = runBlocking {
        val startedA = CompletableDeferred<Unit>()
        val cancelledA = CompletableDeferred<Unit>()
        val resolver = resolver(1) { _ ->
            startedA.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelledA.complete(Unit)
            }
        }
        try {
            resolver.request("fake:a")
            withTimeout(TEST_TIMEOUT_MS) { startedA.await() }
            // The data source already opened the growing copy: it is an active reader, not a stale request.
            resolver.published("fake:a")
            resolver.prioritize(LazyFetchWindow(playing = "fake:c"))
            assertFalse(cancelledA.isCompleted)
            assertTrue(resolver.fetchingKeys().contains("fake:a"))
        } finally {
            resolver.reset()
        }
        withTimeout(TEST_TIMEOUT_MS) { cancelledA.await() }
    }

    @Test
    fun completedCopiesAreLeftToTheEvictionPolicy() = runBlocking {
        val done = CompletableDeferred<Unit>()
        val resolver = resolver(1) { key ->
            done.complete(Unit)
            Result.success("file-$key")
        }
        try {
            resolver.request("fake:a")
            withTimeout(TEST_TIMEOUT_MS) { done.await() }
            withTimeout(TEST_TIMEOUT_MS) {
                while (!resolver.readyKeys().contains("fake:a")) delay(5)
            }
            // A completed file may be needed again by Previous; only the bounded eviction policy may drop it.
            resolver.prioritize(LazyFetchWindow(playing = "fake:c"))
            assertTrue(resolver.readyKeys().contains("fake:a"))
        } finally {
            resolver.reset()
        }
    }
}
