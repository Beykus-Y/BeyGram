package com.beykus.beygram.data.music

import com.beykus.beygram.playback.LEGACY_PLAYBACK_ACCOUNT_ID
import com.beykus.beygram.playback.PlaybackOrigin
import org.json.JSONArray
import org.json.JSONObject

/** Result of reading one library document. Only [Decoded] carries data that may be shown or saved again. */
sealed interface MusicLibraryDecodeResult {
    /**
     * [skippedCount] entries of the document were left out: malformed, repeated, or beyond a limit. A library with skipped
     * entries is backed up before the first save overwrites the document, so the left-out data is never lost silently.
     */
    data class Decoded(val library: MusicLibrary, val skippedCount: Int = 0) : MusicLibraryDecodeResult

    /** Written by a newer schema. The document has to stay exactly as it is. */
    data class UnsupportedVersion(val version: Int) : MusicLibraryDecodeResult

    data class Corrupt(val reason: String) : MusicLibraryDecodeResult
}

/**
 * JSON layer of the per-account library file. It has no Android dependency, so the format is covered by plain JVM tests.
 *
 * Writing always uses [MUSIC_LIBRARY_SCHEMA_VERSION]. Reading leaves out a single malformed, repeated or over-limit entry
 * and counts it in [MusicLibraryDecodeResult.Decoded.skippedCount]. A document whose top level is broken, or whose
 * `playlists` is missing or not an array, is [MusicLibraryDecodeResult.Corrupt]. Optional fields may be missing or null;
 * a required field that is missing makes its entry malformed.
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
        val rawPlaylists = root.optJSONArray("playlists")
            ?: return MusicLibraryDecodeResult.Corrupt("playlists is missing or not an array")
        val tally = Tally()
        val playlists = limitPlaylists(
            rawPlaylists.readEntries(tally) { playlistFromJson(it, tally) }.distinctCounting(tally) { it.id },
            tally
        )
        val statsByKey = root.entriesOf("stats", tally) { statsFromJson(it) }
            .distinctCounting(tally) { it.trackKey }
            .associateBy { it.trackKey }
        val stats = boundedStats(statsByKey, playlists)
        tally.skipped += statsByKey.size - stats.size
        val historyEntries = root.entriesOf("recent", tally) { historyFromJson(it) }
            .distinctCounting(tally) { it.trackKey }
        val recent = historyEntries.take(MUSIC_MAX_RECENT_ENTRIES)
        tally.skipped += historyEntries.size - recent.size
        return MusicLibraryDecodeResult.Decoded(
            library = MusicLibrary(accountId = accountId, playlists = playlists, stats = stats, recent = recent),
            skippedCount = tally.skipped
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
        .put("sourceRevision", playlist.sourceRevision ?: JSONObject.NULL)
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

    /** Counts what decoding left out, so the caller knows whether the stored document held anything it did not keep. */
    private class Tally {
        var skipped = 0
    }

    /** Reads every object of the array. An entry that is not an object, or does not decode, is counted as skipped. */
    private inline fun <T> JSONArray.readEntries(tally: Tally, read: (JSONObject) -> T): List<T> {
        val result = ArrayList<T>(length())
        for (index in 0 until length()) {
            val entry = optJSONObject(index)
            val value = if (entry == null) null else runCatching { read(entry) }.getOrNull()
            if (value == null) {
                tally.skipped++
            } else {
                result += value
            }
        }
        return result
    }

    /**
     * A named array of objects. A missing or null value reads as empty; a value of another type counts as one skipped
     * section, so the stored data is not silently replaced by an empty one.
     */
    private inline fun <T> JSONObject.entriesOf(name: String, tally: Tally, read: (JSONObject) -> T): List<T> {
        if (!has(name) || isNull(name)) return emptyList()
        val array = optJSONArray(name)
        if (array == null) {
            tally.skipped++
            return emptyList()
        }
        return array.readEntries(tally, read)
    }

    /** Keeps the first entry for each key. Every later repeat is counted as skipped. */
    private fun <T> List<T>.distinctCounting(tally: Tally, key: (T) -> Any): List<T> {
        val seen = HashSet<Any>()
        val result = ArrayList<T>(size)
        for (entry in this) {
            if (seen.add(key(entry))) {
                result += entry
            } else {
                tally.skipped++
            }
        }
        return result
    }

    /**
     * Keeps at most [MUSIC_MAX_PLAYLISTS] playlists. One slot stays free for the favorites playlist, which the repository adds
     * when the file has none. The favorites entry itself is always kept.
     */
    private fun limitPlaylists(playlists: List<MusicPlaylist>, tally: Tally): List<MusicPlaylist> {
        val room = MUSIC_MAX_PLAYLISTS - 1
        val kept = ArrayList<MusicPlaylist>(playlists.size)
        var used = 0
        for (playlist in playlists) {
            if (playlist.isFavorites) {
                kept += playlist
            } else if (used < room) {
                kept += playlist
                used++
            } else {
                tally.skipped++
            }
        }
        return kept
    }

    private fun playlistFromJson(json: JSONObject, tally: Tally): MusicPlaylist {
        val id = json.getString("id")
        require(id.isNotBlank()) { "playlist id is blank" }
        val tracks = json.entriesOf("tracks", tally) { trackFromJson(it) }.distinctCounting(tally) { it.itemId }
        val kept = if (tracks.size > MUSIC_MAX_TRACKS_PER_PLAYLIST) {
            tally.skipped += tracks.size - MUSIC_MAX_TRACKS_PER_PLAYLIST
            tracks.take(MUSIC_MAX_TRACKS_PER_PLAYLIST)
        } else {
            tracks
        }
        return MusicPlaylist(
            id = id,
            title = json.getString("title"),
            description = json.optionalString("description").orEmpty(),
            coverPath = json.optionalString("coverPath"),
            tracks = kept,
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            revision = json.optLong("revision", 0L),
            sourcePlaylistId = json.optionalString("sourcePlaylistId"),
            sourceRevision = json.optionalLong("sourceRevision"),
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
