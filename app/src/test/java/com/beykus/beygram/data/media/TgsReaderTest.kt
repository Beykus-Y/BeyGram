package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

class TgsReaderTest {
    private val json = """{"v":"5.7.4","fr":60,"ip":0,"op":60,"w":512,"h":512,"layers":[]}"""

    private fun file(bytes: ByteArray): File = File.createTempFile("sticker", ".tgs").apply {
        deleteOnExit()
        writeBytes(bytes)
    }

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    @Test fun `gzipped sticker is read back as its JSON`() {
        assertEquals(json, readTgsJson(file(gzip(json))))
    }

    @Test fun `a file without the gzip header is read as plain JSON`() {
        assertEquals(json, readTgsJson(file(json.toByteArray(Charsets.UTF_8))))
    }
}
