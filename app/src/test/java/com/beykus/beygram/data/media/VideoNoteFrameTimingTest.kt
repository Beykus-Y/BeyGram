package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoNoteFrameTimingTest {
    private fun steady(count: Int, intervalUs: Long): List<Long> = List(count) { it * intervalUs }

    @Test fun computesMedianAndP95FromRealFrameIntervals() {
        assertEquals(40.0, videoNoteGapPercentile(listOf(20_000, 33_333, 40_000, 50_000, 60_000), .5)!!, 0.001)
        assertEquals(60.0, videoNoteGapPercentile(listOf(20_000, 33_333, 40_000, 50_000, 60_000), .95)!!, 0.001)
    }

    @Test fun emptyFileNeverInventsGaps() {
        assertNull(videoNoteGapPercentile(emptyList(), .5))
    }

    @Test fun steadyThirtyFpsHasNoEstimatedDrops() {
        val timing = analyzeVideoNoteFrameTiming(steady(10, 33_333L), declaredFps = 30)
        assertEquals(10, timing.sampleCount)
        assertEquals(VideoNoteTimingSource.FILE_FPS, timing.timingSource)
        assertEquals(33.3, timing.p50GapMs!!, 0.1)
        assertEquals(10, timing.expectedFrames)
        assertEquals(0, timing.estimatedDroppedFrames)
        assertEquals(0, timing.repeatedTimestamps)
        assertEquals(0, timing.reorderedTimestamps)
    }

    @Test fun declaredSixtyWithThirtyFpsDeliveryDoesNotLookHealthy() {
        val timing = analyzeVideoNoteFrameTiming(steady(10, 33_333L), declaredFps = 60)
        assertEquals(VideoNoteTimingSource.FILE_FPS, timing.timingSource)
        // Every interval is about two 60 FPS frame budgets: nine gaps -> nine estimated missing frames.
        assertEquals(9, timing.estimatedDroppedFrames)
    }

    @Test fun aSingleLongGapCountsAsMultipleDroppedFrames() {
        val timing = analyzeVideoNoteFrameTiming(
            listOf(0L, 33_333L, 100_000L, 133_333L),
            declaredFps = 30
        )
        assertEquals(1, timing.estimatedDroppedFrames)
    }

    @Test fun repeatedTimestampsAreCountedWithoutBeingCalledPixels() {
        val timing = analyzeVideoNoteFrameTiming(
            listOf(0L, 33_333L, 33_333L, 66_666L, 66_666L),
            declaredFps = 30
        )
        assertEquals(2, timing.repeatedTimestamps)
        assertEquals(0, timing.reorderedTimestamps)
        assertEquals(0, timing.estimatedDroppedFrames)
    }

    @Test fun nonMonotonicTimestampsDoNotCreateFalseDroppedFrames() {
        val timing = analyzeVideoNoteFrameTiming(
            listOf(0L, 66_666L, 33_333L, 100_000L),
            declaredFps = 30
        )
        assertEquals(1, timing.reorderedTimestamps)
        // Sorted presentation timestamps are 0/33333/66666/100000: no frame is actually missing.
        assertEquals(0, timing.estimatedDroppedFrames)
    }

    @Test fun aRealMissingPresentationTimestampIsStillCounted() {
        val timing = analyzeVideoNoteFrameTiming(
            listOf(0L, 33_333L, 66_666L, 133_333L),
            declaredFps = 30
        )
        assertEquals(1, timing.estimatedDroppedFrames)
    }

    @Test fun vfrWithoutDeclaredFpsUsesMedianButDoesNotInventDrops() {
        val timing = analyzeVideoNoteFrameTiming(
            listOf(0L, 20_000L, 60_000L, 70_000L, 120_000L)
        )
        assertEquals(VideoNoteTimingSource.MEDIAN_GAP, timing.timingSource)
        assertNull(timing.estimatedDroppedFrames)
        assertNull(timing.expectedFrames)
        assertEquals(40.0, timing.p50GapMs!!, 0.1)
    }

    @Test fun twoSamplesAreNotEnoughForATimingBaseline() {
        val timing = analyzeVideoNoteFrameTiming(listOf(0L, 33_333L))
        assertEquals(VideoNoteTimingSource.UNKNOWN, timing.timingSource)
        assertNull(timing.estimatedDroppedFrames)
        assertNull(timing.expectedFrames)
    }

    @Test fun emptyAndSingleSampleFilesStayUnknown() {
        val empty = analyzeVideoNoteFrameTiming(emptyList(), declaredFps = 60)
        assertEquals(0, empty.sampleCount)
        assertEquals(VideoNoteTimingSource.UNKNOWN, empty.timingSource)
        assertNull(empty.p50GapMs)

        val single = analyzeVideoNoteFrameTiming(listOf(0L), declaredFps = 60)
        assertEquals(1, single.sampleCount)
        assertEquals(VideoNoteTimingSource.UNKNOWN, single.timingSource)
    }

    @Test fun targetFpsIsOnlyAFallbackAndIsNamedAsSuch() {
        val timing = analyzeVideoNoteFrameTiming(steady(6, 33_333L), targetFps = 30)
        assertEquals(VideoNoteTimingSource.TARGET_FPS, timing.timingSource)
        assertEquals(0, timing.estimatedDroppedFrames)
    }
}
