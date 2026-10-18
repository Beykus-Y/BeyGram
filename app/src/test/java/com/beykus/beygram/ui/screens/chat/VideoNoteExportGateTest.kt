package com.beykus.beygram.ui.screens.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VideoNoteExportGateTest {
    // The gate is one object for the whole test JVM. Each case starts and ends with it idle, so no case depends on another's count.
    @Before fun gateStartsIdle() = resetGate()

    @After fun gateEndsIdle() = resetGate()

    private fun resetGate() {
        while (VideoNoteExportGate.busy) VideoNoteExportGate.end()
    }

    @Test fun gateIsBusyUntilEveryExportFinishes() {
        assertFalse(VideoNoteExportGate.busy)
        VideoNoteExportGate.begin()
        assertTrue(VideoNoteExportGate.busy)
        VideoNoteExportGate.begin()
        assertTrue(VideoNoteExportGate.busy)
        VideoNoteExportGate.end()
        assertTrue(VideoNoteExportGate.busy)
        VideoNoteExportGate.end()
        assertFalse(VideoNoteExportGate.busy)
    }

    @Test fun gateNeverGoesNegative() {
        VideoNoteExportGate.end()
        assertFalse(VideoNoteExportGate.busy)
    }

    @Test fun leaseReleasedWhenExportCompletes() = runBlocking {
        var executed = false
        val job = launchExclusiveExport { executed = true }
        job.join()
        assertTrue(executed)
        assertFalse(VideoNoteExportGate.busy)
    }

    @Test fun leaseReleasedEvenWhenScopeAlreadyCancelled() = runBlocking {
        // Пользователь ушёл из чата, VM уничтожен, CameraX прислал Finalize позже: тело не выполнится,
        // но шлюз обязан освободиться, иначе записи заблокированы до перезапуска процесса.
        val cancelled = CoroutineScope(Job().apply { cancel() })
        var executed = false
        val job = cancelled.launchExclusiveExport { executed = true }
        job.join()
        assertTrue(job.isCompleted)
        assertFalse(executed)
        assertFalse(VideoNoteExportGate.busy)
    }

    @Test fun leaseReleasedWhenRunningExportIsCancelled() = runBlocking {
        val scope = CoroutineScope(Job())
        val started = Job()
        val job = scope.launchExclusiveExport {
            started.complete()
            awaitCancellation()
        }
        started.join()
        scope.cancel()
        job.join()
        assertFalse(VideoNoteExportGate.busy)
    }

    @Test fun exclusiveExportsNeverOverlap() = runBlocking {
        val scope = CoroutineScope(Job())
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        var concurrent = 0
        var maxConcurrent = 0
        val first = scope.launchExclusiveExport {
            firstEntered.complete(Unit)
            concurrent += 1
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            releaseFirst.await()
            concurrent -= 1
        }
        firstEntered.await()
        val second = scope.launchExclusiveExport {
            secondEntered.complete(Unit)
            concurrent += 1
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            concurrent -= 1
        }
        // Пока первое тело держит мьютекс, второе не стартует: никаких двух Transformer параллельно.
        assertTrue(secondEntered.isActive)
        releaseFirst.complete(Unit)
        first.join()
        second.join()
        assertEquals(1, maxConcurrent)
        assertFalse(VideoNoteExportGate.busy)
        scope.cancel()
    }

    @Test fun concurrentBeginAndEndNeverLeaveTheGateBusy() {
        val threads = List(8) {
            Thread {
                repeat(2_000) {
                    VideoNoteExportGate.begin()
                    VideoNoteExportGate.end()
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertFalse(VideoNoteExportGate.busy)
    }
}
