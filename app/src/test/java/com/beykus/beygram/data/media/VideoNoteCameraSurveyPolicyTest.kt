package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteCameraSurveyPolicyTest {
    @Test fun cameraWithNormal60AndEncoderPreserves60() {
        val decision = safeVideoNoteFpsSelection(
            55..60, listOf(15..30, 30..30, 30..60), listOf(15..30, 30..30, 30..60), true
        )
        assertEquals(30..60, decision.selected)
        assertEquals(VideoNoteFpsCause.AS_REQUESTED, decision.cause)
        assertTrue(decision.attemptedHighFrameRate)
    }

    @Test fun normal30OnlyFallsBackWithoutPretendingHighSpeedIsNormal() {
        // Samsung M21 / A55 front: every normal range ends at 30.
        val decision = safeVideoNoteFpsSelection(55..60, listOf(15..30, 30..30), listOf(15..30, 30..30), true)
        assertEquals(30..30, decision.selected)
        assertEquals(VideoNoteFpsCause.NO_NORMAL_60, decision.cause)
        assertFalse(decision.attemptedHighFrameRate)
    }

    @Test fun h264EncoderLimitationTriggers30() {
        val decision = safeVideoNoteFpsSelection(55..60, listOf(30..30, 30..60), listOf(30..30, 30..60), false)
        assertEquals(30..30, decision.selected)
        assertEquals(VideoNoteFpsCause.ENCODER_REJECTS_60, decision.cause)
    }

    @Test fun cameraXRangeMissingFromNormalRangesIsNotUsed() {
        // A 60 range reported only by the recording library but absent from the normal AE list
        // (e.g. leaked from a high-speed profile) must not be treated as a normal recording range.
        val decision = safeVideoNoteFpsSelection(55..60, listOf(30..30, 60..60), listOf(15..30, 30..30), true)
        assertEquals(30..30, decision.selected)
        assertEquals(listOf(30..30), decision.normalCandidates)
        assertEquals(VideoNoteFpsCause.NO_NORMAL_60, decision.cause)
    }

    @Test fun unknownNormalRangesDoNotInvent60Capability() {
        val decision = safeVideoNoteFpsSelection(35..60, listOf(30..30), null, null)
        assertEquals(30..30, decision.selected)
        assertEquals(VideoNoteFpsCause.NO_NORMAL_60, decision.cause)
    }

    @Test fun custom24DoesNotBecomeFixed30() {
        val decision = safeVideoNoteFpsSelection(
            24..24, listOf(15..24, 24..24, 30..30), listOf(15..24, 24..24, 30..30), true
        )
        assertEquals(24..24, decision.selected)
        assertEquals(VideoNoteFpsCause.AS_REQUESTED, decision.cause)
    }

    @Test fun lowLightMinimumSurvives30FpsSelection() {
        val decision = safeVideoNoteFpsSelection(
            15..30, listOf(15..30, 30..30), listOf(15..30, 30..30), true
        )
        assertEquals(15..30, decision.selected)
    }

    @Test fun unsupported60FallsBackWithoutRaisingMinimum() {
        val decision = safeVideoNoteFpsSelection(
            15..60, listOf(15..30, 30..30), listOf(15..30, 30..30), false
        )
        assertEquals(15..30, decision.selected)
    }

    @Test fun lensWithoutRequestedMaximumReportsCameraFallback() {
        val decision = safeVideoNoteFpsSelection(30..30, listOf(15..24, 24..24), listOf(15..24, 24..24), null)
        assertEquals(24..24, decision.selected)
        assertEquals(VideoNoteFpsCause.CAMERA_RANGE_FALLBACK, decision.cause)
    }

    @Test fun missingCompatibleRangeReportsUncertainty() {
        val decision = safeVideoNoteFpsSelection(55..60, emptyList(), emptyList(), null)
        assertNull(decision.selected)
        assertEquals(VideoNoteFpsCause.NO_COMPATIBLE_RANGE, decision.cause)
    }
}
