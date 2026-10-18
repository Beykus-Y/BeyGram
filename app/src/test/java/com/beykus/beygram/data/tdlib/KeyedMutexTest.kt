package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class KeyedMutexTest {
    @Test fun sameKeyRunsOneAtATime() = runBlocking {
        val mutex = KeyedMutex<Long>()
        val inside = AtomicInteger()
        val mostInside = AtomicInteger()
        (1..20).map {
            launch(Dispatchers.Default) {
                mutex.withLock(7L) {
                    mostInside.accumulateAndGet(inside.incrementAndGet()) { a, b -> maxOf(a, b) }
                    delay(1)
                    inside.decrementAndGet()
                }
            }
        }.joinAll()
        assertEquals(1, mostInside.get())
    }

    @Test fun differentKeysDoNotWaitForEachOther() = runBlocking {
        val mutex = KeyedMutex<Long>()
        val release = CompletableDeferred<Unit>()
        val holder = launch { mutex.withLock(1L) { release.await() } }
        withTimeout(5_000) { mutex.withLock(2L) { } }
        release.complete(Unit)
        holder.join()
    }

    @Test fun failedActionReleasesTheKey() = runBlocking {
        val mutex = KeyedMutex<Long>()
        runCatching { mutex.withLock(1L) { error("boom") } }
        withTimeout(5_000) { mutex.withLock(1L) { } }
        assertEquals(0, mutex.activeKeys)
    }

    @Test fun cancelledWaiterLeavesNoKeyBehind() = runBlocking {
        val mutex = KeyedMutex<Long>()
        val release = CompletableDeferred<Unit>()
        val holder = launch { mutex.withLock(1L) { release.await() } }
        val waiter = launch { mutex.withLock(1L) { error("the cancelled waiter must not run") } }
        yield()
        waiter.cancelAndJoin()
        release.complete(Unit)
        holder.join()
        assertEquals(0, mutex.activeKeys)
    }

    @Test fun unusedKeysAreForgotten() = runBlocking {
        val mutex = KeyedMutex<Long>()
        (1L..5L).forEach { key -> mutex.withLock(key) { } }
        assertEquals(0, mutex.activeKeys)
    }
}
