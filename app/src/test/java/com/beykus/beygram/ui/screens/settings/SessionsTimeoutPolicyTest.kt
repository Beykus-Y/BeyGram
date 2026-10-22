package com.beykus.beygram.ui.screens.settings

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionsTimeoutPolicyTest {
    @Test fun successfulResponseRetained() = runBlocking {
        val outcome = awaitSessionRequest(1_000) { Result.success(listOf(1L, 2L)) }
        assertEquals(listOf(1L, 2L), (outcome as SessionAwait.Completed).result.getOrThrow())
    }

    @Test fun failedResponseIsNotReinterpretedAsEmpty() = runBlocking {
        val outcome = awaitSessionRequest<List<Long>>(1_000) {
            Result.failure(IllegalStateException("offline"))
        }
        assertTrue((outcome as SessionAwait.Completed).result.isFailure)
    }

    @Test fun stalledTdlibQueryExitsWithTimeout() = runBlocking {
        val outcome = awaitSessionRequest<List<Long>>(20) {
            delay(10_000)
            Result.success(emptyList())
        }
        assertEquals(SessionAwait.TimedOut, outcome)
    }
}
