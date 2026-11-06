package com.beykus.beygram.media.bgmix

import org.json.JSONObject

data class BgMixDeck(
    val loaded: Boolean,
    val playing: Boolean,
    val ended: Boolean,
    val positionSeconds: Double,
    val durationSeconds: Double,
    val bpm: Double,
)

data class BgMixQueueItem(
    val id: Long,
    val name: String,
    /** One of: pending, preparing, ready, failed, playing, played. */
    val state: String,
    val progress: Float,
    val bpm: Double,
    val durationSeconds: Double,
    val error: String,
)

/** Snapshot of the native MixSession, parsed from the JSON produced by bgmix_jni.cpp. */
data class BgMixStatus(
    val paused: Boolean,
    val master: Int,
    val transitionActive: Boolean,
    val transitionAvailable: Boolean,
    val transitionType: String,
    val transitionProgress: Double,
    val preparingNext: Boolean,
    val preparingName: String,
    val preparingProgress: Float,
    val lastError: String,
    val status: String,
    val decks: List<BgMixDeck>,
    val queue: List<BgMixQueueItem>,
) {
    companion object {
        /** Returns null for malformed input instead of throwing, so one bad tick never stops playback. */
        fun parse(bytes: ByteArray?): BgMixStatus? {
            if (bytes == null || bytes.isEmpty()) return null
            return try {
                val root = JSONObject(String(bytes, Charsets.UTF_8))
                val decksJson = root.optJSONArray("decks")
                val queueJson = root.optJSONArray("queue")
                BgMixStatus(
                    paused = root.optBoolean("paused", false),
                    master = root.optInt("master", 0),
                    transitionActive = root.optBoolean("transitionActive", false),
                    transitionAvailable = root.optBoolean("transitionAvailable", false),
                    transitionType = root.optString("transitionType", "off"),
                    transitionProgress = root.optDouble("transitionProgress", 0.0),
                    preparingNext = root.optBoolean("preparingNext", false),
                    preparingName = root.optString("preparingName", ""),
                    preparingProgress = root.optDouble("preparingProgress", 0.0).toFloat(),
                    lastError = root.optString("lastError", ""),
                    status = root.optString("status", ""),
                    decks = List(decksJson?.length() ?: 0) { i ->
                        val d = decksJson!!.getJSONObject(i)
                        BgMixDeck(
                            loaded = d.optBoolean("loaded", false),
                            playing = d.optBoolean("playing", false),
                            ended = d.optBoolean("ended", false),
                            positionSeconds = d.optDouble("positionSeconds", 0.0),
                            durationSeconds = d.optDouble("durationSeconds", 0.0),
                            bpm = d.optDouble("bpm", 0.0),
                        )
                    },
                    queue = List(queueJson?.length() ?: 0) { i ->
                        val q = queueJson!!.getJSONObject(i)
                        BgMixQueueItem(
                            id = q.optLong("id", 0L),
                            name = q.optString("name", ""),
                            state = q.optString("state", "pending"),
                            progress = q.optDouble("progress", 0.0).toFloat(),
                            bpm = q.optDouble("bpm", 0.0),
                            durationSeconds = q.optDouble("durationSeconds", 0.0),
                            error = q.optString("error", ""),
                        )
                    },
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
