package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.ProviderTrack
import org.json.JSONArray
import org.json.JSONObject

/**
 * Decides when a «Моя волна» may act for an account. A running wave is bound to the sign-in generation it started under
 * (see [YandexMusicProvider.accountGeneration]): signing out, or signing in again, even as the same account, ends it,
 * nothing is written for it, and its requests are refused. The saved place on disk is bound to the account uid, so it
 * can be restored after a restart. Pure functions, so they can be tested without the player.
 */
internal object YandexWaveAccountGuard {
    /** The uid of the signed-in account, or null when signed out or the state is unknown. */
    fun uidOf(state: MusicProviderState?): String? = (state as? MusicProviderState.SignedIn)?.account?.uid

    /** True when the wave of [activeGeneration] must end: the source is signed out, or the sign-in changed. */
    fun shouldEndWave(activeGeneration: Long?, currentGeneration: Long?): Boolean =
        activeGeneration != null && currentGeneration != activeGeneration

    /** True when the wave of [activeGeneration] may write its place: the sign-in is still the same one. */
    fun canSave(activeGeneration: Long?, currentGeneration: Long?): Boolean =
        activeGeneration != null && currentGeneration == activeGeneration

    /** True when a saved wave of [savedUid] may be restored: the source is signed in as the same account. */
    fun canRestore(savedUid: String, state: MusicProviderState?): Boolean = uidOf(state) == savedUid
}

/** Reads and writes a saved wave as a JSON string. A record of another version, or without an account, is rejected. */
internal object YandexWaveSavedCodec {
    const val VERSION = 2
    const val MAX_POSITION_MS = 86_400_000L

    fun serialize(value: YandexWaveResume.Saved): String = JSONObject()
        .put("version", VERSION)
        .put("uid", value.uid)
        .put("saved_at", value.savedAtMs)
        .put("seeds", JSONArray(value.seeds))
        .put("subtitle", value.subtitle)
        .put("track", trackToJson(value.track))
        .put("position", value.positionMs)
        .put("given", JSONArray(value.given.takeLast(YandexWave.QUEUE_HINT)))
        .toString()

    /** The saved wave; throws when the record is of another version or cannot be read. */
    fun parse(raw: String): YandexWaveResume.Saved {
        val json = JSONObject(raw)
        require(json.getInt("version") == VERSION)
        val uid = json.getString("uid")
        require(uid.isNotEmpty())
        val seeds = json.getJSONArray("seeds").strings()
        require(seeds.isNotEmpty())
        return YandexWaveResume.Saved(
            uid = uid,
            seeds = seeds,
            subtitle = json.getString("subtitle"),
            track = trackFromJson(json.getJSONObject("track")),
            positionMs = json.getLong("position").coerceIn(0L, MAX_POSITION_MS),
            given = json.getJSONArray("given").strings().takeLast(YandexWave.QUEUE_HINT),
            savedAtMs = json.getLong("saved_at"),
        )
    }

    private fun trackToJson(track: ProviderTrack): JSONObject = JSONObject()
        .put("id", track.trackId)
        .put("title", track.title)
        .put("artists", JSONArray(track.artists))
        .put("duration", track.durationMs)
        .put("available", track.isAvailable)
        .apply {
            track.albumTitle?.let { put("album", it) }
            track.albumId?.let { put("albumId", it) }
            track.coverUrl?.let { put("cover", it) }
        }

    private fun trackFromJson(json: JSONObject): ProviderTrack = ProviderTrack(
        trackId = json.getString("id"),
        title = json.getString("title"),
        artists = json.getJSONArray("artists").strings(),
        albumTitle = json.optStringOrNull("album"),
        durationMs = json.getLong("duration"),
        coverUrl = json.optStringOrNull("cover"),
        isAvailable = json.getBoolean("available"),
        albumId = json.optStringOrNull("albumId"),
    )

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    private fun JSONObject.optStringOrNull(name: String): String? = if (has(name)) getString(name) else null
}
