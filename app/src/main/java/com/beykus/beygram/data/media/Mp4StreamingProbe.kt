package com.beykus.beygram.data.media

import java.io.File
import java.io.RandomAccessFile

internal enum class Mp4StreamingReason {
    Streamable,
    NotMp4,
    MissingMoov,
    TruncatedMoov,
    MoovAfterMediaData,
    MoovTooLarge,
    NoVideoTrack,
    UnsupportedVideoCodec,
    NoAudioTrack,
    UnsupportedAudioCodec,
    ReadFailed
}

internal data class Mp4StreamingInfo(
    val isMp4: Boolean,
    val fastStart: Boolean,
    val videoCodecs: Set<String>,
    val streamable: Boolean,
    val reason: Mp4StreamingReason,
    val audioCodecs: Set<String> = emptySet()
)

/**
 * Checks whether an MP4 can be played progressively without a full local copy: the `moov` index must be
 * complete and located before `mdat`, and the video track must use a codec that Android/Telegram players
 * commonly support. `availableBytes` lets the player probe a partially downloaded prefix; the parser only
 * reads box headers and a bounded `moov`, never the media payload.
 *
 * A false result is a safe fallback: the caller keeps `supportsStreaming = false` for uploads and waits
 * for the complete file before playback. This probe does not remux or transcode anything.
 */
internal object Mp4StreamingProbe {
    /** A malformed or unusually large index is not trusted for progressive playback. */
    const val MAX_MOOV_BYTES = 16L * 1024 * 1024
    private const val MAX_TOP_LEVEL_BOXES = 512
    private const val MAX_CHILD_BOXES = 20_000
    private const val MAX_STSD_ENTRIES = 128

    private val CONTAINER_BOXES = setOf("moov", "trak", "mdia", "minf", "stbl")
    private val VIDEO_CODECS = setOf("avc1", "avc3", "hvc1", "hev1", "vp09", "av01", "mp4v")
    private val AUDIO_CODECS = setOf("mp4a", "ac-3", "ec-3", "Opus", "alac", "sowt", "twos", "ulaw", "alaw")

    fun inspect(file: File, availableBytes: Long = file.length()): Mp4StreamingInfo = try {
        RandomAccessFile(file, "r").use { source ->
            val length = minOf(source.length(), availableBytes.coerceAtLeast(0L))
            if (length < 8L) return notMp4()
            val boxes = topLevelBoxes(source, length)
            if (boxes.none { it.type == "ftyp" }) return notMp4()
            val moov = boxes.firstOrNull { it.type == "moov" }
                ?: return result(Mp4StreamingReason.MissingMoov, isMp4 = true)
            val mdat = boxes.firstOrNull { it.type == "mdat" }
            // An index after the media payload can only be read after the whole file arrives.
            if (mdat != null && moov.offset > mdat.offset) {
                return result(Mp4StreamingReason.MoovAfterMediaData, isMp4 = true)
            }
            if (moov.end > length) return result(Mp4StreamingReason.TruncatedMoov, isMp4 = true)
            if (moov.payloadSize > MAX_MOOV_BYTES) return result(Mp4StreamingReason.MoovTooLarge, isMp4 = true)
            val codecs = readCodecs(source, moov)
            val videoCodecs = codecs.intersect(VIDEO_CODECS)
            val audioOnly = codecs.isNotEmpty() && codecs.all { it in AUDIO_CODECS }
            return when {
                codecs.isEmpty() || audioOnly -> result(Mp4StreamingReason.NoVideoTrack, isMp4 = true)
                videoCodecs.isEmpty() -> result(
                    Mp4StreamingReason.UnsupportedVideoCodec, isMp4 = true, videoCodecs = codecs
                )
                else -> Mp4StreamingInfo(
                    isMp4 = true,
                    fastStart = true,
                    videoCodecs = videoCodecs,
                    streamable = true,
                    reason = Mp4StreamingReason.Streamable
                )
            }
        }
    } catch (_: Exception) {
        result(Mp4StreamingReason.ReadFailed, isMp4 = false)
    }

    fun supportsStreaming(file: File): Boolean = inspect(file).streamable

    /**
     * Audio-only MP4/M4A counterpart. The same structural rules apply (complete moov before mdat),
     * but the track must contain a supported audio sample description instead of video.
     */
    fun inspectAudio(file: File, availableBytes: Long = file.length()): Mp4StreamingInfo = try {
        RandomAccessFile(file, "r").use { source ->
            val length = minOf(source.length(), availableBytes.coerceAtLeast(0L))
            if (length < 8L) return result(Mp4StreamingReason.NotMp4, isMp4 = false)
            val boxes = topLevelBoxes(source, length)
            if (boxes.none { it.type == "ftyp" }) return result(Mp4StreamingReason.NotMp4, isMp4 = false)
            val moov = boxes.firstOrNull { it.type == "moov" }
                ?: return result(Mp4StreamingReason.MissingMoov, isMp4 = true)
            val mdat = boxes.firstOrNull { it.type == "mdat" }
            if (mdat != null && moov.offset > mdat.offset) {
                return result(Mp4StreamingReason.MoovAfterMediaData, isMp4 = true)
            }
            if (moov.end > length) return result(Mp4StreamingReason.TruncatedMoov, isMp4 = true)
            if (moov.payloadSize > MAX_MOOV_BYTES) return result(Mp4StreamingReason.MoovTooLarge, isMp4 = true)
            val codecs = readCodecs(source, moov)
            val audioCodecs = codecs.intersect(AUDIO_CODECS)
            return when {
                codecs.isEmpty() || audioCodecs.isEmpty() -> result(
                    Mp4StreamingReason.NoAudioTrack, isMp4 = true, audioCodecs = codecs
                )
                else -> Mp4StreamingInfo(
                    isMp4 = true,
                    fastStart = true,
                    videoCodecs = codecs.intersect(VIDEO_CODECS),
                    streamable = true,
                    reason = Mp4StreamingReason.Streamable,
                    audioCodecs = audioCodecs
                )
            }
        }
    } catch (_: Exception) {
        result(Mp4StreamingReason.ReadFailed, isMp4 = false)
    }

