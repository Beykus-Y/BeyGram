package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MusicBotTrack

private const val BOT_KEY_PREFIX = "bot:"

/**
 * Turns the results of a music bot search into a real queue. Rows for bot results are resolved by [LazyTrackResolver]:
 * the copy is fetched from the bot and kept in the bot cache.
 */
internal object BotTrackResolver : LazyTrackResolver<MusicBotTrack>(BOT_KEY_PREFIX) {
    const val KEY_PREFIX = BOT_KEY_PREFIX

    override val restricted: Boolean = false

    /** The screen that owns the search supplies the fetch; it stays valid for the queue built from that search. */
    fun configure(fetcher: suspend (queryId: Long, resultId: String) -> Result<MusicBotTrack?>) {
        setFetch { key ->
            val (queryId, resultId) = parse(key) ?: return@setFetch Result.success(null)
            fetcher(queryId, resultId)
        }
    }

    fun keyOf(queryId: Long, resultId: String): String = "$KEY_PREFIX$queryId:$resultId"

    override fun pathOf(track: MusicBotTrack): String = track.path

    override fun discardCopy(path: String) = MusicBotCache.discard(path)

    override fun handOverCopy(path: String) = MusicBotCache.handOver(path)

    private fun parse(key: String): Pair<Long, String>? {
        val rest = key.removePrefix(KEY_PREFIX)
        val split = rest.indexOf(':')
        if (split <= 0) return null
        val queryId = rest.substring(0, split).toLongOrNull() ?: return null
        val resultId = rest.substring(split + 1)
        return if (resultId.isEmpty()) null else queryId to resultId
    }
}
