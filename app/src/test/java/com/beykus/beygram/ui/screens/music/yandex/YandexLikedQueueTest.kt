package com.beykus.beygram.ui.screens.music.yandex

import com.beykus.beygram.data.music.provider.ProviderTrack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexLikedQueueTest {

    private fun track(id: String, available: Boolean = true) = ProviderTrack(
        trackId = id,
        title = "Track $id",
        artists = listOf("Artist"),
        albumTitle = null,
        durationMs = 180_000L,
        coverUrl = null,
        isAvailable = available,
    )

    /**
     * Stands in for the playback queue: a start makes a new generation and replaces the queue, and tracks join only the
     * queue they were started for. Queue keys are the track ids here.
     */
    private class FakeQueue {
        var generation = 0L
            private set
        var queue: List<String> = emptyList()
            private set
        val appended = mutableListOf<String>()

        val current: String? get() = queue.firstOrNull()

        /** Starts a queue with [ids], the first one playing; reports its keys and the generation the start made. */
        fun start(ids: List<String>): Pair<List<String>, Long> {
            generation++
            queue = ids
            return ids to generation
        }

        /** Appends [rest] only while the queue is still the one started at [startedAt]. */
        fun append(startedAt: Long, rest: List<ProviderTrack>) {
            if (startedAt != generation) return
            val ids = rest.map { it.trackId }
            appended += ids
            queue = queue + ids
        }
    }

    @Test
    fun restKeepsListOrderAndLeavesOutQueuedAndUnavailableTracks() {
        val full = listOf(track("1"), track("2"), track("3", available = false), track("4"), track("5"))
        assertEquals(listOf("5"), likedRestAfter(full, "2", setOf("2", "4")).map { it.trackId })
        assertEquals(listOf("4", "5"), likedRestAfter(full, "2", setOf("2")).map { it.trackId })
        assertEquals(emptyList<String>(), likedRestAfter(full, "9", emptySet()).map { it.trackId })
    }

    @Test
    fun tappedTrackStartsWhileTheWholeListIsStillLoading() = runTest {
        // The provider has not answered for the rest of the list.
        val wholeList = CompletableDeferred<List<ProviderTrack>?>()
        val queue = FakeQueue()
        val tap = launch {
            playLikedTap(
                tappedId = "2",
                headKeys = listOf("2", "3"),
                queued = setOf("2", "3"),
                startNow = { queue.start(listOf("2", "3")) },
                loadAll = { wholeList.await() },
                appendRest = { generation, rest -> queue.append(generation, rest) },
            )
        }
        advanceUntilIdle()
        assertEquals("2", queue.current)
        assertFalse(tap.isCompleted)

        wholeList.complete(listOf(track("1"), track("2"), track("3"), track("4")))
        advanceUntilIdle()
        assertEquals(listOf("4"), queue.appended)
        assertTrue(tap.isCompleted)
    }

    @Test
    fun aStartThatLeavesAnotherQueuePlayingAppendsNothing() = runTest {
        // A track already playing in BG Mix keeps its own queue: the tap starts nothing of the liked list.
        val queue = FakeQueue()
        queue.start(listOf("9", "2"))
        var wholeListAsked = false
        playLikedTap(
            tappedId = "2",
            headKeys = listOf("2", "3"),
            queued = setOf("2", "3"),
            startNow = { listOf("9", "2") to queue.generation },
            loadAll = {
                wholeListAsked = true
                listOf(track("1"), track("2"), track("3"), track("4"))
            },
            appendRest = { generation, rest -> queue.append(generation, rest) },
        )
        assertFalse(wholeListAsked)
        assertEquals(emptyList<String>(), queue.appended)
        assertEquals(listOf("9", "2"), queue.queue)
    }

    @Test
    fun anEarlierTapCannotExtendTheQueueThatReplacedIt() = runTest {
        val wholeList = CompletableDeferred<List<ProviderTrack>?>()
        val queue = FakeQueue()
        val earlier = launch {
            playLikedTap(
                tappedId = "2",
                headKeys = listOf("2"),
                queued = setOf("2"),
                startNow = { queue.start(listOf("2")) },
                loadAll = { wholeList.await() },
                appendRest = { generation, rest -> queue.append(generation, rest) },
            )
        }
        advanceUntilIdle()
        // Another list starts while the earlier tap still waits for its rest.
        queue.start(listOf("9"))
        wholeList.complete(listOf(track("1"), track("2"), track("3")))
        advanceUntilIdle()
        assertEquals(emptyList<String>(), queue.appended)
        assertEquals(listOf("9"), queue.queue)
        earlier.join()
    }

    @Test
    fun aTapWaitingForARetiredPreloadQueuesNothing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val queue = FakeQueue()
        val preload = LikedPreload(
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            owner = Any(),
            ids = listOf("1", "2"),
            fetch = { ids -> gate.await(); ids.map { track(it) } },
            publish = { },
        )
        val tap = launch {
            playLikedTap(
                tappedId = "1",
                headKeys = listOf("1"),
                queued = setOf("1"),
                startNow = { queue.start(listOf("1")) },
                loadAll = { preload.tracks.await() },
                appendRest = { generation, rest -> queue.append(generation, rest) },
            )
        }
        advanceUntilIdle()
        assertEquals("1", queue.current)
        // The provider changed while the list loaded: the preload is retired, so its tracks never arrive.
        preload.retire()
        advanceUntilIdle()
        assertTrue(tap.isCancelled)
        assertEquals(emptyList<String>(), queue.appended)
    }

    @Test
    fun preloadOfAnotherProviderIsRetiredAndPublishesNothing() = runTest {
        val published = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val preload = LikedPreload(
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            owner = Any(),
            ids = listOf("1", "2"),
            fetch = { ids -> gate.await(); ids.map { track(it) } },
            publish = { list -> published += list.map { it.trackId } },
        )
        advanceUntilIdle()
        // The account changed while the list loaded.
        assertTrue(preload.isStaleFor(Any(), listOf("1", "2")))
        preload.retire()
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(preload.tracks.isCancelled)
        assertEquals(emptyList<String>(), published)
    }

    @Test
    fun preloadOfChangedLikedIdsIsRetiredAndPublishesNothing() = runTest {
        val published = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val owner = Any()
        val preload = LikedPreload(
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            owner = owner,
            ids = listOf("1", "2"),
            fetch = { ids -> gate.await(); ids.map { track(it) } },
            publish = { list -> published += list.map { it.trackId } },
        )
        advanceUntilIdle()
        // A track was liked while the list loaded: the copy of the ids the load holds is stale.
        assertTrue(preload.isStaleFor(owner, listOf("1", "2", "3")))
        preload.retire()
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(preload.tracks.isCancelled)
        assertEquals(emptyList<String>(), published)
    }

    @Test
    fun preloadOfTheSameProviderAndIdsPublishesItsList() = runTest {
        val published = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val owner = Any()
        val preload = LikedPreload(
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            owner = owner,
            ids = listOf("1", "2"),
            fetch = { ids -> gate.await(); ids.map { track(it) } },
            publish = { list -> published += list.map { it.trackId } },
        )
        advanceUntilIdle()
        assertFalse(preload.isStaleFor(owner, listOf("1", "2")))
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("1", "2"), published)
        assertEquals(listOf("1", "2"), preload.tracks.await()?.map { it.trackId })
    }
}
