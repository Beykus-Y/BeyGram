package com.beykus.beygram.data.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCancellationTest {
    @Test
    fun aCancelledAutomaticCheckReportsSkippedWhileTheCallerLives() = runTest {
        val work = CompletableDeferred<AutomaticCheckOutcome>()
        val awaiting = async { awaitAutomaticCheck(work) }
        runCurrent()

        work.cancel()

        assertEquals(AutomaticCheckOutcome.Skipped, awaiting.await())
    }

    @Test
    fun cancellingTheCallerIsNotSwallowed() = runTest {
        val work = CompletableDeferred<AutomaticCheckOutcome>()
        val awaiting = launch { awaitAutomaticCheck(work) }
        runCurrent()

        awaiting.cancelAndJoin()

        assertTrue(awaiting.isCancelled)
    }
}