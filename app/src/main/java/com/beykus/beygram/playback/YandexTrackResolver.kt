package com.beykus.beygram.playback

import com.beykus.beygram.data.vpn.MihomoProxyClient
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val YANDEX_KEY_PREFIX = "ym:"

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
            Result.success(fetchCopy(File(cacheDir, DIR_NAME), trackId, linkOf))
        }
    }

    private suspend fun fetchCopy(
        dir: File,
        trackId: String,
        linkOf: suspend (String) -> YandexAudioLink?,
    ): String? {
        val first = linkOf(trackId) ?: return null
        val path = try {
            YandexAudioDownloader.download(first, dir)
        } catch (expired: YandexAudioDownloader.LinkExpired) {
            // The link lived less than the download took: one fresh link, then give up for this attempt.
            val fresh = linkOf(trackId) ?: return null
            YandexAudioDownloader.download(fresh, dir)
        }
        return path.absolutePath
    }

    override fun pathOf(track: String): String = track

    override fun discardCopy(path: String) {
        File(path).delete()
    }

    override fun handOverCopy(path: String) {
        val dir = File(path).parentFile ?: return
        if (dir.name != DIR_NAME) return
        pruneCopies(dir)
    }

    /** Removes copies that neither a queue row nor a live entry still names. */
    private fun pruneCopies(dir: File) {
        val keep = livePaths() + PlaybackEngine.musicPathsInUse()
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

    /**
     * Returns the complete file. A partial download is removed, so a copy in the cache is always whole.
     * The connection bypasses the local proxy: the service answers only inside its own region.
     */
    fun download(link: YandexAudioLink, dir: File): File {
        dir.mkdirs()
        val target = File(dir, "${UUID.randomUUID()}.${extensionFor(link.codec)}")
        val connection = MihomoProxyClient.openConnection(URL(link.url), proxy = null)
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_FORBIDDEN || code == HttpURLConnection.HTTP_GONE) throw LinkExpired()
            if (code !in HttpURLConnection.HTTP_OK..299) throw IOException("Audio download answered $code")
            connection.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            if (target.length() == 0L) throw IOException("Audio download was empty")
            return target
        } catch (error: Throwable) {
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