    fun supportsAudioStreaming(file: File, availableBytes: Long = file.length()): Boolean =
        inspectAudio(file, availableBytes).streamable

    private fun notMp4() = result(Mp4StreamingReason.NotMp4, isMp4 = false)

    private fun result(
        reason: Mp4StreamingReason,
        isMp4: Boolean,
        videoCodecs: Set<String> = emptySet(),
        audioCodecs: Set<String> = emptySet()
    ) = Mp4StreamingInfo(
        isMp4 = isMp4,
        fastStart = false,
        videoCodecs = videoCodecs,
        streamable = false,
        reason = reason,
        audioCodecs = audioCodecs
    )

    private fun topLevelBoxes(source: RandomAccessFile, length: Long): List<Box> {
        val boxes = ArrayList<Box>()
        var offset = 0L
        while (offset + 8L <= length && boxes.size < MAX_TOP_LEVEL_BOXES) {
            val header = readHeader(source, offset, length) ?: break
            val end = safeEnd(offset, header.size)
            boxes += Box(header.type, offset, header.headerSize, header.size)
            if (header.size <= 0L || end <= offset || end > length) break
            offset = end
        }
        return boxes
    }

    private fun readHeader(source: RandomAccessFile, offset: Long, limit: Long): BoxHeader? {
        if (offset + 8L > limit) return null
        source.seek(offset)
        val size32 = uint32(source.readInt())
        val type = fourCc(source)
        var size = size32
        var headerSize = 8L
        if (size32 == 1L) {
            if (offset + 16L > limit) return null
            size = source.readLong()
            headerSize = 16L
            if (size < 0L) return null
        } else if (size32 == 0L) {
            size = limit - offset
        }
        if (size < headerSize) return null
        return BoxHeader(type, size, headerSize)
    }

    private fun readCodecs(source: RandomAccessFile, moov: Box): Set<String> {
        val bytes = ByteArray(moov.payloadSize.toInt())
        source.seek(moov.offset + moov.headerSize)
        source.readFully(bytes)
        return scanCodecs(bytes)
    }

    private fun scanCodecs(bytes: ByteArray): Set<String> {
        val codecs = linkedSetOf<String>()
        var visited = 0
        fun scan(start: Int, end: Int, depth: Int) {
            if (depth > 8) return
            val endLong = end.toLong()
            var offset = start.toLong()
            while (offset + 8L <= endLong && visited < MAX_CHILD_BOXES) {
                visited++
                val boxOffset = offset.toInt()
                val size32 = uint32(bytes, boxOffset)
                val type = fourCc(bytes, boxOffset + 4)
                val headerSize = when (size32) {
                    1L -> 16L
                    0L -> endLong - offset
                    else -> 8L
                }
                val size = when {
                    size32 == 1L && offset + 16L <= endLong -> uint64(bytes, boxOffset + 8)
                    size32 == 0L -> endLong - offset
                    else -> size32
                }
                if (size < headerSize || size > Int.MAX_VALUE || offset + size > endLong) return
                val payloadStart = (offset + headerSize).toInt()
                val payloadEnd = (offset + size).toInt()
                when (type) {
                    "stsd" -> readSampleDescriptions(bytes, payloadStart, payloadEnd, codecs)
                    in CONTAINER_BOXES -> scan(payloadStart, payloadEnd, depth + 1)
                }
                if (size == 0L) return
                offset += size
            }
        }
        scan(0, bytes.size, 0)
        return codecs
    }

    private fun readSampleDescriptions(bytes: ByteArray, start: Int, end: Int, codecs: MutableSet<String>) {
        if (start + 8 > end) return
        val count = uint32(bytes, start + 4).coerceAtMost(MAX_STSD_ENTRIES.toLong()).toInt()
        var offset = start + 8
        repeat(count) {
            if (offset + 8 > end) return
            val size = uint32(bytes, offset)
            val type = fourCc(bytes, offset + 4)
            if (size < 8L || size > Int.MAX_VALUE || offset.toLong() + size > end.toLong()) return
            codecs += type.trim()
            offset += size.toInt()
        }
    }

    private fun safeEnd(offset: Long, size: Long): Long =
        if (size > Long.MAX_VALUE - offset) Long.MAX_VALUE else offset + size

    private fun uint32(value: Int): Long = value.toLong() and 0xFFFF_FFFFL

    private fun uint32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFFL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 8) or
            (bytes[offset + 3].toLong() and 0xFFL)

    private fun uint64(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        repeat(8) { index -> value = (value shl 8) or (bytes[offset + index].toLong() and 0xFFL) }
        return value
    }

    private fun fourCc(source: RandomAccessFile): String {
        val bytes = ByteArray(4)
        source.readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun fourCc(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, Charsets.US_ASCII)

    private data class BoxHeader(val type: String, val size: Long, val headerSize: Long)

    private data class Box(
        val type: String,
        val offset: Long,
        val headerSize: Long,
        val size: Long
    ) {
        val payloadSize: Long get() = size - headerSize
        val end: Long get() = offset + size
    }
}
