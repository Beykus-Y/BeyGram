package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Constants of the radio («Моя волна») requests. */
internal object YandexWave {
    /** Seed of the personal wave. */
    const val MY_WAVE_SEED = "user:onyourwave"

    /** Seed of a wave built from one track; the id is the service's track id. Format not yet checked on the live API. */
    fun trackSeed(trackId: String): String = "track:$trackId"

    /** Seed of a wave built from one artist. Format not yet checked on the live API. */
    fun artistSeed(artistId: String): String = "artist:$artistId"

    /** Seed of a wave built from one album. Format not yet checked on the live API. */
    fun albumSeed(albumId: String): String = "album:$albumId"

    /** Seed of a wave built from one playlist: owner uid and kind. Format not yet checked on the live API. */
    fun playlistSeed(ownerUid: String, kind: String): String = "playlist:${ownerUid}_$kind"

    /**
     * Diversity setting of the personal wave, sent as an extra seed. Seed format not yet checked on the live API:
     * unverified on device, so a wrong value must not break the wave (the seed is simply left out by [seedsFor]).
     */
    enum class Diversity(val seed: String?) {
        DEFAULT(null),
        DISCOVER("settingDiversity:discover"),
        POPULAR("settingDiversity:popular"),
    }

    /** The seeds of a wave that starts from [base] with the chosen [diversity]. */
    fun seedsFor(base: String, diversity: Diversity): List<String> = listOfNotNull(base, diversity.seed)

    /** How many of the last given tracks are sent back, so the service does not repeat them. */
    const val QUEUE_HINT = 50

    /** How many empty or failed batches in a row end the attempt; the player asks again later. */
    const val MAX_EMPTY_BATCHES = 2

    /** Where the radio was started from, as the official mobile client reports it for the personal wave. */
    const val FROM = "radio-mobile-user-onyourwave"
}

/** One feedback event of a radio session. [timestampMs] is wall-clock time. */
internal data class YandexWaveEvent(
    val type: String,
    val timestampMs: Long,
    /** `id:albumId`; null only for [RADIO_STARTED]. */
    val trackId: String? = null,
    val playedSeconds: Double? = null,
) {
    fun toJson(): JSONObject {
        val json = JSONObject().put("type", type).put("timestamp", isoTimestamp(timestampMs))
        if (trackId != null) json.put("trackId", trackId)
        if (playedSeconds != null) json.put("totalPlayedSeconds", playedSeconds)
        return json
    }

    companion object {
        const val RADIO_STARTED = "radioStarted"
        const val TRACK_STARTED = "trackStarted"
        const val TRACK_FINISHED = "trackFinished"
        const val SKIP = "skip"
        const val LIKE = "like"
        const val UNLIKE = "unlike"

        /** ISO 8601 in UTC with milliseconds, such as `2024-01-01T12:00:00.000Z`. */
        fun isoTimestamp(ms: Long): String {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            return format.format(Date(ms))
        }
    }
}

/** A next-batch request failed; the player keeps the queue and tries again after a pause. */
internal class YandexWaveException(val failure: MusicProviderFailure) : IOException("wave batch failed: $failure")

/**
 * Where a track of the radio came from: the session and the batch that gave it. The feedback of the track belongs to
 * that session even after the radio moved on to another one, so a settings change or a replaced session does not
 * re-address it.
 */
internal data class WaveTrackOrigin(
    /** The radio's `id:albumId`, as the feedback names the track. */
    val queueId: String,
    /** The session that gave the track; null when it is unknown (a wave restored from a save) or already gone. */
    val sessionId: String?,
    /** The batch that gave the track, when the session reported one. */
    val batchId: String?,
)

/**
 * One listening of a radio: the first batch, then the next ones on request. When the service forgets the session or
 * ends it, a new session with the same seeds continues from the tracks already given, so the queue never breaks.
 * Requests are serialized; the player never asks for two batches at once, but a stale caller must not interleave.
 */
