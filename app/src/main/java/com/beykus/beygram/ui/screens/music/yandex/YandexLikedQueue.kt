package com.beykus.beygram.ui.screens.music.yandex

import com.beykus.beygram.data.music.provider.ProviderTrack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive

/**
 * The whole liked list, loading in the background for one [owner] (the provider) and one copy of the liked [ids]. [publish]
 * receives the tracks only while the load is current: [retire] cancels a load made for another owner or other ids, so its
 * tracks never reach the cache or a queue of the newer session.
 */
internal class LikedPreload<O : Any>(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    val owner: O,
    /** The liked ids the load was started for: the caller passes an immutable copy, so a later change cannot reach it. */
    val ids: List<String>,
    fetch: suspend (List<String>) -> List<ProviderTrack>?,
    publish: (List<ProviderTrack>) -> Unit,
) {
    val tracks: Deferred<List<ProviderTrack>?> = scope.async(dispatcher) {
        val list = fetch(ids) ?: return@async null
        // A load retired while its request was in flight publishes nothing.
        ensureActive()
        publish(list)
        list
    }

    /** Whether this load was made for another [owner], or for other liked [ids] when those are given. */
    fun isStaleFor(owner: O, ids: List<String>?): Boolean =
        this.owner !== owner || (ids != null && this.ids != ids)

    /** Cancels the request; the load publishes nothing from now on. */
    fun retire() {
        tracks.cancel()
    }
}

/**
 * The tracks of [full] after the tapped one that the queue does not hold yet ([queued] holds the ids it started with),
 * in list order. Unavailable tracks are left out.
 */
internal fun likedRestAfter(full: List<ProviderTrack>, tappedId: String, queued: Set<String>): List<ProviderTrack> {
    val tappedAt = full.indexOfFirst { it.trackId == tappedId }
    if (tappedAt < 0) return emptyList()
    return full.drop(tappedAt + 1).filter { it.isAvailable && it.trackId !in queued }
}

/**
 * A tap on a row of the whole liked list. [startNow] plays the loaded tracks from the tapped one at once and reports the keys
 * of the queue it left playing with the queue generation. Only a queue that holds exactly [headKeys] is the one this tap
 * started: a track that already plays in BG Mix, or a restored queue, keeps its own, and nothing joins it. Once [loadAll] has
 * the whole list, [appendRest] receives the tracks after the tapped one with that same generation, so the queue takes them
 * only while it is still the one started.
 */
internal suspend fun playLikedTap(
    tappedId: String,
    headKeys: List<String>,
    queued: Set<String>,
    startNow: () -> Pair<List<String>, Long>,
    loadAll: suspend () -> List<ProviderTrack>?,
    appendRest: (generation: Long, rest: List<ProviderTrack>) -> Unit,
) {
    val (keys, generation) = startNow()
    if (keys != headKeys) return
    val full = loadAll() ?: return
    appendRest(generation, likedRestAfter(full, tappedId, queued))
}
