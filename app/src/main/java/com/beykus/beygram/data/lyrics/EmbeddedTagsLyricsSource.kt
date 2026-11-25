package com.beykus.beygram.data.lyrics

import com.beykus.beygram.domain.lyrics.EnhancedLrcParser
import com.beykus.beygram.domain.lyrics.LyricsAttribution
import com.beykus.beygram.domain.lyrics.LyricsDocument
import com.beykus.beygram.domain.lyrics.LyricsFetchResult
import com.beykus.beygram.domain.lyrics.LyricsSource
import com.beykus.beygram.domain.lyrics.LyricsSourceId
import com.beykus.beygram.domain.lyrics.LyricsSyncMode
import com.beykus.beygram.domain.lyrics.RichLyricLine
import com.beykus.beygram.domain.music.ExternalMetadataPolicy
import com.beykus.beygram.domain.music.MetadataLookupDecision
import com.beykus.beygram.domain.music.TrackIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Reads embedded lyrics from local/downloaded audio files off the UI thread with strict byte bounds
 * (never scans the full audio file). Supports ID3v2 USLT frames (MP3) and FLAC Vorbis comments.
 */
class EmbeddedTagsLyricsSource : LyricsSource {

    override val id: LyricsSourceId = LyricsSourceId.EmbeddedTags
    override val isExternalNetwork: Boolean = false

    override fun supports(identity: TrackIdentity): Boolean =
        ExternalMetadataPolicy.checkOnDeviceTagRead(identity) == MetadataLookupDecision.Allowed

    override suspend fun fetch(identity: TrackIdentity): LyricsFetchResult = withContext(Dispatchers.IO) {
        if (!supports(identity)) return@withContext LyricsFetchResult.NotFound
        val path = identity.localFilePath ?: return@withContext LyricsFetchResult.NotFound
        val file = File(path)
        if (!file.isFile || file.length() < 16L) return@withContext LyricsFetchResult.NotFound

        val rawLyrics = runCatching { extractFromFile(file) }.getOrNull()?.trim()
        if (rawLyrics.isNullOrEmpty()) return@withContext LyricsFetchResult.NotFound

        val (syncMode, parsedLines) = EnhancedLrcParser.parse(rawLyrics)
        val attribution = LyricsAttribution(
            sourceId = LyricsSourceId.EmbeddedTags,
            sourceDisplayName = "Embedded tags",
            copyrightHolder = null,
            isExternalLookup = false,
        )
        val document = if (syncMode != LyricsSyncMode.Plain && parsedLines.isNotEmpty()) {
            LyricsDocument(
                syncMode = syncMode,
                lines = parsedLines,
                plainText = null,
                attribution = attribution,
            )
        } else {
            val plainLines = rawLyrics.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { RichLyricLine(startMs = null, text = it) }
                .toList()
            LyricsDocument(
                syncMode = LyricsSyncMode.Plain,
                lines = plainLines,
                plainText = rawLyrics,
                attribution = attribution,
            )
        }
        LyricsFetchResult.Found(document, matchScore = 0.95f)
    }

