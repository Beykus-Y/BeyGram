package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack

/**
 * Holds the session a radio's queue loads from. [swap] opens a session for new seeds that carries over the tracks already
 * given, with their sessions and batches; the current session is replaced only once the new one has opened, and only
 * when no newer swap superseded this one, so a failure keeps the radio as it was.
 */
internal class YandexWaveRouter(
    initial: YandexWaveSession,
    private val openSession: (
        seeds: List<String>,
        given: Collection<String>,
        origins: Map<String, WaveTrackOrigin>,
    ) -> YandexWaveSession,
) {
    @Volatile
    var current: YandexWaveSession = initial
        private set

    /** The next tracks of the radio, from whichever session is current. */
    suspend fun more(): List<ProviderTrack> = current.more()

    /**
     * The first batch of a session for [seeds]; on success that session becomes the current one. [isCurrent] is asked
     * after the request returned: a swap superseded by a newer one (fast settings changes) returns null and leaves the
     * session of the newer choice in place.
     */
    suspend fun swap(
        seeds: List<String>,
        isCurrent: () -> Boolean = { true },
    ): ProviderResult<List<ProviderTrack>>? {
        val base = current
        val fresh = openSession(seeds, base.givenIds(), base.origins())
        val first = fresh.first()
        if (first !is ProviderResult.Success) return first
        if (!isCurrent()) return null
        current = fresh
        return first
    }
}