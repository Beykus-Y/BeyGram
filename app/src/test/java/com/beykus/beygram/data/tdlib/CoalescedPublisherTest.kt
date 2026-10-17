package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoalescedPublisherTest {
    @Test
    fun `a burst of requests publishes once after the delay`() = runTest {
        var publishes = 0
        val publisher = CoalescedPublisher(backgroundScope, delayMs = 100L) { publishes++ }

        repeat(50) { publisher.request() }
        advanceTimeBy(99L)
        runCurrent()
        assertEquals(0, publishes)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, publishes)
    }

    @Test
    fun `a request after a publish starts a new one`() = runTest {
        var publishes = 0
        val publisher = CoalescedPublisher(backgroundScope, delayMs = 100L) { publishes++ }

        publisher.request()
        advanceTimeBy(100L)
        runCurrent()
        publisher.request()
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(2, publishes)
    }

    @Test
    fun `a request made during a publish schedules the next one`() = runTest {
        var publishes = 0
        lateinit var publisher: CoalescedPublisher
        publisher = CoalescedPublisher(backgroundScope, delayMs = 100L) {
            publishes++
            if (publishes == 1) publisher.request()
        }

        publisher.request()
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(1, publishes)

        advanceTimeBy(100L)
        runCurrent()
        assertEquals(2, publishes)
    }
}
