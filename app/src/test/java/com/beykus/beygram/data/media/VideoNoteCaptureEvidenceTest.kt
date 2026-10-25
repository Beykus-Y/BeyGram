package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteCaptureEvidenceTest {
    @Test fun emptySamplesDoNotInventEvidence() {
        val summary = summarizeAeCapture(emptyList(), 30)
        assertFalse(summary.hasData)
        assertNull(summary.dominantTargetRange)
        assertTrue(summary.distinctTargetRanges.isEmpty())
        assertNull(summary.frameDurationP50Ms)
        assertEquals(0, summary.frameDurationOverBudget)
        assertTrue(summary.aeStates.isEmpty())
    }

    @Test fun dominantRangeIsMostFrequentAndRangesAreSorted() {
        val samples = List(5) { AeCaptureSample(targetFpsRange = 30..30) } +
            List(2) { AeCaptureSample(targetFpsRange = 15..30) } +
            listOf(AeCaptureSample(targetFpsRange = 24..24))
        val summary = summarizeAeCapture(samples, 30)
        assertEquals(30..30, summary.dominantTargetRange)
        assertEquals(listOf(15..30, 24..24, 30..30), summary.distinctTargetRanges)
        assertTrue(summary.hasData)
    }

    @Test fun frameDurationPercentilesAndOverBudgetUseSensorResults() {
        val durations = listOf(16_000_000L, 16_500_000L, 33_000_000L, 34_000_000L, 50_000_000L)
        val summary = summarizeAeCapture(durations.map { AeCaptureSample(frameDurationNs = it) }, 60)
        assertEquals(33.0, summary.frameDurationP50Ms!!, 0.001)
        assertEquals(50.0, summary.frameDurationP95Ms!!, 0.001)
        // 60 FPS budget is 16.7 ms; 33, 34 and 50 ms are over it.
        assertEquals(3, summary.frameDurationOverBudget)
    }

    @Test fun aeStatesAndLastStabilizationModesAreReported() {
        val samples = listOf(
            AeCaptureSample(aeState = 1, videoStabilizationMode = 0, opticalStabilizationMode = 0),
            AeCaptureSample(aeState = 2, videoStabilizationMode = 1, opticalStabilizationMode = 1),
            AeCaptureSample(aeState = 2)
        )
        val summary = summarizeAeCapture(samples, 30)
        assertEquals(mapOf(1 to 1, 2 to 2), summary.aeStates)
        assertEquals(1, summary.videoStabilizationMode)
        assertEquals(1, summary.opticalStabilizationMode)
    }

    @Test fun sensorTapCollectsAndClearsAeEvidence() {
        val tap = VideoNoteSensorTap()
        tap.begin()
        repeat(2) { index ->
            tap.record(
                sensorTimestampNs = index * 33_333_333L,
                exposureNs = 20_000_000L,
                frameDurationNs = 33_333_333L,
                targetFpsRange = 30..30,
                aeState = 2,
                videoStabilizationMode = 1,
                opticalStabilizationMode = 0
            )
        }
        val result = tap.finish(30)!!
        assertEquals(30..30, result.ae!!.dominantTargetRange)
        assertEquals(2, result.ae!!.aeStates[2])
        assertEquals(1, result.ae!!.videoStabilizationMode)
        assertNull(tap.finish(30))
    }

    @Test fun framesWithoutAeFieldsDoNotProduceEmptyEvidence() {
        val tap = VideoNoteSensorTap()
        tap.begin()
        tap.record(0L, 20_000_000L)
        assertNull(tap.finish(30)!!.ae)
    }

    @Test fun probeStatsIncludeSensorFrameDuration() {
        val timestamps = (0 until 60).map { it * 16_666_667L }
        val durations = List(60) { 16_000_000L }
        val stats = measureProbeFrames(timestamps, emptyList(), 60, frameDurationsNs = durations)
        assertEquals(16.0, stats.medianFrameDurationMs!!, 0.001)
        assertEquals(16.0, stats.p95FrameDurationMs!!, 0.001)
        assertEquals(0, stats.frameDurationOverBudget)
    }
}
