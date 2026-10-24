package com.beykus.beygram.data.media

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4StreamingProbeTest {
    @Test fun fastStartMp4IsStreamable() {
        val file = tempFile(fastStartBytes("avc1"))
        val info = Mp4StreamingProbe.inspect(file)
        assertTrue(info.isMp4)
        assertTrue(info.fastStart)
        assertTrue(info.streamable)
        assertEquals(Mp4StreamingReason.Streamable, info.reason)
        assertTrue(info.videoCodecs.contains("avc1"))
    }

    @Test fun hevcIsAlsoRecognized() {
        assertTrue(Mp4StreamingProbe.inspect(tempFile(fastStartBytes("hvc1"))).streamable)
    }

    @Test fun moovAtTheEndIsNotStreamable() {
        val bytes = concat(ftyp(), mdat(32), moovWithVideo("avc1"))
        val info = Mp4StreamingProbe.inspect(tempFile(bytes))
        assertFalse(info.streamable)
        assertEquals(Mp4StreamingReason.MoovAfterMediaData, info.reason)
    }

    @Test fun truncatedIndexIsNotStreamable() {
        val full = fastStartBytes("avc1")
        val moovEnd = ftyp().size + moovWithVideo("avc1").size
        val info = Mp4StreamingProbe.inspect(tempFile(full), availableBytes = (moovEnd - 4).toLong())
        assertFalse(info.streamable)
        assertEquals(Mp4StreamingReason.TruncatedMoov, info.reason)
    }

    @Test fun prefixEndingAfterCompleteMoovCanStartPlaying() {
        val full = fastStartBytes("avc1")
        val afterMoov = (ftyp().size + moovWithVideo("avc1").size).toLong()
        val info = Mp4StreamingProbe.inspect(tempFile(full), availableBytes = afterMoov)
        assertTrue(info.streamable)
        assertEquals(Mp4StreamingReason.Streamable, info.reason)
    }

    @Test fun unsupportedVideoCodecDoesNotClaimStreaming() {
        val info = Mp4StreamingProbe.inspect(tempFile(fastStartBytes("jpeg")))
        assertFalse(info.streamable)
        assertEquals(Mp4StreamingReason.UnsupportedVideoCodec, info.reason)
    }

    @Test fun audioOnlyMp4DoesNotClaimVideoStreaming() {
        val bytes = concat(ftyp(), box("moov", box("trak", box("mdia", box("minf", box("stbl", stsd("mp4a")))))), mdat(16))
        val info = Mp4StreamingProbe.inspect(tempFile(bytes))
        assertFalse(info.streamable)
        assertEquals(Mp4StreamingReason.NoVideoTrack, info.reason)
    }

    @Test fun corruptAndEmptyFilesFailClosed() {
        assertEquals(Mp4StreamingReason.NotMp4, Mp4StreamingProbe.inspect(tempFile(ByteArray(0))).reason)
        val corrupt = concat(ftyp(), byteArrayOf(0, 0, 0, 2, 'f'.code.toByte(), 'r'.code.toByte(), 'e'.code.toByte(), 'e'.code.toByte()))
        val info = Mp4StreamingProbe.inspect(tempFile(corrupt))
        assertFalse(info.streamable)
        assertEquals(Mp4StreamingReason.MissingMoov, info.reason)
    }

    @Test fun sixtyFourBitMdatSizeStillFindsTheIndexFirst() {
        val bytes = concat(ftyp(), moovWithVideo("avc1"), box64("mdat", ByteArray(16)))
        assertTrue(Mp4StreamingProbe.inspect(tempFile(bytes)).streamable)
    }

    private fun fastStartBytes(codec: String): ByteArray =
        concat(ftyp(), moovWithVideo(codec), mdat(32))

    private fun moovWithVideo(codec: String): ByteArray =
        box("moov", box("trak", box("mdia", box("minf", box("stbl", stsd(codec, "mp4a"))))))

    private fun ftyp(): ByteArray =
        box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + ByteArray(4))

    private fun mdat(size: Int): ByteArray = box("mdat", ByteArray(size))

    private fun stsd(vararg codecs: String): ByteArray {
        val entries = ByteArrayOutputStream()
        codecs.forEach { codec -> entries.write(box(codec, ByteArray(78))) }
        val payload = ByteArrayOutputStream()
        payload.write(ByteArray(4)) // version and flags
        payload.write(uint32(codecs.size.toLong()))
        payload.write(entries.toByteArray())
        return box("stsd", payload.toByteArray())
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(uint32((8 + payload.size).toLong()))
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(payload)
        return out.toByteArray()
    }

    private fun box64(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(uint32(1))
        out.write(type.toByteArray(Charsets.US_ASCII))
        val size = 16L + payload.size
        repeat(8) { index -> out.write(((size shr (56 - index * 8)) and 0xFFL).toInt()) }
        out.write(payload)
        return out.toByteArray()
    }

    private fun uint32(value: Long): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFFL).toByte(),
        ((value shr 16) and 0xFFL).toByte(),
        ((value shr 8) and 0xFFL).toByte(),
        (value and 0xFFL).toByte()
    )

    private fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("mp4-probe", ".mp4").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
}
