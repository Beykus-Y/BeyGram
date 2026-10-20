package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoNoteFrameTimingTest {
    @Test fun computesMedianAndP95FromRealFrameIntervals() {
        assertEquals(33.333, videoNoteGapPercentile(listOf(20_000,33_333,40_000,50_000,60_000), .5)!!, 0.001)
        assertEquals(60.0, videoNoteGapPercentile(listOf(20_000,33_333,40_000,50_000,60_000), .95)!!, 0.001)
    }
    @Test fun emptyFileNeverInventsGaps() {
        assertNull(videoNoteGapPercentile(emptyList(), .5))
    }
}
