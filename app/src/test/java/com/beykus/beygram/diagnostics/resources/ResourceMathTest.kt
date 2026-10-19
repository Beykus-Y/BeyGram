package com.beykus.beygram.diagnostics.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceMathTest {

    @Test
    fun cpuPercentIsShareOfAllCores() {
        // 500 мс CPU за 1000 мс на 4 ядрах = 12.5% от всей мощности
        assertEquals(12.5, ResourceMath.cpuPercent(1_000, 1_500, 1_000, 4)!!, 0.0001)
    }

    @Test
    fun cpuPercentIsNullWithoutCountersOrOnReset() {
        assertNull(ResourceMath.cpuPercent(null, 1_500, 1_000, 4))
        assertNull(ResourceMath.cpuPercent(2_000, 1_500, 1_000, 4))
        assertNull(ResourceMath.cpuPercent(1_000, 1_500, 0, 4))
    }

    @Test
    fun bytesPerSecondScalesToSeconds() {
        assertEquals(2_000.0, ResourceMath.bytesPerSecond(0, 4_000, 2_000)!!, 0.0001)
    }

    @Test
    fun unsupportedOrResetCountersAreNotZero() {
        assertNull(ResourceMath.bytesPerSecond(-1, 4_000, 1_000))
        assertNull(ResourceMath.bytesPerSecond(100, -1, 1_000))
        assertNull(ResourceMath.bytesPerSecond(5_000, 4_000, 1_000))
    }

    @Test
    fun ratesCombineAllCounters() {
        val previous = ResourceCounters(10_000, 100, 1_000, 500)
        val current = ResourceCounters(11_000, 200, 3_000, 500)
        val rates = ResourceRates(5.0, 2_000.0, 0.0)
        val actual = ResourceMath.rates(previous, current, cpuCores = 2)
        assertEquals(rates.cpuPercent!!, actual.cpuPercent!!, 0.0001)
        assertEquals(rates.rxBytesPerSecond!!, actual.rxBytesPerSecond!!, 0.0001)
        assertEquals(rates.txBytesPerSecond!!, actual.txBytesPerSecond!!, 0.0001)
    }

    @Test
    fun ratesAreNullForZeroOrNegativeInterval() {
        val previous = ResourceCounters(10_000, 100, 1_000, 500)
        val sameTime = ResourceCounters(10_000, 200, 3_000, 900)
        val actual = ResourceMath.rates(previous, sameTime, cpuCores = 2)
        assertNull(actual.cpuPercent)
        assertNull(actual.rxBytesPerSecond)
        assertNull(actual.txBytesPerSecond)
    }

    @Test
    fun boundedBufferDropsOldestItems() {
        val buffer = BoundedBuffer<Int>(2)
        buffer.add(1)
        buffer.add(2)
        buffer.add(3)
        assertEquals(listOf(2, 3), buffer.snapshot())
    }
}
