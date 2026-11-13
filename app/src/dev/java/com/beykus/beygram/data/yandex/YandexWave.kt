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
) {
    private val lock = Mutex()
    private var sessionId: String? = null

    /** Tracks the session gave, as `id:albumId`, oldest first. Also used to drop repeats. */
    private val given = LinkedHashSet<String>(initialGiven)

    /** Per player key: the track as `id:albumId` and the batch it came in, for feedback. */
    private val byKey = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String?>>()

    /** The session feedback goes to; changes when a forgotten session is replaced. */
    @Volatile
    var currentSessionId: String? = null
        private set

    /** The radio's `id:albumId` and batch of a player key, or null when this session did not give that track. */
    fun trackOf(key: String): Pair<String, String?>? = byKey[key]

    /** The batch the track `id:albumId` came in. */
    fun batchOfTrack(queueId: String): String? = byKey.values.firstOrNull { it.first == queueId }?.second

    /** The radio's `id:albumId` of a track id the session gave, for like events. */
    fun trackIdOf(trackId: String): String? =
        byKey.values.firstOrNull { it.first == trackId || it.first.startsWith("$trackId:") }?.first

    /** Opens the session. The tracks of the first batch, or the failure the UI shows. */
    suspend fun first(): ProviderResult<List<ProviderTrack>> = lock.withLock {
        when (val batch = start(seeds, emptyList())) {
            is ProviderResult.Failure -> batch
            is ProviderResult.Success -> {
                sessionId = batch.value.sessionId
                currentSessionId = batch.value.sessionId
                ProviderResult.Success(accept(batch.value.tracks, batch.value.batchId))
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
            val result = if (id == null) start(seeds, given.toList()) else next(id, given.toList())
            val batch = when (result) {
                is ProviderResult.Failure -> throw YandexWaveException(result.failure)
                is ProviderResult.Success -> result.value
            }
            if (batch.unknownSession || batch.terminated) {
                // The tracks of an ended session may still be fine, but the next request needs a new session.
                sessionId = null
                val fresh = accept(batch.tracks, batch.batchId)
                if (fresh.isNotEmpty()) return@withLock fresh
                return@repeat
            }
            sessionId = batch.sessionId
            currentSessionId = batch.sessionId
            val fresh = accept(batch.tracks, batch.batchId)
            if (fresh.isNotEmpty()) return@withLock fresh
        }
        emptyList()
    }

    /** The tracks given so far, as `id:albumId`, oldest first, for saving the wave. */
    fun givenIds(): List<String> = synchronized(given) { given.toList() }

    /** Marks a track restored from a saved wave as given and known, so its feedback has a key. */
    fun seed(track: ProviderTrack) {
        val id = queueId(track)
        synchronized(given) { given.add(id) }
        byKey[track.key] = id to null
    }

    /** Keeps the tracks the session has not given before, and remembers them. */
    private fun accept(tracks: List<ProviderTrack>, batchId: String?): List<ProviderTrack> =
        tracks.filter { track ->
            val id = queueId(track)
            synchronized(given) { given.add(id) }.also { fresh -> if (fresh) byKey[track.key] = id to batchId }
        }

    companion object {
        /** A track as the radio requests name it: `id:albumId`, or the bare id when the track has no album. */
        fun queueId(track: ProviderTrack): String =
            track.albumId?.takeIf { it.isNotBlank() }?.let { "${track.trackId}:$it" } ?: track.trackId
    }
}
