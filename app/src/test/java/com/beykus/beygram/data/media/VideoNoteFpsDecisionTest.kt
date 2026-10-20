package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoNoteFpsDecisionTest {
    @Test fun previewReusesRecordingRangeWhenLensHasIt() {
        assertEquals(30..30, previewFrameRateFor(30..30, listOf(15..30, 30..30, 30..60)))
    }

    @Test fun previewDoesNotAskForMoreThanTheRecording() {
        // Before: the preview picked 30..60 from the raw 55..60 request next to a 30..30 recording.
        val preview = previewFrameRateFor(30..30, listOf(15..30, 30..60))
        assertEquals(15..30, preview)
    }

    @Test fun previewFollowsCameraDefaultWhenRecordingHasNoRange() {
        assertNull(previewFrameRateFor(null, listOf(30..30)))
    }

    @Test fun exportLossIsBlamedOnProcessing() {
        assertEquals(VideoNoteFpsLimitStage.EXPORT, videoNoteFpsLimitStage(60, 60, 60.0, 30.0))
        assertEquals(VideoNoteFpsLimitStage.EXPORT, videoNoteFpsLimitStage(30, 30, 30.0, 20.0))
    }

    @Test fun thirtyBecause60WasNeverRequestedIsPolicyNotHardware() {
        // M21 / A55 reports: requested 55..60, selected 30..30, RAW 30, final 30.
        assertEquals(VideoNoteFpsLimitStage.POLICY, videoNoteFpsLimitStage(60, 30, 30.06, 30.06))
    }

    @Test fun requested60ButCameraDeliveredLessIsCameraRuntime() {
        assertEquals(VideoNoteFpsLimitStage.CAMERA_RUNTIME, videoNoteFpsLimitStage(60, 60, 31.0, 31.0))
    }

    @Test fun lowLightBelowVariableRangeIsCameraRuntime() {
        assertEquals(VideoNoteFpsLimitStage.CAMERA_RUNTIME, videoNoteFpsLimitStage(30, 30, 19.0, 19.0))
    }

    @Test fun healthyRecordingReportsNoLoss() {
        assertEquals(VideoNoteFpsLimitStage.NONE, videoNoteFpsLimitStage(60, 60, 59.7, 59.5))
        assertEquals(VideoNoteFpsLimitStage.NONE, videoNoteFpsLimitStage(30, 30, 30.0, 29.9))
    }

    @Test fun missingMeasurementsAreUnknown() {
        assertEquals(VideoNoteFpsLimitStage.UNKNOWN, videoNoteFpsLimitStage(60, 60, null, 30.0))
        assertEquals(VideoNoteFpsLimitStage.UNKNOWN, videoNoteFpsLimitStage(60, null, 30.0, 30.0))
    }
}
