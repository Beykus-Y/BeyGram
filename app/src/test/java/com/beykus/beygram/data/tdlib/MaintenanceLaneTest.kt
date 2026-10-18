package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MaintenanceLaneTest {
    @Test fun exclusiveActionsNeverOverlap() = runBlocking {
        val lane = MaintenanceLane()
        val inside = AtomicInteger()
        val mostInside = AtomicInteger()
        (1..10).map {
            launch(Dispatchers.Default) {
                lane.exclusive {
                    mostInside.accumulateAndGet(inside.incrementAndGet()) { a, b -> maxOf(a, b) }
                    delay(1)
                    inside.decrementAndGet()
                }
            }
        }.joinAll()
        assertEquals(1, mostInside.get())
    }

    @Test fun latestOnlyRunsWhenNothingIsQueued() = runBlocking {
        val lane = MaintenanceLane()
        assertEquals("done", lane.latestOnly(skipped = "skipped") { "done" })
    }

    @Test fun supersededRequestIsSkippedAndOnlyTheLatestRuns() = runBlocking {
        val lane = MaintenanceLane()
        val release = CompletableDeferred<Unit>()
        val holder = launch { lane.exclusive { release.await() } }
        val ran = mutableListOf<String>()
        val first = launch { lane.latestOnly(skipped = "skipped") { ran += "first"; "first" } }
        yield()
        val second = launch { lane.latestOnly(skipped = "skipped") { ran += "second"; "second" } }
        yield()
        release.complete(Unit)
        listOf(holder, first, second).joinAll()
        assertEquals(listOf("second"), ran)
    }
}
