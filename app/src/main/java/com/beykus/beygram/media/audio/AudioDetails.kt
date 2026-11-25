package com.beykus.beygram.media.audio

import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.playback.PlaybackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class AudioSourceOrigin {
    Telegram,
    YandexMusic,
    LocalFile,
}

data class AudioDetails(
    val sourceOrigin: AudioSourceOrigin,
    val fileAvailable: Boolean,
    val codec: String? = null,
    val container: String? = null,
    val bitrateKbps: Int? = null,
    val bitrateEstimated: Boolean = false,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val bitDepth: Int? = null,
    val fileSizeBytes: Long? = null,
    val lossless: Boolean = false,
)

/**
 * Off-UI-thread audio metadata probe that reads container/track header metadata via [MediaExtractor]
 * without decoding audio frames or scanning the full file.
 */
object AudioDetailsProbe {
    private const val MAX_CACHE_ENTRIES = 64
    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<String, AudioDetails>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AudioDetails>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    suspend fun probe(item: PlaybackItem): AudioDetails = withContext(Dispatchers.IO) {
        val origin = resolveSourceOrigin(item)
        val path = item.path.trim()
        val file = path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
        if (file == null) {
            return@withContext AudioDetails(
                sourceOrigin = origin,
                fileAvailable = false,
            )
        }

        val sizeBytes = file.length()
        val cacheKey = "${file.absolutePath}|$sizeBytes|${file.lastModified()}|${item.durationSeconds}"
        synchronized(cacheLock) {
            cache[cacheKey]?.let { return@withContext it }
        }

        val probed = probeFile(
            file = file,
            sizeBytes = sizeBytes,
            durationSeconds = item.durationSeconds,
            origin = origin,
            hideFileSize = item.systemPrivate,
        )
        synchronized(cacheLock) {
            cache[cacheKey] = probed
        }
        probed
    }

    internal fun clearCacheForTests() = synchronized(cacheLock) {
        cache.clear()
    }

    internal fun resolveSourceOrigin(item: PlaybackItem): AudioSourceOrigin = when {
        ProviderTracks.isProviderKey(item.key) -> AudioSourceOrigin.YandexMusic
        item.origin != null -> AudioSourceOrigin.Telegram
        else -> AudioSourceOrigin.LocalFile
    }

    private fun probeFile(
        file: File,
        sizeBytes: Long,
        durationSeconds: Int,
        origin: AudioSourceOrigin,
        hideFileSize: Boolean,
    ): AudioDetails {
        val container = extensionToContainerLabel(file.extension)
        var codec: String? = null
        var lossless = false
        var bitrateKbps: Int? = null
        var bitrateEstimated = false
        var sampleRateHz: Int? = null
        var channelCount: Int? = null
        var bitDepth: Int? = null

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/", ignoreCase = true)) continue

                codec = mimeToCodecLabel(mime)
                lossless = isLosslessCodec(mime)
                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    val bps = format.getInteger(MediaFormat.KEY_BIT_RATE)
                    if (bps > 0) {
                        bitrateKbps = (bps / 1000).coerceAtLeast(1)
                    }
                }
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    val sr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (sr > 0) sampleRateHz = sr
                }
                if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    val ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (ch > 0) channelCount = ch
                }
                bitDepth = extractBitDepth(format)
                break
            }
        } catch (_: Exception) {
            // Leave unavailable fields null; container/file size remain accurate if known.
        } finally {
            runCatching { extractor.release() }
        }

        if (bitrateKbps == null && sizeBytes > 0L && durationSeconds > 0) {
            val est = ((sizeBytes * 8L) / (durationSeconds.toLong() * 1000L)).toInt()
            if (est > 0) {
                bitrateKbps = est
                bitrateEstimated = true
            }
        }

        return AudioDetails(
            sourceOrigin = origin,
            fileAvailable = true,
            codec = codec,
            container = container,
            bitrateKbps = bitrateKbps,
            bitrateEstimated = bitrateEstimated,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            bitDepth = bitDepth,
            fileSizeBytes = if (hideFileSize) null else sizeBytes,
            lossless = lossless,
        )
    }

    private fun extractBitDepth(format: MediaFormat): Int? {
        if (format.containsKey("bits-per-sample")) {
            val bps = format.getInteger("bits-per-sample")
            if (bps in 8..64) return bps
        }
        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            return pcmEncodingToBitDepth(format.getInteger(MediaFormat.KEY_PCM_ENCODING))
        }
        return null
    }

    internal fun mimeToCodecLabel(mime: String): String = when (mime.lowercase()) {
        "audio/mpeg", "audio/mp3" -> "MP3"
        "audio/mp4a-latm", "audio/aac" -> "AAC"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/flac" -> "FLAC"
        "audio/alac" -> "ALAC"
        "audio/raw" -> "PCM"
        "audio/amr-wb" -> "AMR-WB"
        "audio/3gpp" -> "AMR-NB"
        else -> mime.substringAfter('/').uppercase()
    }

    internal fun extensionToContainerLabel(extension: String): String? = when (extension.lowercase()) {
        "mp3" -> "MP3"
        "m4a", "mp4" -> "MP4 / M4A"
        "aac" -> "AAC"
        "ogg", "oga" -> "Ogg"
        "opus" -> "Opus / Ogg"
        "flac" -> "FLAC"
        "wav" -> "WAV"
        "webm" -> "WebM"
        else -> extension.takeIf { it.isNotBlank() }?.uppercase()
    }

    internal fun pcmEncodingToBitDepth(encoding: Int): Int? = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 8
        AudioFormat.ENCODING_PCM_16BIT -> 16
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> null
    }

    internal fun isLosslessCodec(mime: String): Boolean = when (mime.lowercase()) {
        "audio/flac", "audio/alac", "audio/raw" -> true
        else -> false
    }

    internal fun formatSampleRateKhz(sampleRateHz: Int): String {
        val khz = sampleRateHz / 1000.0
        return if (sampleRateHz % 1000 == 0) {
            "${sampleRateHz / 1000} kHz"
        } else {
            String.format(java.util.Locale.US, "%.1f kHz", khz)
        }
    }
}
