package com.beykus.beygram.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserRangeCoordinatorTest {
    private class FakeRanges : PlaybackRanges {
        val readable = HashMap<Long, Long>()
        val requests = mutableListOf<Triple<Long, Long, Boolean>>()
        val cancelled = mutableListOf<Int>()
        /** When set, the next availability queries wait for it, which models a request in flight. */
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        override suspend fun readableFrom(fileId: Int, offset: Long): Long {
            gate?.await()
            return readable[offset] ?: 0L
        }

        override fun requestWindow(fileId: Int, offset: Long, limit: Long, urgent: Boolean) {
            requests += Triple(offset, limit, urgent)
        }

        override fun cancelWindow(fileId: Int) {
            cancelled += fileId
        }
    }

    private var now = 0L

    private fun coordinator(ranges: FakeRanges) = UserRangeCoordinator(
        ranges = ranges,
        clock = { now },
        retryBackoffMs = 1_000L,
        maxAttemptsWithoutProgress = 3,
    )

    @Test
    fun `the first read asks for a window at the requested offset`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        val available = subject.ensureReadable(fileId = 7, offset = 0L, needed = 64 * 1024L, urgent = true)

        assertEquals(0L, available)
        assertEquals(listOf(Triple(0L, 0L, true)), ranges.requests)
    }

    @Test
    fun `a pending unlimited window is not requested twice`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        subject.ensureReadable(7, 4096L, 1024L, urgent = true)

        assertEquals(1, ranges.requests.size)
    }

    @Test
    fun `a seek outside a bounded tail window replaces it`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.ensureTail(fileId = 9, start = 1_000_000L, length = 65_536L)
        // The bounded tail cannot serve a read at the start of the file.
        subject.ensureReadable(9, 0L, 4096L, urgent = true)

        assertEquals(2, ranges.requests.size)
        assertEquals(Triple(0L, 0L, true), ranges.requests.last())
    }

    @Test
    fun `a covered read does not request anything`() = runTest {
        val ranges = FakeRanges().apply { readable[1_000_000L] = 65_536L }
        val subject = coordinator(ranges)

        val available = subject.ensureTail(9, 1_000_000L, 65_536L)

        assertEquals(65_536L, available)
        assertTrue(ranges.requests.isEmpty())
    }

    @Test
    fun `a background preload never replaces an active user window`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        // A neighbouring page tries to preload at another offset while the user is watching.
        subject.ensureReadable(7, 500_000L, 1024L, urgent = false)

        assertEquals(1, ranges.requests.size)
        assertEquals(Triple(0L, 0L, true), ranges.requests.single())
    }

    @Test
    fun `a user seek is allowed to replace the background window`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.ensureReadable(7, 10_000L, 1024L, urgent = false)
        subject.ensureReadable(7, 10_000L, 1024L, urgent = true)

        assertEquals(2, ranges.requests.size)
        assertEquals(Triple(10_000L, 0L, true), ranges.requests.last())
    }

    @Test
    fun `requests without progress back off and then resume`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        // Three bounded tail windows, none of which yields bytes.
        subject.ensureTail(7, 0L, 1_000L)
        subject.ensureTail(7, 2_000L, 1_000L)
        subject.ensureTail(7, 4_000L, 1_000L)
        assertEquals(3, ranges.requests.size)

        // Immediately after that the coordinator waits instead of restarting the download.
        now = 10L
        subject.ensureTail(7, 6_000L, 1_000L)
        assertEquals(3, ranges.requests.size)

        // After the backoff it tries once more.
        now = 2_000L
        subject.ensureTail(7, 6_000L, 1_000L)
        assertEquals(4, ranges.requests.size)
    }

    @Test
    fun `a stalled window is requested again after the deadline`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        assertEquals(1, ranges.requests.size)

        // Nothing arrived for longer than the window deadline: the same offset must be asked for again instead of
        // being treated as "already pending" forever (Codex blocker on #1188).
        now = 6_000L
        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        assertEquals(2, ranges.requests.size)
        assertEquals(Triple(0L, 0L, true), ranges.requests.last())
    }

    @Test
    fun `a rejected tail window is requested again after the deadline`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.ensureTail(9, 1_000_000L, 65_536L)
        assertEquals(1, ranges.requests.size)

        now = 6_000L
        subject.ensureTail(9, 1_000_000L, 65_536L)
        assertEquals(2, ranges.requests.size)
        assertEquals(Triple(1_000_000L, 65_536L, true), ranges.requests.last())
    }

    @Test
    fun `a window that keeps delivering is not restarted`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        // Bytes arrive inside the deadline, then the next read is requested: same window, no new request.
        ranges.readable[0L] = 512L
        now = 3_000L
        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        ranges.readable[0L] = 900L
        now = 5_000L
        subject.ensureReadable(7, 0L, 1024L, urgent = true)

        assertEquals(1, ranges.requests.size)
    }

    @Test
    fun `progress at another offset does not extend the window of this offset`() = runTest {
        // Codex review of #1188: lastAvailable/lastProgressAt were per file, so polling a different offset looked like
        // progress for the window at offset 0 and kept a stalled download alive forever.
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        assertEquals(1, ranges.requests.size)

        // Data exists ahead of the file: a neighbouring read of the same file sees bytes...
        ranges.readable[4_096L] = 8_192L
        now = 3_000L
        subject.ensureReadable(7, 4_096L, 1024L, urgent = true)
        // ...but the window at offset 0 still delivers nothing.
        now = 5_500L
        subject.ensureReadable(7, 0L, 1024L, urgent = true)

        // The stalled window must be requested again instead of being extended by the other offset's bytes.
        assertEquals(2, ranges.requests.size)
        assertEquals(Triple(0L, 0L, true), ranges.requests.last())
    }

    @Test
    fun `a page closed during a pending query never sends a window request`() = runTest {
        val ranges = FakeRanges()
        val gate = CompletableDeferred<Unit>()
        ranges.gate = gate
        val subject = coordinator(ranges)

        val query = launch { subject.ensureReadable(7, 0L, 1_024L, urgent = true) }
        // Let the reader reach the suspended query; otherwise reset would happen before the coroutine even starts.
        runCurrent()
        // The reader is still waiting for TDLib when the viewer closes.
        subject.reset(7)
        gate.complete(Unit)
        query.join()

        assertTrue("no window may be requested for a closed page", ranges.requests.isEmpty())
    }

    /**
     * The reader and the disposing screen really are different threads (Codex review of #1195): the page scope asks for
     * bytes while `DisposableEffect.onDispose` calls [UserRangeCoordinator.reset]. The ownership check, the state update
     * and `requestWindow` must therefore be one atomic step; otherwise `DownloadFile` can be sent for a page that has
     * already been closed and already had its window cancelled.
     *
     * A fake that stamps every request with `System.nanoTime()` makes the ordering checkable: a request that started
     * after `reset` returned belongs to nobody.
     */
    /**
     * The reader and the disposing screen really are different threads (Codex review of #1195): the page scope asks for
     * bytes while `DisposableEffect.onDispose` calls [UserRangeCoordinator.reset]. The ownership check, the state update
     * and `requestWindow` must therefore be one atomic step; otherwise `DownloadFile` can be sent for a page that has
     * already been closed and already had its window cancelled.
     *
     * A fake that stamps every request with `System.nanoTime()` makes the ordering checkable: the reader is released
     * from its query at the same moment the closing thread resets, and a request that starts after `reset` returned
     * belongs to nobody.
     */
    /**
     * The reader and the disposing screen really are different threads (Codex review of #1195): the page scope asks for
     * bytes while `DisposableEffect.onDispose` calls [UserRangeCoordinator.reset]. The ownership check, the state update
     * and `requestWindow` must therefore be one atomic step; otherwise `DownloadFile` can be sent for a page that has
     * already been closed and already had its window cancelled.
     *
     * A fake that stamps every request with `System.nanoTime()` makes the ordering checkable: the reader is released
     * from its query at the same moment the closing thread resets, and a request that starts after `reset` returned
     * belongs to nobody.
     */
    @Test
    fun `a request cannot slip past a reset from another thread`() {
        repeat(500) {
            val requestTimes = java.util.Collections.synchronizedList(ArrayList<Long>())
            val entered = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            val ranges = object : PlaybackRanges {
                override suspend fun readableFrom(fileId: Int, offset: Long): Long {
                    entered.countDown()
                    release.await()
                    return 0L
                }

                override fun requestWindow(fileId: Int, offset: Long, limit: Long, urgent: Boolean) {
                    requestTimes += System.nanoTime()
                }

                override fun cancelWindow(fileId: Int) = Unit
            }
            val subject = UserRangeCoordinator(ranges)

            val reader = Thread {
                kotlinx.coroutines.runBlocking { subject.ensureReadable(7, 0L, 1_024L, urgent = true) }
            }
            reader.start()
            // The reader owns its state and is suspended inside the TDLib query when the page closes.
            assertTrue(ranges.let { entered.await(10, java.util.concurrent.TimeUnit.SECONDS) })
            release.countDown()
            subject.reset(7)
            val closedAt = System.nanoTime()
            reader.join(10_000)

            assertTrue("the reader thread must finish", !reader.isAlive)
            val late = synchronized(requestTimes) { requestTimes.filter { it > closedAt } }
            assertTrue("a window was requested after the page closed: $late", late.isEmpty())
        }
    }

    @Test
    fun `a request from a page closed before it reached the coordinator is ignored`() = runTest {
        // Codex review of #1195: reset() ran before the Media3 loader entered ensureReadable, so the late call created a
        // fresh state and requested a window for a closed page. The page's token makes that call a no-op.
        val ranges = FakeRanges()
        val subject = coordinator(ranges)
        val closed = subject.owner(7)

        subject.reset(7, closed)
        val available = subject.ensureReadable(7, 0L, 1_024L, urgent = true, owner = closed)

        assertEquals(0L, available)
        assertTrue("no window may be requested for a closed page", ranges.requests.isEmpty())
        assertTrue(ranges.cancelled.isEmpty())
    }

    @Test
    fun `a reopened page can request again`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)
        val first = subject.owner(7)
        subject.reset(7, first)

        val second = subject.owner(7)
        subject.ensureReadable(7, 0L, 1_024L, urgent = true, owner = second)

        assertEquals(1, ranges.requests.size)
    }

    @Test
    fun `a late dispose of the previous page does not cancel the current one`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)
        val previous = subject.owner(7)
        val current = subject.owner(7)

        subject.ensureReadable(7, 0L, 1_024L, urgent = true, owner = current)
        assertEquals(1, ranges.requests.size)

        // The old viewer finishes disposing after the new one started: it must not take the window away.
        subject.reset(7, previous)
        assertTrue("the current page keeps its window", ranges.cancelled.isEmpty())
        assertEquals(1, ranges.requests.size)

        subject.reset(7, current)
        assertEquals(listOf(7), ranges.cancelled)
    }

    @Test
    fun `two viewers of the same file do not cancel each other`() = runTest {
        // A bubble in the timeline and the full-screen viewer can show the same video at once (Codex review of #1195).
        val ranges = FakeRanges()
        val subject = coordinator(ranges)
        val bubble = subject.owner(7)
        val viewer = subject.owner(7)

        subject.ensureReadable(7, 0L, 1_024L, urgent = true, owner = bubble)
        subject.ensureReadable(7, 5_000_000L, 1_024L, urgent = true, owner = viewer)
        assertEquals(2, ranges.requests.size)

        // Closing the bubble must not take the window away from the viewer that is still on screen.
        subject.reset(7, bubble)
        assertTrue(ranges.cancelled.isEmpty())
        subject.ensureReadable(7, 5_000_000L, 1_024L, urgent = true, owner = viewer)
        assertEquals(2, ranges.requests.size)

        subject.reset(7, viewer)
        assertEquals(listOf(7), ranges.cancelled)
    }

    @Test
    fun `a cancelled query propagates instead of being treated as zero bytes`() = runTest {
        val ranges = object : PlaybackRanges {
            override suspend fun readableFrom(fileId: Int, offset: Long): Long =
                throw kotlinx.coroutines.CancellationException("closed")

            override fun requestWindow(fileId: Int, offset: Long, limit: Long, urgent: Boolean) {
                error("a cancelled query must not request a window")
            }

            override fun cancelWindow(fileId: Int) = Unit
        }
        val subject = UserRangeCoordinator(ranges, clock = { now })

        var cancelled = false
        try {
            subject.ensureReadable(7, 0L, 1_024L, urgent = true)
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun `a stalled user window still keeps its priority`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        now = 6_000L
        // A background preload must not steal the window even while it is stalled.
        subject.ensureReadable(7, 0L, 1024L, urgent = false)
        assertEquals(1, ranges.requests.size)

        // The user's own retry is allowed and keeps the watching priority.
        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        assertEquals(2, ranges.requests.size)
        assertTrue(ranges.requests.last().third)
    }

    @Test
    fun `progress at the window offset restores the retry budget`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.ensureTail(7, 0L, 1_000L)
        assertEquals(1, ranges.requests.size)

        // Bytes arrive at the window's own offset: the download is alive, so the retry budget is restored and a new
        // offset may be requested without waiting for the backoff.
        ranges.readable[0L] = 512L
        now = 10L
        subject.ensureTail(7, 0L, 1_000L)
        subject.ensureTail(7, 5_000L, 1_000L)
        assertEquals(2, ranges.requests.size)
        assertEquals(Triple(5_000L, 1_000L, true), ranges.requests.last())
    }

    @Test
    fun `completion forgets the window and a later read starts a new one`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        subject.onCompleted(7)
        subject.ensureReadable(7, 0L, 1024L, urgent = true)

        assertEquals(2, ranges.requests.size)
        // Completion does not cancel a finished download.
        assertTrue(ranges.cancelled.isEmpty())
    }

    @Test
    fun `reset cancels only a file that had a window`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.reset(7)
        assertTrue(ranges.cancelled.isEmpty())

        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        subject.reset(7)
        assertEquals(listOf(7), ranges.cancelled)
    }

    @Test
    fun `resetAll cancels every tracked file`() = runTest {
        val ranges = FakeRanges()
        val subject = coordinator(ranges)

        subject.ensureReadable(7, 0L, 1024L, urgent = true)
        subject.ensureReadable(8, 0L, 1024L, urgent = false)
        subject.resetAll()

        assertEquals(setOf(7, 8), ranges.cancelled.toSet())
    }

    @Test
    fun `a bounded window covers only its own range`() {
        val window = RangeWindow(offset = 1_000L, limit = 500L, urgent = true)
        assertTrue(window.covers(1_000L, 500L))
        assertTrue(window.covers(1_200L, 100L))
        assertTrue(!window.covers(900L, 200L))
        assertTrue(!window.covers(1_400L, 200L))
        assertTrue(RangeWindow(0L, 0L, true).covers(0L, Long.MAX_VALUE / 2))
    }
}