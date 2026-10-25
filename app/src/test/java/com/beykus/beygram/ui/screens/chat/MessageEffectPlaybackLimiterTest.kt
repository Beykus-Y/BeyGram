package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageEffectPlaybackLimiterTest {
    @Test
    fun oneEffectPlaysAtATimeAndAFinishedSlotIsReleased() {
        val limiter = MessageEffectPlaybackLimiter(maxConcurrent = 1)

        assertTrue(limiter.tryAcquire(1L))
        assertFalse(limiter.tryAcquire(2L))
        assertEquals(1, limiter.activeCount())

        limiter.release(1L)
        assertTrue(limiter.tryAcquire(2L))
        assertEquals(1, limiter.activeCount())
    }

    @Test
    fun repeatedClaimForTheSameMessageDoesNotLeakSlots() {
        val limiter = MessageEffectPlaybackLimiter(maxConcurrent = 2)

        assertTrue(limiter.tryAcquire(5L))
        assertTrue(limiter.tryAcquire(5L))
        assertEquals(1, limiter.activeCount())
        assertTrue(limiter.tryAcquire(6L))
        assertFalse(limiter.tryAcquire(7L))
    }
}
