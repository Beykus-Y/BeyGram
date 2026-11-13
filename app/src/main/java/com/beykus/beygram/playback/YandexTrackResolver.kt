package com.beykus.beygram.playback

import android.content.Context
import android.os.SystemClock
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.data.music.provider.MusicProvider
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.MusicProviders
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.vpn.MihomoProxyClient
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val YANDEX_KEY_PREFIX = "ym:"

/** Bytes that must be on disk before a download starts playing: about one second of a 320 kbps track. */
internal const val YANDEX_PLAY_START_BYTES = 40_000L

/**
 * A download in progress. The downloader fills it in as the bytes arrive; the player reads [growingCopy] while the
 * file is still being written. [finished] and [failed] end it.
 */
internal class YandexDownload {
    @Volatile var path: File? = null
        private set
    @Volatile var totalBytes: Long = -1L
        private set
    val written = AtomicLong(0L)
    @Volatile var finished = false
        private set
    @Volatile var failed = false
        private set

    fun start(file: File, total: Long) {
        totalBytes = total
        written.set(0L)
        finished = false
        failed = false
        path = file
    }

    fun advance(bytes: Long) = written.set(bytes)

    fun finish() {
        finished = true
    }

    fun fail() {
        failed = true
    }

    /** A reader for the bytes written so far, or null while fewer than [YANDEX_PLAY_START_BYTES] are on disk. */
    fun growingCopy(): LazyGrowingCopy? {
        val file = path ?: return null
        val ready = finished || (!failed && written.get() >= YANDEX_PLAY_START_BYTES)
        if (!ready || failed) return null
        val total = totalBytes
        val growing = GrowingPlaybackSource(
            totalSize = total,
            readablePrefix = { written.get() },
            isComplete = { finished && !failed },
            completedPath = { if (finished && !failed) file.absolutePath else null },
            isDownloading = { !finished && !failed },
            onNeedMore = {},
            // Without a length the reader cannot seek to the end, so the flag stays until the download completes.
            advertiseLength = total > 0L,
        )
        return LazyGrowingCopy(file.absolutePath, growing)
    }
}

/** A short-lived direct link to the audio of one track. It is requested right before the download and never stored. */
internal data class YandexAudioLink(val url: String, val codec: String)

/**
 * Yandex Music tracks in the shared queue. A row holds only `ym:<trackId>[:<albumId>]`; the file is downloaded into a
 * private cache when the row becomes current or next. The link is asked for at that moment, because it expires within
 * about a minute, and asked again once when the server refuses it.
 *
 * Tracks of this source are restricted: they may not be forwarded, shared, exported or put on a profile.
 */
internal object YandexTrackResolver : LazyTrackResolver<String>(YANDEX_KEY_PREFIX) {
    const val PREFIX = YANDEX_KEY_PREFIX
    const val DIR_NAME = "music-yandex"

    override val restricted: Boolean = true

    private const val SEPARATOR = ':'

    fun keyOf(trackId: String, albumId: String?): String =
        if (albumId.isNullOrBlank()) "$PREFIX$trackId" else "$PREFIX$trackId$SEPARATOR$albumId"

    /** The provider track id inside a row key, or null when the key is not a Yandex key. */
    fun trackIdOf(key: String): String? {
        if (!key.startsWith(PREFIX)) return null
        return key.removePrefix(PREFIX).substringBefore(SEPARATOR).takeIf { it.isNotBlank() }
    }

    /**
     * Supplies the link for a track. The sign-in provides it; null means the track cannot be played for this account,
     * which the row reports as failed. Throwing means a temporary problem, and the row is tried again later.
     */
    fun configure(cacheDir: File, linkOf: suspend (trackId: String) -> YandexAudioLink?) {
        setFetch { key ->
            val trackId = trackIdOf(key) ?: return@setFetch Result.success(null)
            Result.success(fetchCopy(key, File(cacheDir, DIR_NAME), trackId, linkOf))
        }
    }

    /** Downloads in progress, by row key. A row is here from the moment its fetch starts until the file is complete. */
    private val downloads = ConcurrentHashMap<String, YandexDownload>()

    override fun growingCopy(key: String): LazyGrowingCopy? = downloads[key]?.growingCopy()

    private val wiringLock = Any()

    @Volatile
    private var wiredTo: MusicProvider? = null

    /**
     * Connects Yandex rows to the signed-in provider. The provider exists only in the DEV build, so Stable does nothing
     * here. Calling it again for the same provider does nothing.
     */
    fun ensureConfigured(context: Context) {
        val app = context.applicationContext
        val provider = MusicProviders.yandex(app) ?: return
        synchronized(wiringLock) {
            if (wiredTo === provider) return
            wiredTo = provider
        }
        configure(app.cacheDir) { trackId -> linkFor(provider, trackId) }
    }

    /**
     * A temporary failure throws, so the row is tried again after the cooldown. A verdict (no subscription, no sign-in,
     * track missing, changed service format) returns null, and the row fails like an unreadable file.
     */
    private suspend fun linkFor(provider: MusicProvider, trackId: String): YandexAudioLink? =
        when (val result = provider.audio(trackId)) {
            is ProviderResult.Success -> YandexAudioLink(result.value.url, result.value.codec)
            is ProviderResult.Failure -> when (result.failure) {
                // Sign-in and subscription can change while the app runs, so these are retried, never kept as verdicts.
                MusicProviderFailure.RateLimited,
                MusicProviderFailure.Unavailable,
                MusicProviderFailure.Network,
                MusicProviderFailure.SessionExpired,
                MusicProviderFailure.NotSignedIn,
                MusicProviderFailure.SubscriptionRequired,
                MusicProviderFailure.Unknown -> throw IOException("Yandex audio is not available right now")
                MusicProviderFailure.NotFound,
                MusicProviderFailure.ServiceChanged -> null
            }
        }

