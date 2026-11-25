package com.beykus.beygram.media.bgrhythm

import com.beykus.beygram.media.bgbeat.BgBeatAnalysis
import com.beykus.beygram.media.bgbeat.BgBeatBeat
import com.beykus.beygram.media.bgbeat.BgBeatPhraseLoudness
import com.beykus.beygram.media.bgbeat.BgBeatStatus
import com.beykus.beygram.media.bgbeat.BgBeatWaveformPeak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgRhythmEvaluatorTest {

    private fun sampleAnalysis() = BgBeatAnalysis(
        status = BgBeatStatus.OK,
        analyzed = true,
        bpm = 120.0,
        beats = listOf(
            BgBeatBeat(time = 1.0, strength = 1.0f, confidence = 0.9f, beatInBar = 1, barIndex = 0, downbeat = true, extrapolated = false),
            BgBeatBeat(time = 1.5, strength = 0.6f, confidence = 0.8f, beatInBar = 2, barIndex = 0, downbeat = false, extrapolated = false),
        ),
        waveform = listOf(
            BgBeatWaveformPeak(min = -0.2f, max = 0.3f),
            BgBeatWaveformPeak(min = -0.8f, max = 0.5f),
        ),
        waveformPeaksPerSecond = 1f,
        phraseLoudness = listOf(
            BgBeatPhraseLoudness(time = 1.0, value = 0.75f),
        ),
    )

    @Test
    fun returnsIdleWhenOffPausedReducedMotionOrUnanalyzed() {
        val analysis = sampleAnalysis()
        assertEquals(BgRhythmFrame.IDLE, BgRhythmEvaluator.evaluate(analysis, 1_050L, playing = true, mode = BgRhythmMode.Off))
        assertEquals(BgRhythmFrame.IDLE, BgRhythmEvaluator.evaluate(analysis, 1_050L, playing = false, mode = BgRhythmMode.Dynamic))
        assertEquals(BgRhythmFrame.IDLE, BgRhythmEvaluator.evaluate(analysis, 1_050L, playing = true, mode = BgRhythmMode.Dynamic, reducedMotion = true))
        assertEquals(BgRhythmFrame.IDLE, BgRhythmEvaluator.evaluate(analysis.copy(analyzed = false), 1_050L, playing = true, mode = BgRhythmMode.Dynamic))
    }

    @Test
    fun computesBeatPulseDownbeatPhraseEnergyAndSeekReset() {
        val analysis = sampleAnalysis()
        val atDownbeat = BgRhythmEvaluator.evaluate(analysis, 1_050L, playing = true, mode = BgRhythmMode.Dynamic)
        assertTrue(atDownbeat.isActive)
        assertTrue(atDownbeat.beatPulse > 0.5f)
        assertEquals(atDownbeat.beatPulse, atDownbeat.downbeatPulse, 0.001f)
        assertEquals(0.75f, atDownbeat.phraseEnergy, 0.001f)
        assertEquals(0.8f, atDownbeat.waveformLevel, 0.001f)

        val atSecondBeat = BgRhythmEvaluator.evaluate(analysis, 1_520L, playing = true, mode = BgRhythmMode.Dynamic)
        assertTrue(atSecondBeat.beatPulse > 0.2f)
        assertEquals(0f, atSecondBeat.downbeatPulse, 0.001f)

        // Seeking before first beat clears beat pulse
        val afterSeekBack = BgRhythmEvaluator.evaluate(analysis, 200L, playing = true, mode = BgRhythmMode.Dynamic)
        assertFalse(afterSeekBack.beatPulse > 0f)
    }
}
