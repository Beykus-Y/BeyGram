package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack

/**
 * Holds the session a radio's queue loads from. [swap] opens a session for new seeds that carries over the tracks already
 * given; the current session is replaced only once the new one has opened, so a failure keeps the radio as it was.
 */
internal class YandexWaveRouter(
    initial: YandexWaveSession,
    private val openSession: (seeds: List<String>, given: Collection<String>) -> YandexWaveSession,
) {
    @Volatile
    var current: YandexWaveSession = initial
        private set

    /** The next tracks of the radio, from whichever session is current. */
    suspend fun more(): List<ProviderTrack> = current.more()

    /** The first batch of a session for [seeds]; on success that session becomes the current one. */
    suspend fun swap(seeds: List<String>): ProviderResult<List<ProviderTrack>> {
        val fresh = openSession(seeds, current.givenIds())
        val first = fresh.first()
        if (first is ProviderResult.Success) current = fresh
        return first
    }
}
