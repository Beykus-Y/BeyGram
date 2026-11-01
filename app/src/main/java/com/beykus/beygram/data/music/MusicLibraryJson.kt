package com.beykus.beygram.data.music

import com.beykus.beygram.playback.LEGACY_PLAYBACK_ACCOUNT_ID
import com.beykus.beygram.playback.PlaybackOrigin
import org.json.JSONArray
import org.json.JSONObject

/** Result of reading one library document. Only [Decoded] carries data that may be shown or saved again. */
sealed interface MusicLibraryDecodeResult {
    data class Decoded(val library: MusicLibrary) : MusicLibraryDecodeResult

    /** Written by a newer schema. The document has to stay exactly as it is. */
    data class UnsupportedVersion(val version: Int) : MusicLibraryDecodeResult

    data class Corrupt(val reason: String) : MusicLibraryDecodeResult
}

/**
 * JSON layer of the per-account library file. It has no Android dependency, so the format is covered by plain JVM tests.
 *
 * Writing always uses [MUSIC_LIBRARY_SCHEMA_VERSION]. Reading skips a single malformed playlist, track, stats or history
 * entry, but a document whose top level is broken is reported as [MusicLibraryDecodeResult.Corrupt] so the caller can keep
 * a backup. Optional fields may be missing or null; missing required fields make the entry malformed.
 */
object MusicLibraryJson {
    fun encode(library: MusicLibrary): String = JSONObject()
        .put("schemaVersion", MUSIC_LIBRARY_SCHEMA_VERSION)
        .put("accountId", library.accountId)
        .put("playlists", JSONArray().apply { library.playlists.forEach { put(playlistToJson(it)) } })
        .put("stats", JSONArray().apply { library.stats.values.forEach { put(statsToJson(it)) } })
        .put("recent", JSONArray().apply { library.recent.forEach { put(historyToJson(it)) } })
        .toString()

    fun decode(text: String): MusicLibraryDecodeResult = try {
        decodeRoot(JSONObject(text))
    } catch (failure: Exception) {
        MusicLibraryDecodeResult.Corrupt(failure.javaClass.simpleName)
    }

    private fun decodeRoot(root: JSONObject): MusicLibraryDecodeResult {
        if (!root.has("schemaVersion")) return MusicLibraryDecodeResult.Corrupt("missing schemaVersion")
        val version = root.getInt("schemaVersion")
        if (version > MUSIC_LIBRARY_SCHEMA_VERSION) return MusicLibraryDecodeResult.UnsupportedVersion(version)
        if (version < 1) return MusicLibraryDecodeResult.Corrupt("invalid schemaVersion $version")
        val accountId = root.getString("accountId")
        if (accountId.isBlank()) return MusicLibraryDecodeResult.Corrupt("blank accountId")
        val playlists = root.objectArray("playlists") { playlistFromJson(it) }.distinctBy { it.id }
        val stats = root.objectArray("stats") { statsFromJson(it) }
            .distinctBy { it.trackKey }
            .associateBy { it.trackKey }
        val recent = root.objectArray("recent") { historyFromJson(it) }
            .distinctBy { it.trackKey }
            .take(MUSIC_MAX_RECENT_ENTRIES)
        return MusicLibraryDecodeResult.Decoded(
            MusicLibrary(accountId = accountId, playlists = playlists, stats = stats, recent = recent)
        )
    }

    // ---- Encoding ----------------------------------------------------------------------------------

    private fun playlistToJson(playlist: MusicPlaylist): JSONObject = JSONObject()
        .put("id", playlist.id)
        .put("title", playlist.title)
        .put("description", playlist.description)
        .put("coverPath", playlist.coverPath ?: JSONObject.NULL)
        .put("tracks", JSONArray().apply { playlist.tracks.forEach { put(trackToJson(it)) } })
        .put("createdAt", playlist.createdAt)
        .put("updatedAt", playlist.updatedAt)
        .put("revision", playlist.revision)
        .put("sourcePlaylistId", playlist.sourcePlaylistId ?: JSONObject.NULL)
        .put("sortMode", playlist.sortMode.name)

    private fun trackToJson(track: PlaylistTrack): JSONObject = JSONObject()
        .put("itemId", track.itemId)
        .put("title", track.title)
        .put("performer", track.performer)
        .put("album", track.album)
        .put("durationSeconds", track.durationSeconds)
        .put("origin", track.origin?.let { originToJson(it) } ?: JSONObject.NULL)
        .put("addedAt", track.addedAt)
        .put("audioHash", track.audioHash ?: JSONObject.NULL)

    private fun statsToJson(stats: MusicListenStats): JSONObject = JSONObject()
        .put("track", refToJson(stats.track))
        .put("playCount", stats.playCount)
        .put("lastPlayedAt", stats.lastPlayedAt ?: JSONObject.NULL)
        .put("firstAddedAt", stats.firstAddedAt ?: JSONObject.NULL)