    internal fun extractFromFile(file: File): String? {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(10)
            if (raf.read(header) < 10) return null
            if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                return readId3v2Uslt(raf, header)
            }
            if (header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
                header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()
            ) {
                return readFlacVorbisLyrics(raf)
            }
            return null
        }
    }

    private fun readId3v2Uslt(raf: RandomAccessFile, header: ByteArray): String? {
        val majorVersion = header[3].toInt() and 0xFF
        if (majorVersion !in 3..4) return null
        val flags = header[5].toInt() and 0xFF
        val tagSize = syncSafeInt(header, 6)
        if (tagSize <= 0) return null
        val bytesToRead = minOf(tagSize, MAX_ID3_READ_BYTES)
        val payload = ByteArray(bytesToRead)
        val read = raf.read(payload)
        if (read <= 0) return null

        var pos = 0
        // Skip extended header if present (bit 6)
        if ((flags and 0x40) != 0 && pos + 4 <= read) {
            val extSize = if (majorVersion == 4) syncSafeInt(payload, pos) else bigEndianInt(payload, pos)
            pos += if (majorVersion == 4) extSize else extSize + 4
        }

        while (pos + 10 <= read) {
            if (payload[pos] == 0.toByte()) break
            val frameId = String(payload, pos, 4, StandardCharsets.ISO_8859_1)
            val frameSize = if (majorVersion == 4) syncSafeInt(payload, pos + 4) else bigEndianInt(payload, pos + 4)
            pos += 10
            if (frameSize <= 0 || pos + frameSize > read) break
            if (frameId == "USLT" && frameSize > 4) {
                decodeUsltFrame(payload, pos, frameSize)?.let { return it }
            }
            pos += frameSize
        }
        return null
    }

    private fun decodeUsltFrame(data: ByteArray, offset: Int, length: Int): String? {
        val encoding = data[offset].toInt() and 0xFF
        // Bytes offset+1..offset+3 are 3-byte language code
        val bodyStart = offset + 4
        val bodyEnd = offset + length
        if (bodyStart >= bodyEnd) return null
        val charset: Charset = when (encoding) {
            0 -> StandardCharsets.ISO_8859_1
            1 -> StandardCharsets.UTF_16
            2 -> StandardCharsets.UTF_16BE
            3 -> StandardCharsets.UTF_8
            else -> return null
        }
        val twoByteNull = encoding == 1 || encoding == 2
        val textStart = skipNullTerminatedDescriptor(data, bodyStart, bodyEnd, twoByteNull)
        if (textStart >= bodyEnd) return null
        return String(data, textStart, bodyEnd - textStart, charset).trim().takeIf { it.isNotEmpty() }
    }

    private fun skipNullTerminatedDescriptor(data: ByteArray, start: Int, end: Int, twoByte: Boolean): Int {
        var i = start
        if (twoByte) {
            while (i + 1 < end) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) return i + 2
                i += 2
            }
        } else {
            while (i < end) {
                if (data[i] == 0.toByte()) return i + 1
                i += 1
            }
        }
        return start
    }

    private fun readFlacVorbisLyrics(raf: RandomAccessFile): String? {
        raf.seek(4L)
        var isLast = false
        while (!isLast) {
            val blockHeader = ByteArray(4)
            if (raf.read(blockHeader) < 4) return null
            val first = blockHeader[0].toInt() and 0xFF
            isLast = (first and 0x80) != 0
            val blockType = first and 0x7F
            val blockLength = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                ((blockHeader[2].toInt() and 0xFF) shl 8) or
                (blockHeader[3].toInt() and 0xFF)
            if (blockLength < 0 || blockLength > MAX_FLAC_BLOCK_BYTES) return null
            if (blockType == 4) { // VORBIS_COMMENT
                val block = ByteArray(blockLength)
                if (raf.read(block) < blockLength) return null
                return parseVorbisCommentLyrics(block)
            } else {
                raf.seek(raf.filePointer + blockLength)
            }
        }
        return null
    }

    private fun parseVorbisCommentLyrics(block: ByteArray): String? {
        if (block.size < 8) return null
        var pos = 0
        val vendorLen = littleEndianInt(block, pos)
        pos += 4
        if (vendorLen < 0 || pos + vendorLen + 4 > block.size) return null
        pos += vendorLen
        val count = littleEndianInt(block, pos)
        pos += 4
        if (count < 0) return null
        var fallbackUnsynced: String? = null
        for (i in 0 until minOf(count, 512)) {
            if (pos + 4 > block.size) break
            val len = littleEndianInt(block, pos)
            pos += 4
            if (len < 0 || pos + len > block.size) break
            val entry = String(block, pos, len, StandardCharsets.UTF_8)
            pos += len
            val eq = entry.indexOf('=')
            if (eq <= 0) continue
            val key = entry.substring(0, eq).uppercase()
            val value = entry.substring(eq + 1).trim()
            if (value.isEmpty()) continue
            if (key == "LYRICS") return value
            if (key == "UNSYNCEDLYRICS" && fallbackUnsynced == null) fallbackUnsynced = value
        }
        return fallbackUnsynced
    }

    private fun syncSafeInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)

    private fun bigEndianInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    companion object {
        private const val MAX_ID3_READ_BYTES = 512 * 1024
        private const val MAX_FLAC_BLOCK_BYTES = 256 * 1024
    }
}
