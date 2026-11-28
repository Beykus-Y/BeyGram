package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 815 gave every kept row the same "next" priority. The rows the player actually needs must keep their distinct
 * roles: N (playing), N+1 (the next deck), N+2 (bounded lookahead), N−1 (retained copy only).
 */
class LazyFetchRolesTest {
    private val TEST_TIMEOUT_MS = 30_000L

    private fun resolver(slots: Int, fetch: suspend (String) -> Result<String?>) =
        object : LazyTrackResolver<String>("role:", maxConcurrentFetches = slots) {
            override val restricted = false
            override fun pathOf(track: String): String = track
            override fun discardCopy(path: String) = Unit
            override fun handOverCopy(path: String) = Unit
            init {
                setFetch(fetch)
            }
        }

    @Test
    fun nPlusOneStartsBeforeNPlusTwoWhenOnlyOneSlotIsFree() = runBlocking {
        val started = Collections.synchronizedList(mutableListOf<String>())
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val nextStarted = CompletableDeferred<Unit>()
        val resolver = resolver(1) { key ->
            started += key
            when (key) {
                "role:current" -> {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                "role:next" -> nextStarted.complete(Unit)
            }
            Result.success("file-$key")
        }
        try {
            resolver.request("role:current")
            withTimeout(TEST_TIMEOUT_MS) { firstStarted.await() }
            // Both following rows are queued behind the playing one; N+1 must win even though N+2 was asked first.
            resolver.prefetch("role:ahead")
            resolver.prefetch("role:next")
            resolver.prioritize(
                LazyFetchWindow(playing = "role:current", next = listOf("role:next"), ahead = listOf("role:ahead"))
            )
            releaseFirst.complete(Unit)
            withTimeout(TEST_TIMEOUT_MS) { nextStarted.await() }
            assertEquals("role:next", started.toList().getOrNull(1))
        } finally {
            resolver.reset()
        }
    }

    @Test
    fun aRetainedPreviousRowDoesNotKeepADownloadSlot() = runBlocking {
        val previousStarted = CompletableDeferred<Unit>()
        val previousCancelled = CompletableDeferred<Unit>()
        val currentStarted = CompletableDeferred<Unit>()
        val resolver = resolver(1) { key ->
            when (key) {
                "role:previous" -> {
                    previousStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        previousCancelled.complete(Unit)
                    }
                }
                "role:current" -> currentStarted.complete(Unit)
            }
            Result.success("file-$key")
        }
        try {
            resolver.request("role:previous")
            withTimeout(TEST_TIMEOUT_MS) { previousStarted.await() }
            resolver.request("role:current")
            resolver.prioritize(
                LazyFetchWindow(playing = "role:current", retained = setOf("role:previous"))
            )
            // The previous row's copy is kept if it exists, but its unfinished download is not worth the only slot.
            withTimeout(TEST_TIMEOUT_MS) { previousCancelled.await() }
            withTimeout(TEST_TIMEOUT_MS) { currentStarted.await() }
        } finally {
            resolver.reset()
        }
    }