    private fun refToJson(ref: MusicTrackRef): JSONObject = JSONObject()
        .put("trackKey", ref.trackKey)
        .put("title", ref.title)
        .put("performer", ref.performer)
        .put("album", ref.album)
        .put("durationSeconds", ref.durationSeconds)
        .put("origin", ref.origin?.let { originToJson(it) } ?: JSONObject.NULL)
        .put("audioHash", ref.audioHash ?: JSONObject.NULL)

    private fun historyToJson(entry: MusicHistoryEntry): JSONObject = JSONObject()
        .put("trackKey", entry.trackKey)
        .put("playedAt", entry.playedAt)

    private fun originToJson(origin: PlaybackOrigin): JSONObject = JSONObject()
        .put("chatId", origin.chatId)
        .put("messageId", origin.messageId)
        .put("accountId", origin.accountId)

    // ---- Decoding ----------------------------------------------------------------------------------

    /** Reads every object of the array; an entry that does not decode is skipped, the rest are kept. */
    private inline fun <T> JSONArray.objects(read: (JSONObject) -> T): List<T> {
        val result = ArrayList<T>(length())
        for (index in 0 until length()) {
            val entry = optJSONObject(index) ?: continue
            runCatching { read(entry) }.getOrNull()?.let { result += it }
        }
        return result
    }

    /** A named array of objects; a missing or non-array value reads as empty. */
    private inline fun <T> JSONObject.objectArray(name: String, read: (JSONObject) -> T): List<T> =
        optJSONArray(name)?.objects(read) ?: emptyList()

    private fun playlistFromJson(json: JSONObject): MusicPlaylist {
        val id = json.getString("id")
        require(id.isNotBlank()) { "playlist id is blank" }
        val tracks = json.objectArray("tracks") { trackFromJson(it) }.distinctBy { it.itemId }
        return MusicPlaylist(
            id = id,
            title = json.getString("title"),
            description = json.optionalString("description").orEmpty(),
            coverPath = json.optionalString("coverPath"),
            tracks = tracks,
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            revision = json.optLong("revision", 0L),
            sourcePlaylistId = json.optionalString("sourcePlaylistId"),
            sortMode = MusicSortMode.values().firstOrNull { it.name == json.optString("sortMode") }
                ?: MusicSortMode.Manual
        )
    }

    private fun trackFromJson(json: JSONObject): PlaylistTrack {
        val itemId = json.getString("itemId")
        require(itemId.isNotBlank()) { "track id is blank" }
        return PlaylistTrack(
            itemId = itemId,
            title = json.getString("title"),
            performer = json.optionalString("performer").orEmpty(),
            album = json.optionalString("album").orEmpty(),
            durationSeconds = json.optInt("durationSeconds", 0),
            origin = json.optJSONObject("origin")?.let { originFromJson(it) },
            addedAt = json.optLong("addedAt", 0L),
            audioHash = json.optionalString("audioHash")
        )
    }

    private fun statsFromJson(json: JSONObject): MusicListenStats = MusicListenStats(
        track = refFromJson(json.getJSONObject("track")),
        playCount = json.optInt("playCount", 0),
        lastPlayedAt = json.optionalLong("lastPlayedAt"),
        firstAddedAt = json.optionalLong("firstAddedAt")
    )

    private fun refFromJson(json: JSONObject): MusicTrackRef {
        val trackKey = json.getString("trackKey")
        require(trackKey.isNotBlank()) { "track key is blank" }
        return MusicTrackRef(
            trackKey = trackKey,
            title = json.getString("title"),
            performer = json.optionalString("performer").orEmpty(),
            album = json.optionalString("album").orEmpty(),
            durationSeconds = json.optInt("durationSeconds", 0),
            origin = json.optJSONObject("origin")?.let { originFromJson(it) },
            audioHash = json.optionalString("audioHash")
        )
    }

    private fun historyFromJson(json: JSONObject): MusicHistoryEntry {
        val trackKey = json.getString("trackKey")
        require(trackKey.isNotBlank()) { "history key is blank" }
        return MusicHistoryEntry(trackKey = trackKey, playedAt = json.optLong("playedAt", 0L))
    }

    private fun originFromJson(json: JSONObject): PlaybackOrigin = PlaybackOrigin(
        chatId = json.getLong("chatId"),
        messageId = json.getLong("messageId"),
        accountId = json.optString("accountId", LEGACY_PLAYBACK_ACCOUNT_ID).ifBlank { LEGACY_PLAYBACK_ACCOUNT_ID }
    )

    /** A JSON null and a missing key both read as null; org.json would otherwise return the text "null". */
    private fun JSONObject.optionalString(name: String): String? = if (isNull(name)) null else optString(name)

    private fun JSONObject.optionalLong(name: String): Long? = if (isNull(name)) null else optLong(name)
}
