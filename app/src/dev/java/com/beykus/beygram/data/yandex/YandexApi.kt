package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderAudio
import com.beykus.beygram.data.music.provider.ProviderLyrics
import com.beykus.beygram.data.music.provider.ProviderPlaylist
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Account data needed by the provider. */
internal data class YandexAccount(val uid: String, val displayName: String, val hasPlus: Boolean)

/**
 * Requests and response mapping for the endpoints the app uses. Every call returns a [ProviderResult]; no
 * exception leaves this class. Unknown fields are ignored, and a missing required field gives ServiceChanged.
 */
internal class YandexApi(private val http: YandexHttp) {

    fun account(token: String): ProviderResult<YandexAccount> = call(token, "/account/status") { root ->
        val account = root.result().optJSONObject("account") ?: return@call null
        val uid = account.idOf("uid") ?: return@call null
        val name = account.optString("displayName").ifBlank { account.optString("login") }
        val hasPlus = root.result().optJSONObject("plus")?.optBoolean("has_plus") ?: false
        YandexAccount(uid, name, hasPlus)
    }

    fun search(token: String, text: String, limit: Int): ProviderResult<List<ProviderTrack>> {
        val query = "text=${encode(text)}&type=track&page=0&nocorrect=false"
        return call(token, "/search", query) { root ->
            root.result().optJSONObject("tracks")?.optJSONArray("results")?.tracks()?.take(limit).orEmpty()
        }
    }

    /** Liked track ids, newest first as the service returns them. */
    fun likedTrackIds(token: String, uid: String): ProviderResult<List<String>> =
        call(token, "/users/$uid/likes/tracks") { root ->
            val tracks = root.result().optJSONObject("library")?.optJSONArray("tracks") ?: JSONArray()
            (0 until tracks.length()).mapNotNull { tracks.optJSONObject(it)?.idOf("id") }
        }

    fun tracksByIds(token: String, ids: List<String>): ProviderResult<List<ProviderTrack>> {
        val result = mutableListOf<ProviderTrack>()
        for (chunk in ids.chunked(YandexConfig.TRACK_CHUNK)) {
            val part = call(token, "/tracks", form = mapOf("track-ids" to chunk.joinToString(","))) { root ->
                root.resultArray().tracks()
            }
            when (part) {
                is ProviderResult.Success -> result += part.value
                is ProviderResult.Failure -> return part
            }
        }
        return ProviderResult.Success(result)
    }

    fun setLiked(token: String, uid: String, trackId: String, liked: Boolean): ProviderResult<Unit> {
        val action = if (liked) "add-multiple" else "remove"
        return call(token, "/users/$uid/likes/tracks/$action", form = mapOf("track-ids" to trackId)) { Unit }
    }

