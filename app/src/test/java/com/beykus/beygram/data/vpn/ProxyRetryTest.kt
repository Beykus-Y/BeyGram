package com.beykus.beygram.data.vpn

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRetryTest {
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
