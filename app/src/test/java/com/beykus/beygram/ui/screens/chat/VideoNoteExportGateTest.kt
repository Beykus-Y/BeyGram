package com.beykus.beygram.ui.screens.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteExportGateTest {
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
}
