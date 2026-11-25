package com.beykus.beygram.media.bgrhythm

import com.beykus.beygram.media.bgbeat.BgBeatAnalysis
import com.beykus.beygram.media.bgbeat.BgBeatBeat
import com.beykus.beygram.media.bgbeat.BgBeatStatus
import kotlin.math.abs

enum class BgRhythmMode {
    Off,
    Calm,
    Dynamic,
}

data class BgRhythmFrame(
    val beatPulse: Float,
    val downbeatPulse: Float,
    val phraseEnergy: Float,
    val waveformLevel: Float,
    val isActive: Boolean,
) {
    companion object {
        val IDLE = BgRhythmFrame(
            beatPulse = 0f,
            downbeatPulse = 0f,
            phraseEnergy = 0f,
            waveformLevel = 0f,
            isActive = false,
        )
    }
}

/**
 * Pure, allocation-free evaluator of BG Rhythm visual state from an existing [BgBeatAnalysis].
 * Does not run any FFT or decode audio, and emits [BgRhythmFrame.IDLE] when disabled or paused.
 */
object BgRhythmEvaluator {

    private const val MIN_CONFIDENCE = 0.15f
    private const val CALM_DECAY_SEC = 0.26
    private const val DYNAMIC_DECAY_SEC = 0.34

    fun evaluate(
        analysis: BgBeatAnalysis?,
        positionMs: Long,
        playing: Boolean,
        mode: BgRhythmMode,
        reducedMotion: Boolean = false,
    ): BgRhythmFrame {
        if (mode == BgRhythmMode.Off || !playing || reducedMotion || positionMs < 0L) {
            return BgRhythmFrame.IDLE
        }
        if (analysis == null || analysis.status != BgBeatStatus.OK || !analysis.analyzed) {
            return BgRhythmFrame.IDLE
        }

        val posSec = positionMs / 1_000.0
        val decaySec = if (mode == BgRhythmMode.Dynamic) DYNAMIC_DECAY_SEC else CALM_DECAY_SEC
        val intensityScale = if (mode == BgRhythmMode.Dynamic) 1f else 0.55f

        val beat = latestBeatAt(analysis.beats, posSec)
        var beatPulse = 0f
        var downbeatPulse = 0f
        if (beat != null && beat.confidence >= MIN_CONFIDENCE) {
            val dt = posSec - beat.time
            if (dt in 0.0..decaySec) {
                val envelope = (1.0 - dt / decaySec).toFloat()
                val raw = (envelope * envelope * beat.strength.coerceIn(0f, 1f) * beat.confidence.coerceIn(0f, 1f) * intensityScale)
                    .coerceIn(0f, 1f)
                beatPulse = raw
                if (beat.downbeat) {
                    downbeatPulse = raw
                }
            }
        }

        val phraseEnergy = ((analysis.phraseLoudnessAt(posSec) ?: 0f) * intensityScale).coerceIn(0f, 1f)
        val waveformLevel = waveformLevelAt(analysis, posSec)

        return BgRhythmFrame(
            beatPulse = beatPulse,
            downbeatPulse = downbeatPulse,
            phraseEnergy = phraseEnergy,
            waveformLevel = waveformLevel,
            isActive = true,
        )
    }

    private fun latestBeatAt(beats: List<BgBeatBeat>, posSec: Double): BgBeatBeat? {
        if (beats.isEmpty() || posSec < beats[0].time) return null
        var low = 0
        var high = beats.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (beats[mid].time <= posSec) low = mid else high = mid - 1
        }
        return beats[low]
    }

    private fun waveformLevelAt(analysis: BgBeatAnalysis, posSec: Double): Float {
        if (analysis.waveform.isEmpty() || analysis.waveformPeaksPerSecond <= 0f) return 0f
        val idx = (posSec * analysis.waveformPeaksPerSecond).toInt().coerceIn(0, analysis.waveform.lastIndex)
        val peak = analysis.waveform[idx]
        return maxOf(abs(peak.min), abs(peak.max)).coerceIn(0f, 1f)
    }
}
