package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoNoteFrameAnalysisTest {
    @Test fun calculatesFpsFromActualPresentationTimestamps() {
        assertEquals(60.0, measuredVideoFps(421, 0, 7_000_000)!!, 0.001)
        assertEquals(30.0, measuredVideoFps(211, 0, 7_000_000)!!, 0.001)
    }

    @Test fun refusesMisleadingSingleFrameEstimates() {
        assertNull(measuredVideoFps(1, 0, 0))
        assertNull(measuredVideoFps(20, 2_000, 2_000))
    }
}
