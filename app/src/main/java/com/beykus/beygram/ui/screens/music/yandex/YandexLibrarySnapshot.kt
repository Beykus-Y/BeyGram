package com.beykus.beygram.ui.screens.music.yandex

import android.content.Context
import com.beykus.beygram.data.music.provider.ProviderAlbum
import com.beykus.beygram.data.music.provider.ProviderPlaylist
import com.beykus.beygram.data.music.provider.ProviderTrack
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Yandex tab as it was last loaded: liked track ids, the first liked tracks, playlists and albums of one account.
 * It is shown at once on the next open, and the network is asked again only by a pull or once it is old.
 */
internal data class YandexLibrarySnapshot(
    val uid: String,
    val savedAt: Long,
    val likedIds: List<String>,
    val likedPreview: List<ProviderTrack>,
    val playlists: List<ProviderPlaylist>,
    val albums: List<ProviderAlbum>,
)

/** Reads and writes the snapshot. It holds no token; it is deleted on sign-out and replaced by another account's. */
internal object YandexLibrarySnapshots {
    private const val FILE_NAME = "yandex-library-snapshot.json"
    private const val VERSION = 1

    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, FILE_NAME)

    /** The stored snapshot of [uid], or null when there is none or it belongs to another account. */
    fun read(context: Context, uid: String): YandexLibrarySnapshot? = runCatching {
        val target = file(context)
        if (!target.isFile) return null
        val root = JSONObject(target.readText())
        if (root.optInt("version") != VERSION || root.optString("uid") != uid) return null
        YandexLibrarySnapshot(
            uid = uid,
            savedAt = root.optLong("savedAt"),
            likedIds = root.optJSONArray("likedIds").strings(),
            likedPreview = root.optJSONArray("likedPreview").objects().mapNotNull(::trackOf),
            playlists = root.optJSONArray("playlists").objects().mapNotNull(::playlistOf),
            albums = root.optJSONArray("albums").objects().mapNotNull(::albumOf),
        )
    }.getOrNull()

    fun write(context: Context, snapshot: YandexLibrarySnapshot) {
        runCatching {
            val root = JSONObject()
                .put("version", VERSION)
                .put("uid", snapshot.uid)
                .put("savedAt", snapshot.savedAt)
                .put("likedIds", JSONArray(snapshot.likedIds))
                .put("likedPreview", JSONArray(snapshot.likedPreview.map(::jsonOf)))
                .put("playlists", JSONArray(snapshot.playlists.map(::jsonOf)))
                .put("albums", JSONArray(snapshot.albums.map(::jsonOf)))
            val target = file(context)
            val partial = File(target.parentFile, "$FILE_NAME.part")
            partial.writeText(root.toString())
            if (!partial.renameTo(target)) partial.delete()
        }
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun jsonOf(track: ProviderTrack) = JSONObject()
        .put("id", track.trackId)
        .put("title", track.title)
        .put("artists", JSONArray(track.artists))
        .put("album", track.albumTitle ?: JSONObject.NULL)
        .put("albumId", track.albumId ?: JSONObject.NULL)
        .put("durationMs", track.durationMs)
        .put("cover", track.coverUrl ?: JSONObject.NULL)
        .put("available", track.isAvailable)

    private fun trackOf(o: JSONObject): ProviderTrack? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        return ProviderTrack(
            trackId = id,
            title = o.optString("title"),
            artists = o.optJSONArray("artists").strings(),
            albumTitle = o.nullableString("album"),
            durationMs = o.optLong("durationMs"),
            coverUrl = o.nullableString("cover"),
            isAvailable = o.optBoolean("available", true),
            liked = true,
            albumId = o.nullableString("albumId"),
        )
    }

    private fun jsonOf(playlist: ProviderPlaylist) = JSONObject()
        .put("kind", playlist.kind)
        .put("title", playlist.title)
        .put("count", playlist.trackCount)
        .put("cover", playlist.coverUrl ?: JSONObject.NULL)
        .put("own", playlist.isOwn)
        .put("ownerUid", playlist.ownerUid ?: JSONObject.NULL)
        .put("ownerName", playlist.ownerName ?: JSONObject.NULL)
        .put("uuid", playlist.uuid ?: JSONObject.NULL)

    private fun playlistOf(o: JSONObject): ProviderPlaylist? {
        val kind = o.optString("kind").takeIf { it.isNotBlank() } ?: return null
        return ProviderPlaylist(
            kind = kind,
            title = o.optString("title"),
            trackCount = o.optInt("count"),
            coverUrl = o.nullableString("cover"),
            isOwn = o.optBoolean("own"),
            ownerUid = o.nullableString("ownerUid"),
            ownerName = o.nullableString("ownerName"),
            uuid = o.nullableString("uuid"),
        )
    }

    private fun jsonOf(album: ProviderAlbum) = JSONObject()
        .put("id", album.id)
        .put("title", album.title)
        .put("artists", JSONArray(album.artists))
        .put("year", album.year ?: JSONObject.NULL)
        .put("count", album.trackCount)
        .put("cover", album.coverUrl ?: JSONObject.NULL)

    private fun albumOf(o: JSONObject): ProviderAlbum? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        return ProviderAlbum(
            id = id,
            title = o.optString("title"),
            artists = o.optJSONArray("artists").strings(),
            year = if (o.isNull("year")) null else o.optInt("year").takeIf { it > 0 },
            trackCount = o.optInt("count"),
            coverUrl = o.nullableString("cover"),
        )
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
