package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficUsageTest {
    @Test
    fun repeatedAndDecreasingProgressNeverDoubleCounts() {
        val usage = TrafficUsageAccumulator()
        usage.begin(1, TrafficOrigin.Auto)

        usage.progress(1, 100)
        usage.progress(1, 100)
        usage.progress(1, 40)
        usage.progress(1, 80)
        assertEquals(100L, usage.snapshot().autoBytes)

        usage.progress(1, 150)
        assertEquals(150L, usage.snapshot().autoBytes)
    }

    @Test
    fun resumedAttemptStartsFromKnownBytes() {
        val usage = TrafficUsageAccumulator()
        usage.begin(7, TrafficOrigin.Manual, alreadyDownloadedBytes = 40)
        usage.progress(7, 40)
        assertEquals(0L, usage.snapshot().manualBytes)

        usage.progress(7, 100)
        assertEquals(60L, usage.snapshot().manualBytes)
    }

    @Test
    fun aNewAttemptAfterFinishCountsNewTrafficAgain() {
        val usage = TrafficUsageAccumulator()
        usage.begin(3, TrafficOrigin.Auto)
        usage.progress(3, 100)
        usage.finish(3)
        assertEquals(100L, usage.snapshot().autoBytes)
        assertFalse(usage.snapshot().hasActiveDownloads)

        usage.begin(3, TrafficOrigin.Auto)
        usage.progress(3, 100)
        assertEquals(200L, usage.snapshot().autoBytes)
    }

    @Test
    fun manualTakeoverKeepsAlreadyCountedAutomaticBytes() {
        val usage = TrafficUsageAccumulator()
        usage.begin(5, TrafficOrigin.Auto)
        usage.progress(5, 100)

        usage.promote(5)
        usage.progress(5, 180)

        val snapshot = usage.snapshot()
        assertEquals(100L, snapshot.autoBytes)
        assertEquals(80L, snapshot.manualBytes)
        assertEquals(180L, snapshot.totalBytes)
        assertEquals(1, snapshot.manualActive)
        assertEquals(0, snapshot.autoActive)
        assertEquals(TrafficOrigin.Manual, snapshot.activeOrigins[5])
    }

    @Test
    fun automaticBeginDoesNotDemoteAManualTransfer() {
        val usage = TrafficUsageAccumulator()
        usage.begin(5, TrafficOrigin.Manual)
        usage.begin(5, TrafficOrigin.Auto)
        usage.progress(5, 10)
        assertEquals(0L, usage.snapshot().autoBytes)
        assertEquals(10L, usage.snapshot().manualBytes)
    }

    @Test
    fun progressWithoutBeginIsCountedAsAutomaticBackgroundTraffic() {
        val usage = TrafficUsageAccumulator()
        usage.progress(9, 30)
        val snapshot = usage.snapshot()
        assertEquals(30L, snapshot.autoBytes)
        assertEquals(1, snapshot.autoActive)
        assertEquals(TrafficOrigin.Auto, snapshot.activeOrigins[9])
    }

    @Test
    fun finishingOneTransferKeepsOtherTransfersActive() {
        val usage = TrafficUsageAccumulator()
        usage.begin(1, TrafficOrigin.Auto)
        usage.begin(2, TrafficOrigin.Manual)
        usage.progress(1, 50)
        usage.progress(2, 20)
        usage.finish(1)

        val snapshot = usage.snapshot()
        assertEquals(50L, snapshot.autoBytes)
        assertEquals(20L, snapshot.manualBytes)
        assertFalse(1 in snapshot.activeOrigins)
        assertTrue(2 in snapshot.activeOrigins)
        assertEquals(1, snapshot.manualActive)
    }

    @Test
    fun negativeProgressIsTreatedAsZero() {
        val usage = TrafficUsageAccumulator()
        usage.begin(4, TrafficOrigin.Auto)
        usage.progress(4, -100)
        assertEquals(0L, usage.snapshot().autoBytes)
    }

    @Test
    fun sessionsAreScopedByAccount() {
        val first = TrafficUsageStore.session("account-a")
        val second = TrafficUsageStore.session("account-b")
        first.begin(1, TrafficOrigin.Auto)
        first.progress(1, 70)

        assertEquals(70L, first.snapshot.value.autoBytes)
        assertEquals(0L, second.snapshot.value.totalBytes)
        assertEquals(70L, TrafficUsageStore.session("account-a").snapshot.value.autoBytes)
    }
}
