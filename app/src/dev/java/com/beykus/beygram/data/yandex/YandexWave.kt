package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Constants of the radio («Моя волна») requests. */
internal object YandexWave {
    /** Seed of the personal wave. */
    const val MY_WAVE_SEED = "user:onyourwave"

    /** How many of the last given tracks are sent back, so the service does not repeat them. */
    const val QUEUE_HINT = 50

    /** How many empty or failed batches in a row end the attempt; the player asks again later. */
    const val MAX_EMPTY_BATCHES = 2
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
) {
    private val lock = Mutex()
    private var sessionId: String? = null

    /** Tracks the session gave, as `id:albumId`, oldest first. Also used to drop repeats. */
    private val given = LinkedHashSet<String>()

    /** Opens the session. The tracks of the first batch, or the failure the UI shows. */
    suspend fun first(): ProviderResult<List<ProviderTrack>> = lock.withLock {
        when (val batch = start(seeds, emptyList())) {
            is ProviderResult.Failure -> batch
            is ProviderResult.Success -> {
                sessionId = batch.value.sessionId
                ProviderResult.Success(accept(batch.value.tracks))
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
                val fresh = accept(batch.tracks)
                if (fresh.isNotEmpty()) return@withLock fresh
                return@repeat
            }
            sessionId = batch.sessionId
            val fresh = accept(batch.tracks)
            if (fresh.isNotEmpty()) return@withLock fresh
        }
        emptyList()
    }

    /** Keeps the tracks the session has not given before, and remembers them. */
    private fun accept(tracks: List<ProviderTrack>): List<ProviderTrack> =
        tracks.filter { given.add(queueId(it)) }

    companion object {
        /** A track as the radio requests name it: `id:albumId`, or the bare id when the track has no album. */
        fun queueId(track: ProviderTrack): String =
            track.albumId?.takeIf { it.isNotBlank() }?.let { "${track.trackId}:$it" } ?: track.trackId
    }
}