    private suspend fun fetchCopy(
        key: String,
        dir: File,
        trackId: String,
        linkOf: suspend (String) -> YandexAudioLink?,
    ): String? {
        val progress = YandexDownload()
        downloads[key] = progress
        try {
            val startedAt = SystemClock.elapsedRealtime()
            val first = linkOf(trackId) ?: return null
            val linkedAt = SystemClock.elapsedRealtime()
            var retried = false
            val path = try {
                YandexAudioDownloader.download(first, dir, progress)
            } catch (expired: YandexAudioDownloader.LinkExpired) {
                // The link lived less than the download took: one fresh link, then give up for this attempt.
                retried = true
                val fresh = linkOf(trackId) ?: return null
                YandexAudioDownloader.download(fresh, dir, progress)
            }
            recordFetchTiming(linkMs = linkedAt - startedAt, totalMs = SystemClock.elapsedRealtime() - startedAt, path.length(), retried)
            return path.absolutePath
        } finally {
            downloads.remove(key, progress)
        }
    }

    /**
     * How long a track took before it could play: the link, then the whole file. Only durations and the size are
     * recorded, never the track or the link, so a diagnostics report shows where the wait before the first sound goes.
     */
    private fun recordFetchTiming(linkMs: Long, totalMs: Long, bytes: Long, retried: Boolean) {
        val downloadMs = (totalMs - linkMs).coerceAtLeast(1L)
        recordAppEvent(
            kind = EventKind.PERFORMANCE,
            code = "provider_track_fetch",
            text = "Музыка: трек сервиса загружен за $totalMs мс",
            severity = Severity.INFO,
            attrs = mapOf(
                "link_ms" to linkMs.toString(),
                "download_ms" to downloadMs.toString(),
                "total_ms" to totalMs.toString(),
                "size_kb" to (bytes / 1024L).toString(),
                "kbps" to (bytes * 8L / downloadMs).toString(),
                "link_retried" to retried.toString(),
            )
        )
    }

    override fun pathOf(track: String): String = track

    override fun discardCopy(path: String) {
        YandexAudioDownloader.reserved.remove(path)
        File(path).delete()
    }

    override fun handOverCopy(path: String) {
        val dir = File(path).parentFile ?: return
        if (dir.name != DIR_NAME) return
        // The entry already names the copy (live), so it is safe to stop reserving it before the prune runs.
        YandexAudioDownloader.reserved.remove(path)
        pruneCopies(dir)
    }

    /** Removes copies that no queue row, live entry or download in progress still names. */
    private fun pruneCopies(dir: File) {
        val keep = livePaths() + PlaybackEngine.musicPathsInUse() + YandexAudioDownloader.reserved
        dir.listFiles().orEmpty().forEach { file ->
            if (file.absolutePath !in keep) file.delete()
        }
    }
}

/** Downloads one track over a direct connection. Nothing from the link is logged. */
internal object YandexAudioDownloader {
    /** The server refused the link: it expired or was revoked. */
    class LinkExpired : IOException("The audio link was refused")

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val COPY_BUFFER_BYTES = 16 * 1024

    /**
     * Paths being written, and finished copies the resolver has not recorded yet. A prune must not remove them: a
     * download reserves its path before the first byte, and keeps the reservation until the resolver takes the copy.
     */
    internal val reserved: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Returns the complete file. A partial download is removed, so a copy in the cache is always whole. [progress] is
     * filled in as the bytes arrive, so the player can start from the readable start of the file.
     * The connection bypasses the local proxy: the service answers only inside its own region.
     */
    fun download(link: YandexAudioLink, dir: File, progress: YandexDownload? = null): File {
        dir.mkdirs()
        val target = File(dir, "${UUID.randomUUID()}.${extensionFor(link.codec)}")
        reserved += target.absolutePath
        val connection = try {
            MihomoProxyClient.openConnection(URL(link.url), proxy = null)
        } catch (error: Throwable) {
            reserved -= target.absolutePath
            throw error
        }
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_FORBIDDEN || code == HttpURLConnection.HTTP_GONE) throw LinkExpired()
            if (code !in HttpURLConnection.HTTP_OK..299) throw IOException("Audio download answered $code")
            progress?.start(target, connection.contentLengthLong)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        // Flushed per chunk, so a reader following the file sees the bytes at once.
                        output.flush()
                        total += read
                        progress?.advance(total)
                    }
                }
            }
            if (target.length() == 0L) throw IOException("Audio download was empty")
            progress?.finish()
            return target
        } catch (error: Throwable) {
            reserved -= target.absolutePath
            progress?.fail()
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun extensionFor(codec: String): String = when (codec.lowercase()) {
        "mp3" -> "mp3"
        "aac", "he-aac", "heaac" -> "m4a"
        "flac" -> "flac"
        else -> "audio"
    }
}
