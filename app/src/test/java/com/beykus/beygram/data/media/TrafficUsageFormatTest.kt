package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficUsageFormatTest {
    @Test
    fun zeroAndNegativeValuesStayInBytes() {
        assertEquals(TrafficSizeValue(0.0, TrafficSizeUnit.Bytes), trafficSizeValue(0))
        assertEquals(TrafficSizeValue(0.0, TrafficSizeUnit.Bytes), trafficSizeValue(-1))
        assertEquals(TrafficSizeValue(1023.0, TrafficSizeUnit.Bytes), trafficSizeValue(1023))
    }

    @Test
    fun kilobytesStartAt1024Bytes() {
        assertEquals(TrafficSizeValue(1.0, TrafficSizeUnit.Kilobytes), trafficSizeValue(1024))
        assertEquals(TrafficSizeValue(1.5, TrafficSizeUnit.Kilobytes), trafficSizeValue(1536))
    }

    @Test
    fun megabytesStartAt1024Kilobytes() {
        assertEquals(TrafficSizeValue(1.0, TrafficSizeUnit.Megabytes), trafficSizeValue(1024L * 1024L))
        assertEquals(TrafficSizeValue(12.5, TrafficSizeUnit.Megabytes), trafficSizeValue((12.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun gigabytesStartAt1024Megabytes() {
        assertEquals(TrafficSizeValue(1.0, TrafficSizeUnit.Gigabytes), trafficSizeValue(1024L * 1024L * 1024L))
        assertEquals(TrafficSizeValue(2.0, TrafficSizeUnit.Gigabytes), trafficSizeValue(2L * 1024L * 1024L * 1024L))
    }
}
