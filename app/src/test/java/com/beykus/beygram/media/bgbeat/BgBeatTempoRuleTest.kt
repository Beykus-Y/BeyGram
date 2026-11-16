package com.beykus.beygram.media.bgbeat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BgBeatTempoRuleTest {

    private fun analysis(
        status: BgBeatStatus = BgBeatStatus.OK,
        analyzed: Boolean = true,
        bpm: Double = 128.0,
        confidence: Float = 0.9f,
        tempoStable: Boolean = true,
        octaveAmbiguous: Boolean = false
    ) = BgBeatAnalysis(
        status = status,
        analyzed = analyzed,
        bpm = bpm,
        bpmConfidence = confidence,
        tempoStable = tempoStable,
        octaveAmbiguous = octaveAmbiguous
    )

    @Test
    fun aTrustworthyAnalysisKeepsItsTempo() {
        assertEquals(128.0, trustedTempoBpm(analysis())!!, 0.0)
    }

    @Test
    fun confidenceAtTheThresholdIsEnoughAndBelowItIsNot() {
        assertEquals(128.0, trustedTempoBpm(analysis(confidence = BG_BEAT_TRUSTED_BPM_CONFIDENCE))!!, 0.0)
        assertNull(trustedTempoBpm(analysis(confidence = 0.59f)))
        assertNull(trustedTempoBpm(analysis(confidence = 0f)))
        assertNull(trustedTempoBpm(analysis(confidence = Float.NaN)))
    }

    @Test
    fun anOctaveAmbiguousTempoIsNotKept() {
        assertNull(trustedTempoBpm(analysis(octaveAmbiguous = true)))
    }

    @Test
    fun anUnstableTempoIsNotKept() {
        assertNull(trustedTempoBpm(analysis(tempoStable = false)))
    }

    @Test
    fun anAnalysisThatDidNotBuildTheTimelineIsNotKept() {
        assertNull(trustedTempoBpm(analysis(analyzed = false)))
    }

    @Test
    fun onlySuccessfulAnalysesQualify() {
        assertNull(trustedTempoBpm(analysis(status = BgBeatStatus.CANCELLED)))
        assertNull(trustedTempoBpm(analysis(status = BgBeatStatus.UNSUPPORTED)))
        assertNull(trustedTempoBpm(analysis(status = BgBeatStatus.ERROR)))
    }

    @Test
    fun aMissingOrBrokenBpmIsNotKept() {
        assertNull(trustedTempoBpm(analysis(bpm = 0.0)))
        assertNull(trustedTempoBpm(analysis(bpm = -120.0)))
        assertNull(trustedTempoBpm(analysis(bpm = Double.NaN)))
        assertNull(trustedTempoBpm(analysis(bpm = Double.POSITIVE_INFINITY)))
    }
}
