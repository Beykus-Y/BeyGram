package com.beykus.beygram.data.lyrics

import com.beykus.beygram.domain.lyrics.EnhancedLrcParser
import com.beykus.beygram.domain.lyrics.LyricsAttribution
import com.beykus.beygram.domain.lyrics.LyricsDocument
import com.beykus.beygram.domain.lyrics.LyricsFetchResult
import com.beykus.beygram.domain.lyrics.LyricsMatcher
import com.beykus.beygram.domain.lyrics.LyricsSource
import com.beykus.beygram.domain.lyrics.LyricsSourceId
import com.beykus.beygram.domain.lyrics.LyricsSyncMode
import com.beykus.beygram.domain.lyrics.RichLyricLine
import com.beykus.beygram.domain.music.ExternalMetadataPolicy
import com.beykus.beygram.domain.music.ExternalQueryMetadata
import com.beykus.beygram.domain.music.TrackIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LrcLibHttpResponse(val code: Int, val body: String)

fun interface LrcLibHttpClient {
    fun get(url: String, userAgent: String): LrcLibHttpResponse
}

/**
 * Public LRCLIB lyrics source (`https://lrclib.net`).
 * External network lookup: only invoked when [TrackIdentity.privacy] allows it and the user opted in.
 */
class LrcLibLyricsSource(
    private val http: LrcLibHttpClient = DEFAULT_HTTP_CLIENT,
    private val userAgent: String = DEFAULT_USER_AGENT,
) : LyricsSource {

    override val id: LyricsSourceId = LyricsSourceId.LrcLib
    override val isExternalNetwork: Boolean = true

    override fun supports(identity: TrackIdentity): Boolean =
        ExternalMetadataPolicy.sanitizedExternalQuery(identity) != null

    override suspend fun fetch(identity: TrackIdentity): LyricsFetchResult = withContext(Dispatchers.IO) {
        val query = ExternalMetadataPolicy.sanitizedExternalQuery(identity)
            ?: return@withContext LyricsFetchResult.NotFound

        val getUrl = buildGetUrl(query)
        val getResponse = try {
            http.get(getUrl, userAgent)
        } catch (_: IOException) {
            return@withContext LyricsFetchResult.Unavailable(retryable = true)
        }

        if (getResponse.code in 200..299) {
            val parsed = runCatching { JSONObject(getResponse.body) }.getOrNull()
                ?: return@withContext LyricsFetchResult.Unavailable(retryable = false)
            parseRecord(parsed, query)?.let { (doc, score) ->
                return@withContext LyricsFetchResult.Found(doc, score)
            }
        } else if (getResponse.code == 429 || getResponse.code >= 500) {
            return@withContext LyricsFetchResult.Unavailable(retryable = true)
        } else if (getResponse.code != 404) {
            return@withContext LyricsFetchResult.Unavailable(retryable = false)
        }

        val searchUrl = buildSearchUrl(query)
        val searchResponse = try {
            http.get(searchUrl, userAgent)
        } catch (_: IOException) {
            return@withContext LyricsFetchResult.Unavailable(retryable = true)
        }
        if (searchResponse.code == 429 || searchResponse.code >= 500) {
            return@withContext LyricsFetchResult.Unavailable(retryable = true)
        }
        if (searchResponse.code !in 200..299) {
            return@withContext LyricsFetchResult.NotFound
        }
        val array = runCatching { JSONArray(searchResponse.body) }.getOrNull()
            ?: return@withContext LyricsFetchResult.NotFound
        var best: Pair<LyricsDocument, Float>? = null
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val candidate = parseRecord(obj, query) ?: continue
            if (best == null || candidate.second > best.second) {
                best = candidate
            }
        }
        best?.let { LyricsFetchResult.Found(it.first, it.second) } ?: LyricsFetchResult.NotFound
    }

    private fun parseRecord(obj: JSONObject, query: ExternalQueryMetadata): Pair<LyricsDocument, Float>? {
        val title = obj.optString("trackName").ifBlank { obj.optString("name") }
        val artist = obj.optString("artistName")
        val durationMs = (obj.optDouble("duration", 0.0) * 1_000.0).toLong().coerceAtLeast(0L)
        val instrumental = obj.optBoolean("instrumental", false)
        val syncedRaw = obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plainRaw = obj.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }

        val attribution = LyricsAttribution(
            sourceId = LyricsSourceId.LrcLib,
            sourceDisplayName = "LRCLIB",
            copyrightHolder = null,
            isExternalLookup = true,
        )

        if (syncedRaw != null) {
            val (mode, lines) = EnhancedLrcParser.parse(syncedRaw)
            if (mode != LyricsSyncMode.Plain && lines.isNotEmpty()) {
                val score = LyricsMatcher.scoreCandidate(query, title, artist, durationMs, mode) ?: return null
                return LyricsDocument(
                    syncMode = mode,
                    lines = lines,
                    plainText = plainRaw,
                    attribution = attribution,
                    isInstrumentalTrack = instrumental,
                ) to score
            }
        }
        if (plainRaw != null) {
            val score = LyricsMatcher.scoreCandidate(query, title, artist, durationMs, LyricsSyncMode.Plain)
                ?: return null
            val plainLines = plainRaw.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { RichLyricLine(startMs = null, text = it) }
                .toList()
            return LyricsDocument(
                syncMode = LyricsSyncMode.Plain,
                lines = plainLines,
                plainText = plainRaw,
                attribution = attribution,
                isInstrumentalTrack = instrumental,
            ) to score
        }
        if (instrumental) {
            val score = LyricsMatcher.scoreCandidate(query, title, artist, durationMs, LyricsSyncMode.Plain)
                ?: return null
            return LyricsDocument(
                syncMode = LyricsSyncMode.Plain,
                lines = emptyList(),
                plainText = null,
                attribution = attribution,
                isInstrumentalTrack = true,
            ) to score
        }
        return null
    }

    private fun buildGetUrl(query: ExternalQueryMetadata): String = buildString {
        append(BASE_URL).append("/api/get?track_name=").append(encode(query.title))
        append("&artist_name=").append(encode(query.artist))
        query.album?.let { append("&album_name=").append(encode(it)) }
        query.durationSeconds?.let { append("&duration=").append(it) }
    }

    private fun buildSearchUrl(query: ExternalQueryMetadata): String = buildString {
        append(BASE_URL).append("/api/search?track_name=").append(encode(query.title))
        if (query.artist.isNotBlank()) append("&artist_name=").append(encode(query.artist))
        query.album?.let { append("&album_name=").append(encode(it)) }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val BASE_URL = "https://lrclib.net"
        private const val DEFAULT_USER_AGENT = "BeyGram/Android (https://github.com/Beykus-Y/BeyGram)"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 10_000

        val DEFAULT_HTTP_CLIENT = LrcLibHttpClient { url, userAgent ->
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                LrcLibHttpResponse(code, body)
            } finally {
                connection.disconnect()
            }
        }
    }
}
