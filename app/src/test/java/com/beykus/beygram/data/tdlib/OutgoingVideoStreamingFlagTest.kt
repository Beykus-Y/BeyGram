package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import java.io.ByteArrayOutputStream
import java.io.File
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The outgoing `supports_streaming` flag is a container fact (#1175): a fast-start MP4 may be streamed by clients that
 * decode its codec, an MP4 with `moov` at the end may not. It must not depend on H.264 alone.
 */
class OutgoingVideoStreamingFlagTest {
    @Test
    fun `a fast-start hevc mp4 is offered as streamable`() {
        val file = tempFile(fastStartBytes("hvc1"))

        val video = media(file).toInputContent(caption = "") as TdApi.InputMessageVideo

        assertTrue(video.video.supportsStreaming)
    }

    @Test
    fun `an mp4 with the index at the end is not offered as streamable`() {
        val file = tempFile(concat(ftyp(), mdat(64), moov("hvc1")))

        val video = media(file).toInputContent(caption = "") as TdApi.InputMessageVideo

        assertFalse(video.video.supportsStreaming)
    }

    @Test
    fun `a fast-start avc mp4 stays streamable`() {
        val file = tempFile(fastStartBytes("avc1"))

        val video = media(file).toInputContent(caption = "") as TdApi.InputMessageVideo

        assertTrue(video.video.supportsStreaming)
    }

    private fun media(file: File) = OutgoingMedia(
        path = file.absolutePath,
        fileName = file.name,
        mimeType = "video/mp4",
        kind = MediaKind.Video,
        width = 720,
        height = 1280,
        durationSeconds = 5,
        size = file.length(),
    )

    private fun fastStartBytes(codec: String): ByteArray = concat(ftyp(), moov(codec), mdat(64))

    private fun moov(codec: String): ByteArray =
        box("moov", box("trak", box("mdia", box("minf", box("stbl", stsd(codec, "mp4a"))))))

    private fun ftyp(): ByteArray = box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + ByteArray(4))

    private fun mdat(size: Int): ByteArray = box("mdat", ByteArray(size))

    private fun stsd(vararg codecs: String): ByteArray {
        val entries = ByteArrayOutputStream()
        codecs.forEach { codec -> entries.write(box(codec, ByteArray(78))) }
        val payload = ByteArrayOutputStream()
        payload.write(ByteArray(4))
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

    private fun uint32(value: Long): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFFL).toByte(),
        ((value shr 16) and 0xFFL).toByte(),
        ((value shr 8) and 0xFFL).toByte(),
        (value and 0xFFL).toByte(),
    )

    private fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("outgoing-video", ".mp4").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
}