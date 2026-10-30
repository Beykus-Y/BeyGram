package com.beykus.beygram.media.bgbeat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgBeatJsonTest {

    @Test
    fun parseOkResultWithBeatsAndWaveform() {
        val json = """
        {
          "status": "ok",
          "cacheHit": true,
          "schemaVersion": 1,
          "analyzerVersion": 1,
          "contentHash": "4c53841cca9a4d6c",
          "configHash": "0123456789abcdef",
          "duration": 182.5,
          "sampleRate": 44100,
          "sourceChannels": 2,
          "bpm": 128.0,
          "bpmConfidence": 0.92,
          "bpmLevelConfidence": 0.88,
          "beatPhaseConfidence": 0.95,
          "downbeatConfidence": 0.80,
          "tempoStable": true,
          "octaveAmbiguous": false,
          "beatsPerBar": 4,
          "meterConfidence": 0.85,
          "analysisSeconds": 1.42,
          "activeRatio": 0.99,
          "bpmCandidates": [
            {"bpm": 128.0, "score": 1.0},
            {"bpm": 64.0, "score": 0.5}
          ],
          "beats": [
            {
              "time": 0.5,
              "strength": 0.9,
              "confidence": 0.95,
              "beatInBar": 0,
              "barIndex": 0,
              "downbeat": true,
              "extrapolated": false
            },
            {
              "time": 0.96875,
              "strength": 0.7,
              "confidence": 0.90,
              "beatInBar": 1,
              "barIndex": 0,
              "downbeat": false,
              "extrapolated": false
            }
          ],
          "waveform": [
            {"min": -0.45, "max": 0.52},
            {"min": -0.80, "max": 0.75}
          ],
          "waveformPeaksPerSecond": 100.0
        }
        """.trimIndent()

        val result = BgBeatAnalysis.fromJson(json)

        assertEquals(BgBeatStatus.OK, result.status)
        assertTrue(result.cacheHit)
        assertEquals(1, result.schemaVersion)
        assertEquals(1, result.analyzerVersion)
        assertEquals("4c53841cca9a4d6c", result.contentHash)
        assertEquals(182.5, result.duration, 0.001)
        assertEquals(44100, result.sampleRate)
        assertEquals(2, result.channels)
        assertEquals(128.0, result.bpm, 0.001)
        assertEquals(0.92f, result.bpmConfidence, 0.01f)
        assertTrue(result.tempoStable)
        assertFalse(result.octaveAmbiguous)
        assertEquals(4, result.beatsPerBar)
        assertEquals(2, result.beats.size)
        assertEquals(0.5, result.beats[0].time, 0.001)
        assertTrue(result.beats[0].downbeat)
        assertEquals(0, result.beats[0].beatInBar)
        assertEquals(2, result.waveform.size)
        assertEquals(-0.45f, result.waveform[0].min, 0.01f)
        assertEquals(0.52f, result.waveform[0].max, 0.01f)
        assertEquals(2, result.bpmCandidates.size)
        assertEquals(128.0, result.bpmCandidates[0].bpm, 0.001)
    }

    @Test
    fun parseCancelledResult() {
        val json = """
        {
          "status": "cancelled",
          "message": "Analysis cancelled"
        }
        """.trimIndent()

        val result = BgBeatAnalysis.fromJson(json)

        assertEquals(BgBeatStatus.CANCELLED, result.status)
        assertEquals("Analysis cancelled", result.message)
    }

    @Test
    fun parseUnsupportedResult() {
        val json = """
        {
          "status": "unsupported",
          "message": "Unsupported audio format"
        }
        """.trimIndent()

        val result = BgBeatAnalysis.fromJson(json)

        assertEquals(BgBeatStatus.UNSUPPORTED, result.status)
        assertEquals("Unsupported audio format", result.message)
    }

    @Test
    fun parseErrorResult() {
        val json = """
        {
          "status": "error",
          "message": "Decoder failed: corrupt data"
        }
        """.trimIndent()

        val result = BgBeatAnalysis.fromJson(json)

        assertEquals(BgBeatStatus.ERROR, result.status)
        assertEquals("Decoder failed: corrupt data", result.message)
    }
}
