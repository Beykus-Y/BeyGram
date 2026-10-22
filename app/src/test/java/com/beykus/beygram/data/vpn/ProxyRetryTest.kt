package com.beykus.beygram.data.vpn

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRetryTest {
    @Test
    fun transientProxyStatusWaitsForRunning() = runBlocking {
        val states = flowOf(
            ProxyRuntimeState(ProxyRuntimeStatus.STARTING),
            ProxyRuntimeState(ProxyRuntimeStatus.RUNNING)
        )
        assertEquals(ProxyRuntimeStatus.RUNNING, awaitSettledProxyState(states, 1000)?.status)
    }

    @Test
    fun permanentlyStartingProxyTimesOutAndYieldsUserChoice() = runBlocking {
        val states = flow<ProxyRuntimeState> {
            emit(ProxyRuntimeState(ProxyRuntimeStatus.STARTING))
            awaitCancellation()
        }
        assertEquals(null, awaitSettledProxyState(states, 20L))
    }

    @Test
    fun failedProxySettlesImmediately() = runBlocking {
        assertEquals(
            ProxyRuntimeStatus.FAILED,
            awaitSettledProxyState(flowOf(ProxyRuntimeState(ProxyRuntimeStatus.FAILED)), 1000)?.status
        )
    }

    @Test
    fun stopsAtFirstSuccess() = runBlocking {
        val tries = mutableListOf<Int>()
        val succeeded = retryUntilSuccess(maxAttempts = 5, retryDelayMillis = 0L) { number ->
            tries += number
            number == 3
        }
        assertTrue(succeeded)
        assertEquals(listOf(1, 2, 3), tries)
    }

    @Test
    fun givesUpAfterEveryTryFails() = runBlocking {
        var tries = 0
        val succeeded = retryUntilSuccess(maxAttempts = 5, retryDelayMillis = 0L) {
            tries += 1
            false
        }
        assertFalse(succeeded)
        assertEquals(5, tries)
    }
}