    fun playlists(token: String, uid: String): ProviderResult<List<ProviderPlaylist>> =
        call(token, "/users/$uid/playlists/list") { root ->
            val array = root.resultArray()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val kind = item.idOf("kind") ?: return@mapNotNull null
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ProviderPlaylist(
                    kind = kind,
                    title = title,
                    trackCount = item.optInt("trackCount", 0),
                    coverUrl = coverUrl(item.optJSONObject("cover")?.optString("uri")),
                    isOwn = item.optJSONObject("owner")?.idOf("uid") == uid,
                )
            }
        }

    fun playlistTracks(token: String, uid: String, kind: String): ProviderResult<List<ProviderTrack>> =
        call(token, "/users/$uid/playlists/$kind") { root ->
            val items = root.result().optJSONArray("tracks") ?: JSONArray()
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                parseTrack(item.optJSONObject("track") ?: item)
            }
        }

    /**
     * Lyrics come as a link to a file. The file is LRC when it has time tags and plain text otherwise.
     * The file is fetched without the token.
     */
    fun lyrics(token: String, trackId: String): ProviderResult<ProviderLyrics> {
        val info = call(token, "/tracks/$trackId/lyrics?format=LRC") { root ->
            val result = root.result()
            val link = result.optString("downloadUrl").takeIf { it.isNotBlank() } ?: return@call null
            LyricsInfo(link, result.optJSONObject("major")?.optString("name")?.takeIf { it.isNotBlank() })
        }
        val lyricsInfo = when (info) {
            is ProviderResult.Success -> info.value
            is ProviderResult.Failure -> return info
        }
        return fetchFile(lyricsInfo.link) { body ->
            val lines = YandexSigning.parseLrc(body)
            val plain = body.lineSequence().map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("[") }
                .joinToString("\n")
                .takeIf { it.isNotEmpty() }
            ProviderLyrics(
                lines = lines,
                plainText = if (lines.isEmpty()) plain else null,
                copyrightHolder = lyricsInfo.holder,
            )
        }
    }

    /**
     * Full-track MP3 link from the download-info XML. Requires a subscription, which the provider checks first.
     * The link is short-lived: the provider requests it right before playback and never stores it.
     */
    fun audio(token: String, trackId: String): ProviderResult<ProviderAudio> {
        val options = call(token, "/tracks/$trackId/download-info") { root ->
            val array = root.resultArray()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                if (item.optString("codec") != "mp3") return@mapNotNull null
                DownloadOption(
                    bitrateKbps = item.optInt("bitrateInKbps", 0),
                    url = item.optString("downloadInfoUrl"),
                    direct = item.optBoolean("direct"),
                )
            }.maxByOrNull { it.bitrateKbps }
        }
        val best = when (options) {
            is ProviderResult.Success -> options.value ?: return ProviderResult.Failure(MusicProviderFailure.ServiceChanged)
            is ProviderResult.Failure -> return options
        }
        if (best.url.isBlank()) return ProviderResult.Failure(MusicProviderFailure.ServiceChanged)
        if (best.direct) return ProviderResult.Success(ProviderAudio(best.url, "mp3", best.bitrateKbps))
        return fetchFile(best.url) { body ->
            val info = YandexSigning.parseDownloadInfo(body) ?: return@fetchFile null
            ProviderAudio(YandexSigning.directLink(info), "mp3", best.bitrateKbps)
        }
    }

    fun parseTrack(o: JSONObject): ProviderTrack? {
        val id = o.idOf("id") ?: return null
        val title = o.optString("title").takeIf { it.isNotBlank() } ?: return null
        val artists = o.optJSONArray("artists")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
        }.orEmpty()
        val album = o.optJSONArray("albums")?.optJSONObject(0)?.optString("title")?.takeIf { it.isNotBlank() }
        return ProviderTrack(
            trackId = id,
            title = title,
            artists = artists,
            albumTitle = album,
            durationMs = o.optLong("durationMs", 0L),
            coverUrl = coverUrl(o.optString("coverUri")),
            isAvailable = o.optBoolean("available", true),
            liked = null,
        )
    }

    // Helpers -----------------------------------------------------------------------------------------------------

    private data class LyricsInfo(val link: String, val holder: String?)

    private data class DownloadOption(val bitrateKbps: Int, val url: String, val direct: Boolean)

    /**
     * One authenticated JSON call. [map] receives the whole response object and returns null when the shape is
     * not what the app expects. A non-2xx status maps to a failure; a network or parse error never throws.
     */
    private fun <T> call(
        token: String,
        path: String,
        query: String? = null,
        form: Map<String, String>? = null,
        map: (JSONObject) -> T?,
    ): ProviderResult<T> {
        val url = YandexConfig.API_BASE + path + (query?.let { "?$it" } ?: "")
        return try {
            val response = if (form != null) http.post(url, form, token) else http.get(url, token)
            if (response.code !in 200..299) return ProviderResult.Failure(YandexAuth.failureForCode(response.code))
            val value = map(JSONObject(response.body))
                ?: return ProviderResult.Failure(MusicProviderFailure.ServiceChanged)
            ProviderResult.Success(value)
        } catch (e: IOException) {
            ProviderResult.Failure(MusicProviderFailure.Network)
        } catch (e: JSONException) {
            ProviderResult.Failure(MusicProviderFailure.ServiceChanged)
        }
    }

    /** Downloads a helper file (lyrics, download XML) without the token and maps its text. */
    private fun <T> fetchFile(url: String, map: (String) -> T?): ProviderResult<T> = try {
        val response = http.get(url)
        if (response.code !in 200..299) {
            ProviderResult.Failure(YandexAuth.failureForCode(response.code))
        } else {
            map(response.body)?.let { ProviderResult.Success(it) }
                ?: ProviderResult.Failure(MusicProviderFailure.ServiceChanged)
        }
    } catch (e: IOException) {
        ProviderResult.Failure(MusicProviderFailure.Network)
    }

    private fun JSONObject.result(): JSONObject = optJSONObject("result") ?: this

    private fun JSONObject.resultArray(): JSONArray = optJSONArray("result") ?: JSONArray()

    private fun JSONArray.tracks(): List<ProviderTrack> =
        (0 until length()).mapNotNull { optJSONObject(it)?.let { obj -> parseTrack(obj) } }

    private fun JSONObject.idOf(name: String): String? =
        if (has(name) && !isNull(name)) opt(name).toString().takeIf { it.isNotBlank() } else null

    private fun coverUrl(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() }?.let { "https://" + it.replace("%%", "200x200") }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
