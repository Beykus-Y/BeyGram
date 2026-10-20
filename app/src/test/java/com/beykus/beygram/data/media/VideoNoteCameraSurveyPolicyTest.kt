package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteCameraSurveyPolicyTest {
    @Test fun cameraWithNormal60AndEncoderPreserves60() {
        val (range, reason) = safeVideoNoteFpsSelection(
            55..60, listOf(15..30, 30..30, 30..60), listOf(15..30, 30..30, 30..60), true
        )
        assertEquals(30..60, range)
        assertTrue(reason.contains("measured"))
    }

    @Test fun normal30OnlyFallsBackWithoutPretendingHighSpeedIsNormal() {
        val (range, reason) = safeVideoNoteFpsSelection(55..60, listOf(15..30, 30..30), listOf(15..30, 30..30), true)
        assertEquals(30..30, range)
        assertTrue(reason.contains("fallback"))
    }

    @Test fun h264EncoderLimitationTriggers30() {
        val (range, _) = safeVideoNoteFpsSelection(55..60, listOf(30..30, 30..60), listOf(30..30, 30..60), false)
        assertEquals(30..30, range)
    }

    @Test fun unknownNormalRangesDoNotInvent60Capability() {
        val (range, _) = safeVideoNoteFpsSelection(35..60, listOf(30..30), null, null)
        assertEquals(30..30, range)
    }

    @Test fun custom24DoesNotBecomeFixed30() {
        val (range, _) = safeVideoNoteFpsSelection(
            24..24, listOf(15..24, 24..24, 30..30), listOf(15..24, 24..24, 30..30), true
        )
        assertEquals(24..24, range)
    }

    @Test fun lowLightMinimumSurvives30FpsSelection() {
        val (range, _) = safeVideoNoteFpsSelection(
            15..30, listOf(15..30, 30..30), listOf(15..30, 30..30), true
        )
        assertEquals(15..30, range)
    }

    @Test fun unsupported60FallsBackWithoutRaisingMinimum() {
        val (range, _) = safeVideoNoteFpsSelection(
            15..60, listOf(15..30, 30..30), listOf(15..30, 30..30), false
        )
        assertEquals(15..30, range)
    }

    @Test fun missingCompatibleRangeReportsUncertainty() {
        val (range, reason) = safeVideoNoteFpsSelection(55..60, emptyList(), emptyList(), null)
        assertNull(range)
        assertTrue(reason.contains("default"))
    }
}
