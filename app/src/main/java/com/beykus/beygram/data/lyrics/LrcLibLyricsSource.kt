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
import com.beykus.beygram.domain.music.MetadataLookupDecision
import com.beykus.beygram.domain.music.TrackIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

data class LrcLibHttpResponse(val code: Int, val body: String, val retryAfterSeconds: Long? = null)

fun interface LrcLibHttpClient {
    suspend fun get(url: String, userAgent: String): LrcLibHttpResponse
}

/**
 * Public LRCLIB lyrics source (`https://lrclib.net`).
 * Enforces both opt-in ([isExternalLookupEnabled]) and [ExternalMetadataPolicy] directly inside
 * [supports] and [fetch] before any network request is made.
 */
class LrcLibLyricsSource(
    private val isExternalLookupEnabled: () -> Boolean,
    private val http: LrcLibHttpClient = DEFAULT_HTTP_CLIENT,
    private val userAgent: String = DEFAULT_USER_AGENT,
) : LyricsSource {

    override val id: LyricsSourceId = LyricsSourceId.LrcLib
    override val isExternalNetwork: Boolean = true

    override fun supports(identity: TrackIdentity): Boolean =
        ExternalMetadataPolicy.checkExternalLookup(
            identity = identity,
            isMusic = true,
            externalLookupOptIn = isExternalLookupEnabled(),
        ) == MetadataLookupDecision.Allowed

    override suspend fun fetch(identity: TrackIdentity): LyricsFetchResult = withContext(Dispatchers.IO) {
        if (!supports(identity)) return@withContext LyricsFetchResult.NotFound
        val query = ExternalMetadataPolicy.sanitizedExternalQuery(identity)
            ?: return@withContext LyricsFetchResult.NotFound

        coroutineContext.ensureActive()
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
            return@withContext LyricsFetchResult.Unavailable(
                retryable = true,
                retryAfterMs = retryAfterMsFor(getResponse),
            )
        } else if (getResponse.code != 404) {
            return@withContext LyricsFetchResult.Unavailable(retryable = false)
        }

        coroutineContext.ensureActive()
        val searchUrl = buildSearchUrl(query)
        val searchResponse = try {
            http.get(searchUrl, userAgent)
        } catch (_: IOException) {
            return@withContext LyricsFetchResult.Unavailable(retryable = true)
        }
        if (searchResponse.code == 429 || searchResponse.code >= 500) {
            return@withContext LyricsFetchResult.Unavailable(
                retryable = true,
                retryAfterMs = retryAfterMsFor(searchResponse),
            )
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
        val album = obj.optString("albumName").takeIf { it.isNotBlank() && it != "null" }
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
                val score = LyricsMatcher.scoreCandidate(query, title, artist, durationMs, mode, album) ?: return null
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
            val score = LyricsMatcher.scoreCandidate(query, title, artist, durationMs, LyricsSyncMode.Plain, album)
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
            val score = LyricsMatcher.scoreCandidate(query, title, artist, durationMs, LyricsSyncMode.Plain, album)
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
        private fun retryAfterMsFor(response: LrcLibHttpResponse): Long =
            (response.retryAfterSeconds?.takeIf { it > 0L }?.times(1_000L) ?: DEFAULT_RETRY_AFTER_MS)
                .coerceIn(MIN_RETRY_AFTER_MS, MAX_RETRY_AFTER_MS)

        private const val BASE_URL = "https://lrclib.net"
        private const val DEFAULT_RETRY_AFTER_MS = 60_000L
        private const val MIN_RETRY_AFTER_MS = 15_000L
        private const val MAX_RETRY_AFTER_MS = 10 * 60_000L
        private const val DEFAULT_USER_AGENT = "BeyGram/Android (https://github.com/Beykus-Y/BeyGram)"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 10_000
        internal const val MAX_RESPONSE_BYTES = 512 * 1024

        internal fun readBoundedUtf8(stream: InputStream, maxBytes: Int = MAX_RESPONSE_BYTES): String {
            val buffer = ByteArray(8 * 1024)
            val out = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw IOException("LRCLIB response exceeds $maxBytes bytes")
                out.write(buffer, 0, read)
            }
            return out.toString(Charsets.UTF_8.name())
        }

        val DEFAULT_HTTP_CLIENT = LrcLibHttpClient { url, userAgent ->
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json")
            }
            val cancelHook = coroutineContext.job.invokeOnCompletion { cause ->
                if (cause != null) runCatching { connection.disconnect() }
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.use { readBoundedUtf8(it, MAX_RESPONSE_BYTES) }.orEmpty()
                LrcLibHttpResponse(code, body)
            } finally {
                cancelHook.dispose()
                connection.disconnect()
            }
        }
    }
}
