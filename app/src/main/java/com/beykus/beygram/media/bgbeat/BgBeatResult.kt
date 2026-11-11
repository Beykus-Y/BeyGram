package com.beykus.beygram.media.bgbeat

import org.json.JSONObject
import kotlin.math.abs

enum class BgBeatStatus {
    OK,
    CANCELLED,
    UNSUPPORTED,
    ERROR
}

data class BgBeatWaveformPeak(
    val min: Float,
    val max: Float
)

data class BgBeatBeat(
    val time: Double,
    val strength: Float,
    val confidence: Float,
    val beatInBar: Int,
    val barIndex: Int,
    val downbeat: Boolean,
    val extrapolated: Boolean
)

data class BgBeatBpmCandidate(
    val bpm: Double,
    val score: Float
)

/** Loudness of the phrase at a downbeat: [value] is 0 (quiet) .. 1 (loud), or -1 when unknown. */
data class BgBeatPhraseLoudness(
    val time: Double,
    val value: Float
)

data class BgBeatAnalysis(
    val status: BgBeatStatus,
    val message: String = "",
    val cacheHit: Boolean = false,
    val schemaVersion: Int = 1,
    val analyzerVersion: Int = 1,
    val duration: Double = 0.0,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val contentHash: String = "",
    val configHash: String = "",
    val bpm: Double = 0.0,
    val bpmConfidence: Float = 0f,
    val bpmLevelConfidence: Float = 0f,
    val beatPhaseConfidence: Float = 0f,
    val downbeatConfidence: Float = 0f,
    val tempoStable: Boolean = true,
    val octaveAmbiguous: Boolean = false,
    val beatsPerBar: Int = 4,
    val meterConfidence: Float = 0f,
    val analysisSeconds: Double = 0.0,
    val activeRatio: Double = 0.0,
    val beats: List<BgBeatBeat> = emptyList(),
    val waveform: List<BgBeatWaveformPeak> = emptyList(),
    val waveformPeaksPerSecond: Float = 0f,
    val bpmCandidates: List<BgBeatBpmCandidate> = emptyList(),
    /** True when BG Beat built the musical timeline from the track, so its loudness data is evidence. */
    val analyzed: Boolean = false,
    val phraseLoudness: List<BgBeatPhraseLoudness> = emptyList()
) {
    /**
     * Loudness of the phrase nearest to [time] (seconds), 0..1, or null when the track has no
     * loudness evidence (not analysed, or no dynamics).
     */
    fun phraseLoudnessAt(time: Double): Float? {
        if (!analyzed || phraseLoudness.isEmpty()) return null
        val nearest = phraseLoudness.minByOrNull { abs(it.time - time) } ?: return null
        return nearest.value.takeIf { it >= 0f }
    }

    companion object {
        fun fromJson(jsonStr: String): BgBeatAnalysis {
            val root = JSONObject(jsonStr)
            val statusStr = root.optString("status", "error")
            val status = when (statusStr.lowercase()) {
                "ok" -> BgBeatStatus.OK
                "cancelled" -> BgBeatStatus.CANCELLED
                "unsupported" -> BgBeatStatus.UNSUPPORTED
                else -> BgBeatStatus.ERROR
            }
            val message = root.optString("message", "")

            if (status != BgBeatStatus.OK) {
                return BgBeatAnalysis(status = status, message = message)
            }

            val beatsArray = root.optJSONArray("beats")
            val beats = mutableListOf<BgBeatBeat>()
            if (beatsArray != null) {
                for (i in 0 until beatsArray.length()) {
                    val b = beatsArray.optJSONObject(i) ?: continue
                    beats.add(
                        BgBeatBeat(
                            time = b.optDouble("time", 0.0),
                            strength = b.optDouble("strength", 0.0).toFloat(),
                            confidence = b.optDouble("confidence", 0.0).toFloat(),
                            beatInBar = b.optInt("beatInBar", 0),
                            barIndex = b.optInt("barIndex", 0),
                            downbeat = b.optBoolean("downbeat", false),
                            extrapolated = b.optBoolean("extrapolated", false)
                        )
                    )
                }
            }

            val waveformArray = root.optJSONArray("waveform")
            val waveform = mutableListOf<BgBeatWaveformPeak>()
            if (waveformArray != null) {
                for (i in 0 until waveformArray.length()) {
                    val p = waveformArray.optJSONObject(i) ?: continue
                    waveform.add(
                        BgBeatWaveformPeak(
                            min = p.optDouble("min", 0.0).toFloat(),
                            max = p.optDouble("max", 0.0).toFloat()
                        )
                    )
                }
            }

            val bpmCandidatesArray = root.optJSONArray("bpmCandidates")
            val bpmCandidates = mutableListOf<BgBeatBpmCandidate>()
            if (bpmCandidatesArray != null) {
                for (i in 0 until bpmCandidatesArray.length()) {
                    val c = bpmCandidatesArray.optJSONObject(i) ?: continue
                    bpmCandidates.add(
                        BgBeatBpmCandidate(
                            bpm = c.optDouble("bpm", 0.0),
                            score = c.optDouble("score", 0.0).toFloat()
                        )
                    )
                }
            }

            val loudnessArray = root.optJSONArray("phraseLoudness")
            val phraseLoudness = mutableListOf<BgBeatPhraseLoudness>()
            if (loudnessArray != null) {
                for (i in 0 until loudnessArray.length()) {
                    val l = loudnessArray.optJSONObject(i) ?: continue
                    phraseLoudness.add(
                        BgBeatPhraseLoudness(
                            time = l.optDouble("time", 0.0),
                            value = l.optDouble("value", -1.0).toFloat()
                        )
                    )
                }
            }

            return BgBeatAnalysis(
                status = status,
                message = message,
                cacheHit = root.optBoolean("cacheHit", false),
                schemaVersion = root.optInt("schemaVersion", 1),
                analyzerVersion = root.optInt("analyzerVersion", 1),
                duration = root.optDouble("duration", 0.0),
                sampleRate = root.optInt("sampleRate", 0),
                channels = root.optInt("sourceChannels", 0),
                contentHash = root.optString("contentHash", ""),
                configHash = root.optString("configHash", ""),
                bpm = root.optDouble("bpm", 0.0),
                bpmConfidence = root.optDouble("bpmConfidence", 0.0).toFloat(),
                bpmLevelConfidence = root.optDouble("bpmLevelConfidence", 0.0).toFloat(),
                beatPhaseConfidence = root.optDouble("beatPhaseConfidence", 0.0).toFloat(),
                downbeatConfidence = root.optDouble("downbeatConfidence", 0.0).toFloat(),
                tempoStable = root.optBoolean("tempoStable", true),
                octaveAmbiguous = root.optBoolean("octaveAmbiguous", false),
                beatsPerBar = root.optInt("beatsPerBar", 4),
                meterConfidence = root.optDouble("meterConfidence", 0.0).toFloat(),
                analysisSeconds = root.optDouble("analysisSeconds", 0.0),
                activeRatio = root.optDouble("activeRatio", 0.0),
                beats = beats,
                waveform = waveform,
                waveformPeaksPerSecond = root.optDouble("waveformPeaksPerSecond", 0.0).toFloat(),
                bpmCandidates = bpmCandidates,
                analyzed = root.optBoolean("analyzed", false),
                phraseLoudness = phraseLoudness
            )
        }
    }
}
