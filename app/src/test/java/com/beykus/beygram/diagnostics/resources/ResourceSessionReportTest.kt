package com.beykus.beygram.diagnostics.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceSessionReportTest {

    private fun sample(time: Long, cpu: Double?, rx: Long?, tx: Long?) = ResourceSample(
        counters = ResourceCounters(time, null, rx, tx),
        rates = cpu?.let { ResourceRates(it, null, null) },
        javaHeapUsedBytes = 100,
        javaHeapMaxBytes = 1_000,
        nativeHeapBytes = 50,
    )

    @Test
    fun reportSummarizesSessionWithoutSecrets() {
        val samples = listOf(
            sample(0, null, 1_000, 500),
            sample(2_000, 10.0, 3_000, 900),
            sample(4_000, 20.0, 5_000, 1_300),
        )
        val report = formatResourceReport(samples, cpuCores = 4)
        assertTrue(report.contains("samples=3"))
        assertTrue(report.contains("duration_seconds=4"))
        assertTrue(report.contains("cpu_percent_avg=15.0"))
        assertTrue(report.contains("cpu_percent_max=20.0"))
        assertTrue(report.contains("rx_bytes_delta=4000"))
        assertTrue(report.contains("tx_bytes_delta=800"))
    }

    @Test
    fun recorderStopsAcceptingAtCapacity() {
        val recorder = ResourceSessionRecorder(capacity = 2)
        recorder.start()
        recorder.add(sample(0, null, null, null))
        recorder.add(sample(1, null, null, null))
        recorder.add(sample(2, null, null, null))
        assertTrue(recorder.isFull)
        assertEquals(2, recorder.stop().size)
        assertEquals(0, recorder.size)
    }
}
