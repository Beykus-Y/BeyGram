package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderAlbum
import com.beykus.beygram.data.music.provider.ProviderAlbumTracks
import com.beykus.beygram.data.music.provider.ProviderArtistRef
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
import java.security.GeneralSecurityException

private const val LYRICS_FORMAT_LRC = "LRC"
private const val LYRICS_FORMAT_TEXT = "TEXT"

/** One batch of radio tracks and the session it belongs to. */
internal data class YandexWaveBatch(
    val sessionId: String,
    val batchId: String?,
    val tracks: List<ProviderTrack>,
    /** The service no longer knows the session; a new one has to be started. */
    val unknownSession: Boolean,
    /** The session ended on the service side; a new one has to be started. */
    val terminated: Boolean,
)

/** Account data needed by the provider. */
internal data class YandexAccount(
    val uid: String,
    val displayName: String,
    val hasPlus: Boolean,
    val plusSummary: String = "",
)

/**
 * Requests and response mapping for the endpoints the app uses. Every call returns a [ProviderResult]; no
 * exception leaves this class. Unknown fields are ignored, and a missing required field gives ServiceChanged.
 */
internal open class YandexApi(
    private val http: YandexHttp,
    /** Lyrics signing key. Blank means lyrics are unavailable. Injectable for tests. */
    private val lyricsSignKey: () -> String = { YandexConfig.lyricsSignKey },
    /** Clock for the lyrics timeStamp, in milliseconds. Injectable for tests. */
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    open fun account(token: String): ProviderResult<YandexAccount> = call(token, "/account/status") { root ->
        parseAccountStatus(root)
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

    open fun setLiked(token: String, uid: String, trackId: String, liked: Boolean): ProviderResult<Unit> {
        val action = if (liked) "add-multiple" else "remove"
        return call(token, "/users/$uid/likes/tracks/$action", form = mapOf("track-ids" to trackId)) { Unit }
    }

    /**
     * Dislikes a track (or undoes it). Endpoint and form are not yet checked on the live API: a failure only means the
     * service did not learn about it, and the radio keeps playing.
     */
    fun setDisliked(token: String, uid: String, trackId: String, disliked: Boolean): ProviderResult<Unit> {
        val action = if (disliked) "add-multiple" else "remove"
        return call(token, "/users/$uid/dislikes/tracks/$action", form = mapOf("track-ids" to trackId)) { Unit }
    }

    fun playlists(token: String, uid: String): ProviderResult<List<ProviderPlaylist>> =
        call(token, "/users/$uid/playlists/list") { root ->
            val array = root.resultArray()
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { parsePlaylist(it, uid) } }
        }

    /** Playlists of other users the account liked. Each entry may wrap the playlist in a `playlist` object. */
    fun likedPlaylists(token: String, uid: String): ProviderResult<List<ProviderPlaylist>> =
        call(token, "/users/$uid/likes/playlists") { root ->
            val array = root.resultArray()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                parsePlaylist(item.optJSONObject("playlist") ?: item, uid)
            }
        }

    /** Liked albums. Each entry may wrap the album in an `album` object. */
    fun likedAlbums(token: String, uid: String): ProviderResult<List<ProviderAlbum>> =
        call(token, "/users/$uid/likes/albums", query = "rich=true") { root ->
            val array = root.resultArray()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                parseAlbum(item.optJSONObject("album") ?: item)
            }
        }

    /** An album with its tracks; the service groups them by disc in `volumes`. */
    fun album(token: String, albumId: String): ProviderResult<ProviderAlbumTracks> =
        call(token, "/albums/$albumId/with-tracks") { root ->
            val result = root.result()
            val album = parseAlbum(result) ?: return@call null
            val volumes = result.optJSONArray("volumes") ?: JSONArray()
            val tracks = (0 until volumes.length()).flatMap { disc -> volumes.optJSONArray(disc)?.tracks().orEmpty() }
            ProviderAlbumTracks(album, tracks.map { if (it.albumId == null) it.copy(albumId = album.id) else it })
        }

    /**
     * Starts a radio session («Моя волна» for [YandexWave.MY_WAVE_SEED]). [queue] lists tracks already heard, as
     * `id:albumId`, so a replacement session does not repeat them.
     */
    open fun waveStart(token: String, seeds: List<String>, queue: List<String>): ProviderResult<YandexWaveBatch> {
        val body = JSONObject()
            .put("seeds", JSONArray(seeds))
            .put("includeTracksInResponse", true)
        if (queue.isNotEmpty()) body.put("queue", JSONArray(queue.takeLast(YandexWave.QUEUE_HINT)))
        return call(token, "/rotor/session/new", json = body) { root -> parseWaveBatch(root, sessionId = null) }
    }

    /** The next batch of a radio session. [queue] lists the tracks the session already gave, as `id:albumId`. */
    open fun waveNext(token: String, sessionId: String, queue: List<String>): ProviderResult<YandexWaveBatch> {
        val body = JSONObject().put("queue", JSONArray(queue.takeLast(YandexWave.QUEUE_HINT)))
        return call(token, "/rotor/session/${encode(sessionId)}/tracks", json = body) { root ->
            parseWaveBatch(root, sessionId)
        }
    }

    /**
     * One listening event of a radio session, so the wave learns from it. [trackId] is `id:albumId`; [playedSeconds]
     * is required for finished and skipped tracks. Answers are ignored beyond success or failure.
     */
    open fun waveFeedback(token: String, sessionId: String, event: YandexWaveEvent, batchId: String?): ProviderResult<Unit> {
        val body = JSONObject().put("event", event.toJson())
        if (batchId != null) body.put("batchId", batchId)
        if (event.type == YandexWaveEvent.RADIO_STARTED) body.put("from", YandexWave.FROM)
        return call(token, "/rotor/session/${encode(sessionId)}/feedback", json = body) { Unit }
    }

    /** Reads a session or next-batch answer. Null when the shape is not the expected one. */
    fun parseWaveBatch(root: JSONObject, sessionId: String?): YandexWaveBatch? {
        val result = root.result()
        val id = result.optString("radioSessionId").takeIf { it.isNotBlank() } ?: sessionId ?: return null
        val sequence = result.optJSONArray("sequence") ?: JSONArray()
        val tracks = (0 until sequence.length()).mapNotNull { index ->
            val entry = sequence.optJSONObject(index) ?: return@mapNotNull null
            val track = entry.optJSONObject("track") ?: return@mapNotNull null
            parseTrack(track)?.let { parsed ->
                if (entry.has("liked") && !entry.isNull("liked")) parsed.copy(liked = entry.optBoolean("liked")) else parsed
            }
        }
        return YandexWaveBatch(
            sessionId = id,
            batchId = result.optString("batchId").takeIf { it.isNotBlank() },
            tracks = tracks,
            unknownSession = result.optBoolean("unknownSession", false),
            terminated = result.optBoolean("terminated", false),
        )
    }

    fun parsePlaylist(item: JSONObject, uid: String): ProviderPlaylist? {
        val kind = item.idOf("kind") ?: return null
        val title = item.optString("title").takeIf { it.isNotBlank() } ?: return null
        val owner = item.optJSONObject("owner")
        val ownerUid = owner?.idOf("uid") ?: item.idOf("uid")
        return ProviderPlaylist(
            kind = kind,
            title = title,
            trackCount = item.optInt("trackCount", 0),
            coverUrl = coverUrl(item.optJSONObject("cover")?.optString("uri")?.takeIf { it.isNotBlank() } ?: item.optString("ogImage")),
            isOwn = ownerUid == uid,
            ownerUid = ownerUid,
            ownerName = owner?.optString("name")?.takeIf { it.isNotBlank() } ?: owner?.optString("login")?.takeIf { it.isNotBlank() },
            uuid = item.optString("playlistUuid").takeIf { it.isNotBlank() },
        )
    }

    fun parseAlbum(o: JSONObject): ProviderAlbum? {
        val id = o.idOf("id") ?: return null
        val title = o.optString("title").takeIf { it.isNotBlank() } ?: return null
        val artists = o.optJSONArray("artists")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
        }.orEmpty()
        return ProviderAlbum(
            id = id,
            title = title,
            artists = artists,
            year = o.optInt("year", 0).takeIf { it > 0 },
            trackCount = o.optInt("trackCount", 0),
            coverUrl = coverUrl(o.optString("coverUri")),
        )
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
     * Lyrics as a link to a file. The request is signed (see [YandexSigning.lyricsSign]) with the SIGN_KEY secret; without
     * the key nothing is sent. LRC is asked first. When the service has no synced lyrics (404, a 2xx without a link, or a
     * file with no time-tagged line) one TEXT request follows. Track ids of the form "id:albumId" use the id only.
     * The file is fetched without the token. Returns the result and a redacted report: format, status codes, key names,
     * value types and line counts. No token, sign, link, lyrics text or track id goes into the report.
     */
    open fun lyricsWithReport(token: String, trackId: String): Pair<ProviderResult<ProviderLyrics>, String> {
        val key = lyricsSignKey()
        if (key.isBlank()) return ProviderResult.Failure(MusicProviderFailure.Unavailable) to "lyrics.sign=missing"
        val id = trackId.substringBefore(':')
        val synced = fetchLyrics(token, id, key, LYRICS_FORMAT_LRC)
        if (!synced.fallback) return synced.result to synced.report
        val text = fetchLyrics(token, id, key, LYRICS_FORMAT_TEXT)
        // Plain text found: use it. When the TEXT request only has no lyrics either (NotFound), the LRC outcome stands.
        // Any other TEXT failure (auth, network, server) is the real answer and must not be hidden behind that NotFound.
        val textFailure = (text.result as? ProviderResult.Failure)?.failure
        val result = if (textFailure == MusicProviderFailure.NotFound) synced.result else text.result
        return result to "${synced.report}; fallback=TEXT; ${text.report}"
    }

    fun lyrics(token: String, trackId: String): ProviderResult<ProviderLyrics> = lyricsWithReport(token, trackId).first

    /** One lyrics request in one format. [fallback] is set on an LRC attempt that found no synced text. */
    private class LyricsAttempt(val result: ProviderResult<ProviderLyrics>, val report: String, val fallback: Boolean)

    private fun fetchLyrics(token: String, trackId: String, key: String, format: String): LyricsAttempt {
        val prefix = "lyrics.format=$format"
        val timeStamp = (clock() / 1_000L).toString()
        val sign = try {
            YandexSigning.lyricsSign(trackId, timeStamp, key)
        } catch (e: GeneralSecurityException) {
            return LyricsAttempt(ProviderResult.Failure(MusicProviderFailure.Unknown), "$prefix; lyrics.sign=error", false)
        }
        val url = YandexConfig.API_BASE + "/tracks/$trackId/lyrics" +
            "?format=$format&timeStamp=$timeStamp&sign=${encode(sign)}"
        val response = try {
            http.get(url, token)
        } catch (e: IOException) {
            return LyricsAttempt(ProviderResult.Failure(MusicProviderFailure.Network), "$prefix; lyrics.api=network", false)
        }
        val root = try { JSONObject(response.body) } catch (e: JSONException) { null }
        val head = "$prefix; lyrics.api=${response.code}; " +
            (root?.let { shapeSummary(it) } ?: "body is not JSON (${response.body.length} chars)")
        if (response.code == 404) {
            return LyricsAttempt(ProviderResult.Failure(MusicProviderFailure.NotFound), head, fallback = true)
        }
        if (response.code !in 200..299) {
            return LyricsAttempt(ProviderResult.Failure(YandexAuth.failureForCode(response.code)), head, fallback = false)
        }
        val info = root?.optJSONObject("result")
            ?: return LyricsAttempt(ProviderResult.Failure(MusicProviderFailure.ServiceChanged), head, fallback = format == LYRICS_FORMAT_LRC)
        val link = info.optString("downloadUrl").takeIf { it.isNotBlank() }
            ?: return LyricsAttempt(ProviderResult.Failure(MusicProviderFailure.ServiceChanged), head, fallback = format == LYRICS_FORMAT_LRC)
        val holder = info.optJSONObject("major")?.optString("name")?.takeIf { it.isNotBlank() }
        val file = try {
            http.get(link)
        } catch (e: IOException) {
            return LyricsAttempt(ProviderResult.Failure(MusicProviderFailure.Network), "$head; lyrics.file=network", fallback = false)
        }
        val fileHead = "$head; lyrics.file=${file.code}"
        if (file.code !in 200..299) {
            return LyricsAttempt(ProviderResult.Failure(YandexAuth.failureForCode(file.code)), fileHead, fallback = false)
        }
        val nonEmpty = file.body.lineSequence().count { it.isNotBlank() }
        val tagged = file.body.lineSequence().count {
            val line = it.trim()
            line.startsWith("[") && line.getOrNull(1)?.isDigit() == true
        }
        val report = "$fileHead, lines=$nonEmpty, timeTagged=$tagged"
        if (format == LYRICS_FORMAT_LRC) {
            val lines = YandexSigning.parseLrc(file.body)
            return if (lines.isNotEmpty()) {
                LyricsAttempt(ProviderResult.Success(ProviderLyrics(lines, null, holder)), report, fallback = false)
            } else {
                LyricsAttempt(ProviderResult.Failure(MusicProviderFailure.NotFound), report, fallback = true)
            }
        }
        val plain = YandexSigning.plainLyricsText(file.body)
            ?: return LyricsAttempt(ProviderResult.Failure(MusicProviderFailure.NotFound), report, fallback = false)
        return LyricsAttempt(ProviderResult.Success(ProviderLyrics(emptyList(), plain, holder)), report, fallback = false)
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
                // Preview clips (about 30 seconds) are never full tracks, whatever the bitrate.
                if (item.optBoolean("preview")) return@mapNotNull null
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
        val artistObjects = o.optJSONArray("artists")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }.orEmpty()
        val artists = artistObjects.mapNotNull { it.optString("name").takeIf(String::isNotBlank) }
        val artistRefs = artistObjects.mapNotNull { obj ->
            val name = obj.optString("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
            obj.idOf("id")?.let { ProviderArtistRef(it, name) }
        }
        val firstAlbum = o.optJSONArray("albums")?.optJSONObject(0)
        val album = firstAlbum?.optString("title")?.takeIf { it.isNotBlank() }
        return ProviderTrack(
            trackId = id,
            title = title,
            artists = artists,
            albumTitle = album,
            durationMs = o.optLong("durationMs", 0L),
            coverUrl = coverUrl(o.optString("coverUri")),
            isAvailable = o.optBoolean("available", true),
            liked = null,
            artistRefs = artistRefs,
            albumId = firstAlbum?.idOf("id"),
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
        json: JSONObject? = null,
        map: (JSONObject) -> T?,
    ): ProviderResult<T> {
        val url = YandexConfig.API_BASE + path + (query?.let { "?$it" } ?: "")
        return try {
            val response = when {
                json != null -> http.postJson(url, json.toString(), token)
                form != null -> http.post(url, form, token)
                else -> http.get(url, token)
            }
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
        // A storage host, not the API: the gap between API requests does not apply.
        val response = http.get(url, paced = false)
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

/**
 * Reads the account from an /account/status body. The subscription flag is looked for under the keys the
 * official client has been seen to use; any of them set to true counts as a subscription.
 */
internal fun parseAccountStatus(root: JSONObject): YandexAccount? {
    val result = root.optJSONObject("result") ?: root
    val account = result.optJSONObject("account") ?: return null
    val uid = if (account.has("uid") && !account.isNull("uid")) {
        account.opt("uid").toString().takeIf { it.isNotBlank() }
    } else {
        null
    } ?: return null
    val name = account.optString("displayName").ifBlank { account.optString("login") }
    val plus = result.optJSONObject("plus")
    val subscription = result.optJSONObject("subscription")
    val hasPlus = listOf(
        plus?.optBoolean("has_plus") == true,
        plus?.optBoolean("hasPlus") == true,
        subscription?.optBoolean("has_plus") == true,
        subscription?.optBoolean("hasPlus") == true,
        result.optBoolean("has_plus") == true,
        account.optBoolean("has_plus") == true,
    ).any { it }
    return YandexAccount(uid = uid, displayName = name, hasPlus = hasPlus, plusSummary = plusSummary(result))
}

/** Keys whose short, plain string values help diagnose a refusal (error codes, status words). */
private val SHOWN_STRING_KEYS = setOf("error", "errorCode", "code", "status", "type", "message")

/**
 * Key names of a response and the types of its values, one level into "result". Only the short string values of
 * [SHOWN_STRING_KEYS] are shown, and only plain words; anything that could carry a token is dropped.
 */
internal fun shapeSummary(root: JSONObject): String {
    val parts = mutableListOf<String>()
    fun describe(prefix: String, obj: JSONObject) {
        for (key in obj.keys().asSequence().sorted()) {
            val value = obj.opt(key)
            val shown = when (value) {
                is Boolean, is Number -> value.toString()
                is JSONObject -> "object"
                is JSONArray -> "array(${value.length()})"
                null, JSONObject.NULL -> "null"
                is String -> stringShape(key, value)
                else -> "other"
            }
            parts += "$prefix$key=$shown"
        }
    }
    describe("", root)
    root.optJSONObject("result")?.let { describe("result.", it) }
    return parts.joinToString(", ")
}

private fun stringShape(key: String, value: String): String {
    val plain = value.length <= 60 && Regex("[\\p{L}\\p{N} _.:-]+").matches(value)
    val safe = plain && listOf("oauth", "bearer", "token", "sign").none { value.contains(it, ignoreCase = true) }
    return if (key in SHOWN_STRING_KEYS && safe) "\"$value\"" else "string"
}

/**
 * Key names and scalar values of the subscription-related objects, with no names, ids, tokens or other
 * strings. Logged in DEV to compare the service's answer with the flag the app derived.
 */
internal fun plusSummary(result: JSONObject): String {
    val parts = mutableListOf<String>()
    for (section in listOf("plus", "subscription")) {
        val obj = result.optJSONObject(section)
        if (obj == null) {
            parts += "$section=absent"
            continue
        }
        for (key in obj.keys().asSequence().sorted()) {
            val value = obj.opt(key)
            val shown = when (value) {
                is Boolean, is Number -> value.toString()
                is JSONObject -> "object"
                is JSONArray -> "array(${value.length()})"
                null, JSONObject.NULL -> "null"
                else -> "string"
            }
            parts += "$section.$key=$shown"
        }
    }
    return parts.joinToString(", ")
}