internal class YandexWaveSession(
    private val seeds: List<String>,
    private val start: suspend (seeds: List<String>, queue: List<String>) -> ProviderResult<YandexWaveBatch>,
    private val next: suspend (sessionId: String, queue: List<String>) -> ProviderResult<YandexWaveBatch>,
    /** Tracks given before a restart, as `id:albumId`, so the wave does not repeat them. */
    initialGiven: Collection<String> = emptyList(),
    /**
     * The tracks a previous session of the same radio gave, with the session and batch of each: their feedback keeps
     * going to the session that gave them after the radio is swapped to new seeds.
     */
    initialOrigins: Map<String, WaveTrackOrigin> = emptyMap(),
) {
    private val lock = Mutex()
    private var sessionId: String? = null

    /** Tracks the session gave, as `id:albumId`, oldest first. Also used to drop repeats. */
    private val given = LinkedHashSet<String>(initialGiven)

    /** Per player key: the track, the session that gave it and its batch, for feedback. */
    private val byKey = java.util.concurrent.ConcurrentHashMap<String, WaveTrackOrigin>(initialOrigins)

    /**
     * The session feedback goes to: the session currently serving the queue. Null while no session is open (a wave
     * restored from a save) or when the service forgot or ended the last one; a missing target drops the feedback.
     */
    @Volatile
    var currentSessionId: String? = null
        private set

    /** The radio's `id:albumId` and batch of a player key, or null when this session did not give that track. */
    fun trackOf(key: String): Pair<String, String?>? = byKey[key]?.let { it.queueId to it.batchId }

    /** The session and batch a player key came from, or null when this wave did not give that track. */
    fun originOf(key: String): WaveTrackOrigin? = byKey[key]

    /** The session and batch of a track id the wave gave, for like events. */
    fun originOfTrackId(trackId: String): WaveTrackOrigin? =
        byKey.values.firstOrNull { it.queueId == trackId || it.queueId.startsWith("$trackId:") }

    /** The tracks given so far with their sessions and batches, for the next session of the same radio. */
    fun origins(): Map<String, WaveTrackOrigin> = byKey.toMap()

    /** Opens the session. The tracks of the first batch, or the failure the UI shows. */
    suspend fun first(): ProviderResult<List<ProviderTrack>> = lock.withLock {
        when (val batch = start(seeds, emptyList())) {
            is ProviderResult.Failure -> batch
            is ProviderResult.Success -> {
                // A session the service already ended or does not know can take neither a next request nor feedback.
                val live = batch.value.sessionId.takeIf { !batch.value.unknownSession && !batch.value.terminated }
                sessionId = live
                currentSessionId = live
                // Without a session its batch cannot be named in feedback either.
                ProviderResult.Success(accept(batch.value.tracks, batch.value.batchId.takeIf { live != null }, live))
            }
        }
    }

    /**
     * The next tracks for the player. Throws [YandexWaveException] on a failed request, so the player retries later;
     * returns an empty list when the service has nothing new right now.
     */
    suspend fun more(): List<ProviderTrack> = lock.withLock {
        repeat(YandexWave.MAX_EMPTY_BATCHES) {
            val id = sessionId
            val hint = queueHint()
            val result = if (id == null) start(seeds, hint) else next(id, hint)
            val batch = when (result) {
                is ProviderResult.Failure -> throw YandexWaveException(result.failure)
                is ProviderResult.Success -> result.value
            }
            if (batch.unknownSession || batch.terminated) {
                // The tracks of an ended session may still be fine, but neither the next request nor feedback may go to
                // a session the service forgot or ended: the next request opens a new one.
                sessionId = null
                currentSessionId = null
                // Its batch cannot be named in feedback to another session either.
                val fresh = accept(batch.tracks, batchId = null, sessionId = null)
                if (fresh.isNotEmpty()) return@withLock fresh
                return@repeat
            }
            sessionId = batch.sessionId
            currentSessionId = batch.sessionId
            val fresh = accept(batch.tracks, batch.batchId, batch.sessionId)
            if (fresh.isNotEmpty()) return@withLock fresh
        }
        // Reset the exhausted session so the next recovery attempt opens a fresh session with the latest queue hint.
        sessionId = null
        emptyList()
    }

    /** Most recent [YandexWave.QUEUE_HINT] track IDs, so the service avoids repeats without unbounded request payloads. */
    private fun queueHint(): List<String> = synchronized(given) {
        if (given.size <= YandexWave.QUEUE_HINT) given.toList() else given.toList().takeLast(YandexWave.QUEUE_HINT)
    }

    /** The tracks given so far, as `id:albumId`, oldest first, for saving the wave. */
    fun givenIds(): List<String> = synchronized(given) { given.toList() }

    /** Marks a track restored from a saved wave as given and known, so its feedback has a key. */
    fun seed(track: ProviderTrack) {
        val id = queueId(track)
        synchronized(given) { given.add(id) }
        // The session that gave it ended with the previous process, so nothing may be reported to a new one on its behalf.
        byKey[track.key] = WaveTrackOrigin(id, sessionId = null, batchId = null)
    }

    /** Keeps the tracks the session has not given before, and remembers them with the session and batch that gave them. */
    private fun accept(tracks: List<ProviderTrack>, batchId: String?, sessionId: String?): List<ProviderTrack> =
        tracks.filter { track ->
            val id = queueId(track)
            synchronized(given) { given.add(id) }.also { fresh ->
                if (fresh) byKey[track.key] = WaveTrackOrigin(id, sessionId, batchId)
            }
        }

    companion object {
        /** A track as the radio requests name it: `id:albumId`, or the bare id when the track has no album. */
        fun queueId(track: ProviderTrack): String =
            track.albumId?.takeIf { it.isNotBlank() }?.let { "${track.trackId}:$it" } ?: track.trackId
    }
}