    @Test
    fun aPlayingRowPreemptsALowerLookaheadFetchWhenBothSlotsAreBusy() = runBlocking {
        val aheadStarted = CompletableDeferred<Unit>()
        val aheadCancelled = CompletableDeferred<Unit>()
        val nextStarted = CompletableDeferred<Unit>()
        val playingStarted = CompletableDeferred<Unit>()
        val resolver = resolver(2) { key ->
            when (key) {
                "role:ahead" -> {
                    aheadStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        aheadCancelled.complete(Unit)
                    }
                }
                "role:next" -> {
                    nextStarted.complete(Unit)
                    awaitCancellation()
                }
                "role:playing" -> playingStarted.complete(Unit)
            }
            Result.success("file-$key")
        }
        try {
            resolver.prioritize(
                LazyFetchWindow(next = listOf("role:next"), ahead = listOf("role:ahead"))
            )
            resolver.prefetchSoon("role:next")
            resolver.prefetch("role:ahead")
            withTimeout(TEST_TIMEOUT_MS) { nextStarted.await(); aheadStarted.await() }
            assertTrue("both lookahead fetches must run: ${resolver.fetchingKeys()}", resolver.fetchingKeys().containsAll(listOf("role:next", "role:ahead")))

            // The listener jumped to a row that is neither N+1 nor N+2: it must not wait behind both downloads.
            resolver.request("role:playing")
            resolver.prioritize(
                LazyFetchWindow(playing = "role:playing", next = listOf("role:next"), ahead = listOf("role:ahead"))
            )
            withTimeout(TEST_TIMEOUT_MS) { playingStarted.await() }
            withTimeout(TEST_TIMEOUT_MS) { aheadCancelled.await() }
            assertTrue(
                "N+1 must keep its slot; fetching=${resolver.fetchingKeys()} ready=${resolver.readyKeys()}",
                resolver.fetchingKeys().contains("role:next")
            )
        } finally {
            resolver.reset()
        }
    }

    @Test
    fun anActiveGrowingReaderIsNeverPreempted() = runBlocking {
        val readerStarted = CompletableDeferred<Unit>()
        val readerCancelled = CompletableDeferred<Unit>()
        val playingStarted = CompletableDeferred<Unit>()
        val resolver = resolver(1) { key ->
            when (key) {
                "role:reader" -> {
                    readerStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        readerCancelled.complete(Unit)
                    }
                }
                "role:playing" -> playingStarted.complete(Unit)
            }
            Result.success("file-$key")
        }
        try {
            resolver.request("role:reader")
            withTimeout(TEST_TIMEOUT_MS) { readerStarted.await() }
            resolver.published("role:reader")
            resolver.request("role:playing")
            resolver.prioritize(LazyFetchWindow(playing = "role:playing"))
            // The data source is reading the growing copy: the slot stays with it until the copy is complete.
            assertFalse(playingStarted.isCompleted)
            assertFalse(readerCancelled.isCompleted)
        } finally {
            resolver.reset()
        }
        withTimeout(TEST_TIMEOUT_MS) { readerCancelled.await() }
    }

    // --- roles computed from the real play order -------------------------------------------------

    private fun track(key: String) = PlaybackItem(
        key = key,
        kind = MediaKind.Audio,
        path = "",
        title = key,
        durationSeconds = 180,
        fileId = key.hashCode(),
    )

    @Test
    fun theWindowFollowsTheLinearQueue() {
        val queue = listOf(track("a"), track("b"), track("c"), track("d"), track("e"))
        val window = playbackFetchWindow(PlaybackState(item = queue[1], queue = queue, queueIndex = 1))
        assertEquals("b", window.playing)
        assertEquals(listOf("c"), window.next)
        assertEquals(listOf("d"), window.ahead)
        assertEquals(setOf("a"), window.retained)
    }

    @Test
    fun theWindowFollowsTheShuffledPlayOrder() {
        val queue = listOf(track("a"), track("b"), track("c"), track("d"))
        val window = playbackFetchWindow(
            PlaybackState(item = queue[2], queue = queue, queueIndex = 2, shuffleEnabled = true),
            playbackOrder = listOf(2, 0, 3, 1),
        )
        assertEquals("c", window.playing)
        assertEquals(listOf("a"), window.next)
        assertEquals(listOf("d"), window.ahead)
    }

    @Test
    fun repeatAllWrapsAndRepeatOneDoesNot() {
        val queue = listOf(track("a"), track("b"), track("c"))
        val wrap = playbackFetchWindow(
            PlaybackState(item = queue[2], queue = queue, queueIndex = 2, repeatMode = PlaybackRepeatMode.All)
        )
        assertEquals(listOf("a"), wrap.next)
        assertEquals(listOf("b"), wrap.ahead)

        val one = playbackFetchWindow(
            PlaybackState(item = queue[2], queue = queue, queueIndex = 2, repeatMode = PlaybackRepeatMode.One)
        )
        assertTrue(one.next.isEmpty())
        assertTrue(one.ahead.isEmpty())
    }

    @Test
    fun aSingleRowHasNoLookahead() {
        val queue = listOf(track("a"))
        val window = playbackFetchWindow(PlaybackState(item = queue[0], queue = queue, queueIndex = 0))
        assertEquals("a", window.playing)
        assertTrue(window.next.isEmpty())
        assertTrue(window.ahead.isEmpty())
    }

    @Test
    fun aRowOutsideTheWindowHasOnlySpeculativePriority() {
        val window = LazyFetchWindow(playing = "p", next = listOf("n"), ahead = listOf("a"), retained = setOf("r"))
        assertEquals(LazyFetchPriority.PLAYING, window.priorityOf("p"))
        assertEquals(LazyFetchPriority.NEXT, window.priorityOf("n"))
        assertEquals(LazyFetchPriority.AHEAD, window.priorityOf("a"))
        assertEquals(LazyFetchPriority.SPECULATIVE, window.priorityOf("r"))
        assertEquals(LazyFetchPriority.SPECULATIVE, window.priorityOf("other"))
        assertEquals(setOf("p", "n", "a"), window.wanted)
        assertFalse("r" in window.wanted)
    }
}