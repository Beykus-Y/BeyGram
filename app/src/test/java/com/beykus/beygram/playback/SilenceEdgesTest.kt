package com.beykus.beygram.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceEdgesTest {
    private val stereo44k = 44_100
    private val stereo = 2

    /** A window of 10 ms at 100 Hz mono is a single sample, which keeps expectations readable. */
    private fun monoHundredHz(samples: FloatArray, capacity: Int = 10_000, keepLast: Boolean = false): FloatArray {
        val meter = SilenceWindowMeter(sampleRate = 100, channels = 1, capacity = capacity, keepLast = keepLast)
        meter.addFloat(samples, samples.size)
        return meter.finish()
    }

    private fun pcmFrames(frames: Int, channels: Int, value: Short): ShortArray =
        ShortArray(frames * channels) { value }

    @Test fun leadingSilenceIsCountedInWholeWindows() {
        val meter = SilenceWindowMeter(stereo44k, stereo, capacity = SILENCE_SCAN_WINDOWS, keepLast = false)
        meter.addPcm16(pcmFrames(frames = 44_100, channels = stereo, value = 0), 44_100 * stereo)
        meter.addPcm16(pcmFrames(frames = 44_100, channels = stereo, value = 16_384), 44_100 * stereo)
        val edges = silenceEdgesFromPeaks(head = meter.finish(), tail = null, durationMs = 2_000)
        assertEquals(1_000L, edges.leadingMs)
        assertTrue(edges.leadingKnown)
    }

    @Test fun trailingSilenceComesFromTheKeptEnd() {
        val meter = SilenceWindowMeter(stereo44k, stereo, capacity = SILENCE_SCAN_WINDOWS, keepLast = true)
        meter.addPcm16(pcmFrames(frames = 44_100 * 9, channels = stereo, value = 8_000), 44_100 * 9 * stereo)
        meter.addPcm16(pcmFrames(frames = 44_100, channels = stereo, value = 0), 44_100 * stereo)
        val edges = silenceEdgesFromPeaks(head = null, tail = meter.finish(), durationMs = 10_000)
        assertEquals(1_000L, edges.trailingMs)
        assertEquals(SilenceEdges.UNKNOWN_MS, edges.leadingMs)
    }

    @Test fun silentHeadWithoutAnyAudibleWindowStaysUnknown() {
        val head = monoHundredHz(FloatArray(500) { 0f })
        val edges = silenceEdgesFromPeaks(head = head, tail = null, durationMs = 5_000)
        assertFalse(edges.leadingKnown)
        assertEquals(SilenceEdges.UNKNOWN_MS, edges.leadingMs)
    }

    @Test fun audibleFromTheStartReportsZero() {
        val head = monoHundredHz(FloatArray(300) { 0.5f })
        val tail = monoHundredHz(FloatArray(300) { 0.5f }, keepLast = true, capacity = 300)
        val edges = silenceEdgesFromPeaks(head = head, tail = tail, durationMs = 3_000)
        assertEquals(0L, edges.leadingMs)
        assertEquals(0L, edges.trailingMs)
    }

    @Test fun silenceShorterThanTheMinimumReportsZero() {
        // 5 windows of 10 ms is 50 ms, below the 100 ms minimum.
        val head = monoHundredHz(FloatArray(20) { if (it < 5) 0f else 0.5f })
        assertEquals(0L, silenceEdgesFromPeaks(head, null, 1_000).leadingMs)
    }

    @Test fun amplitudeAtTheThresholdIsAudible() {
        // 103/32768 is just under -50 dBFS, 104/32768 is just over it.
        val meter = SilenceWindowMeter(sampleRate = 100, channels = 1, capacity = 10, keepLast = false)
        meter.addPcm16(shortArrayOf(103, 104), 2)
        val peaks = meter.finish()
        assertEquals(1, leadingSilentWindows(peaks))
    }

    @Test fun floatSamplesUseTheSameThreshold() {
        val peaks = monoHundredHz(floatArrayOf(0f, -0.001f, 0.5f, 0f))
        assertEquals(2, leadingSilentWindows(peaks))
        assertEquals(1, trailingSilentWindows(peaks))
    }

    @Test fun keepLastReturnsTheNewestWindowsInTimeOrder() {
        // Eight windows, capacity four: the last four are 0, 0.5, 0, 0.
        val peaks = monoHundredHz(floatArrayOf(0f, 0.5f, 0f, 0f, 0f, 0.5f, 0f, 0f), capacity = 4, keepLast = true)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 0f, 0f), peaks, 0f)
        assertEquals(2, trailingSilentWindows(peaks))
    }

    @Test fun firstWindowModeStopsAtCapacity() {
        val meter = SilenceWindowMeter(sampleRate = 100, channels = 1, capacity = 3, keepLast = false)
        meter.addFloat(floatArrayOf(0f, 0f, 0.5f, 0.5f, 0.5f), 5)
        assertTrue(meter.isFull)
        assertArrayEquals(floatArrayOf(0f, 0f, 0.5f), meter.finish(), 0f)
    }

    @Test fun meterReportsAudibleWindowsForEarlyStop() {
        val meter = SilenceWindowMeter(sampleRate = 100, channels = 1, capacity = 100, keepLast = false)
        meter.addFloat(floatArrayOf(0f, 0f, 0f), 3)
        assertFalse(meter.hasAudibleWindow)
        meter.addFloat(floatArrayOf(0.2f, 0f), 2)
        // At 100 Hz mono each sample is its own window, so the loud sample is audible as soon as it is read.
        assertTrue(meter.hasAudibleWindow)
    }

    @Test fun wholeSilentTailStaysUnknown() {
        val tail = monoHundredHz(FloatArray(1_000) { 0f }, keepLast = true, capacity = SILENCE_SCAN_WINDOWS)
        val edges = silenceEdgesFromPeaks(head = null, tail = tail, durationMs = 60_000)
        assertEquals(SilenceEdges.UNKNOWN_MS, edges.trailingMs)
        assertEquals(60_000L, edges.durationMs)
    }

    @Test fun missingTailLeavesTrailingUnknown() {
        val head = monoHundredHz(FloatArray(30) { if (it < 20) 0f else 0.5f })
        val edges = silenceEdgesFromPeaks(head = head, tail = null, durationMs = 0L)
        assertEquals(200L, edges.leadingMs)
        assertEquals(SilenceEdges.UNKNOWN_MS, edges.trailingMs)
        assertEquals(0L, edges.durationMs)
    }

    @Test fun leadingSkipStartsBeforeTheFirstAudibleWindow() {
        assertEquals(0L, leadingSkipStartMs(SilenceEdges.UNKNOWN_MS))
        assertEquals(0L, leadingSkipStartMs(0L))
        assertEquals(0L, leadingSkipStartMs(90L))
        assertEquals(70L, leadingSkipStartMs(100L))
        assertEquals(970L, leadingSkipStartMs(1_000L))
    }

    @Test fun trailingSkipWaitsForTheAudibleEnd() {
        // A 60 s track with 2 s of trailing silence has its audible content end at 58 s.
        assertFalse(trailingSkipDue(positionMs = 57_999L, durationMs = 60_000L, trailingMs = 2_000L))
        assertTrue(trailingSkipDue(positionMs = 58_000L, durationMs = 60_000L, trailingMs = 2_000L))
        assertTrue(trailingSkipDue(positionMs = 59_500L, durationMs = 60_000L, trailingMs = 2_000L))
    }

    @Test fun trailingSkipIgnoresUnknownShortAndImpossibleCases() {
        assertFalse(trailingSkipDue(59_000L, 60_000L, SilenceEdges.UNKNOWN_MS))
        assertFalse(trailingSkipDue(59_950L, 60_000L, 50L))
        assertFalse(trailingSkipDue(0L, 0L, 500L))
    }
}
