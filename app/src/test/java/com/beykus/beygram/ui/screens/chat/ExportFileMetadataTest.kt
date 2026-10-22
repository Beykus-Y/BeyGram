package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFileMetadataTest {
    private val extensions = mapOf(
        "application/pdf" to "pdf", "image/jpeg" to "jpg", "audio/mpeg" to "mp3",
        "application/octet-stream" to "bin"
    )
    private val mimes = mapOf("pdf" to "application/pdf", "jpg" to "image/jpeg", "mp3" to "audio/mpeg")

    private fun metadata(name: String, mime: String = "application/octet-stream") =
        resolveExportFileMetadata(name, mime, "Файл", extensions::get, mimes::get)

    @Test fun signaturesRecoverKnownTypesWithoutOriginalExtension() {
        val cases = listOf(
            "%PDF-1.7".toByteArray() to ExportSignatureHint("pdf", "application/pdf"),
            byteArrayOf(-119,80,78,71,13,10,26,10) to ExportSignatureHint("png", "image/png"),
            byteArrayOf(-1, -40, -1, 0) to ExportSignatureHint("jpg", "image/jpeg"),
            "GIF89a".toByteArray() to ExportSignatureHint("gif", "image/gif"),
            "OggS".toByteArray() to ExportSignatureHint("ogg", "application/ogg"),
            "fLaC".toByteArray() to ExportSignatureHint("flac", "audio/flac")
        )
        cases.forEach { (header, expected) ->
            assertEquals(expected, detectExportSignature(header))
            val m = resolveExportFileMetadata("", "application/octet-stream", "Файл",
                extensions::get, mimes::get, expected)
            assertEquals("Файл.${expected.extension}", m.displayName)
            assertEquals(expected.mimeType, m.mimeType)
        }
    }

    @Test fun declaredMetadataAlwaysWinsAndUnknownBytesRemainGeneric() {
        val pdf = ExportSignatureHint("pdf", "application/pdf")
        val knownName = resolveExportFileMetadata("book.epub", "application/octet-stream", "Файл",
            extensions::get, mimes::get, pdf)
        assertEquals("book.epub", knownName.displayName)
        assertEquals("application/octet-stream", knownName.mimeType)
        assertEquals("clip.jpg", resolveExportFileMetadata("clip", "image/jpeg", "Файл",
            extensions::get, mimes::get, pdf).displayName)
        assertEquals(null, detectExportSignature(byteArrayOf(0, 1, 2, 3, 4)))
        // An ID3 tag alone may prefix non-MP3 audio, and OggS is a container.
        assertEquals(null, detectExportSignature("ID3".toByteArray()))
    }

    @Test fun signatureReaderHandlesFilePrefixAndMissingFiles() {
        val file = java.io.File.createTempFile("beygram-export-test-", ".cache")
        try {
            val bytes = "%PDF-1.5\\n".toByteArray() + ByteArray(2048) { 42 }
            file.writeBytes(bytes)
            assertEquals(ExportSignatureHint("pdf", "application/pdf"), readExportSignature(file))
            // Inspection must never modify the downloaded Telegram cache content.
            assertEquals(bytes.toList(), file.readBytes().toList())
        } finally {
            file.delete()
        }
        assertEquals(null, readExportSignature(file))
    }

    @Test fun zipPrefixDoesNotNameUnnamedFiles() {
        // DOCX, APK and EPUB share the ZIP prefix, so a file without a name must not become .zip.
        val zipPrefix = byteArrayOf(80, 75, 3, 4)
        assertEquals(null, detectExportSignature(zipPrefix))
        val unnamed = resolveExportFileMetadata("", "application/octet-stream", "Файл",
            extensions::get, mimes::get, detectExportSignature(zipPrefix))
        assertEquals("Файл", unnamed.displayName)
        assertEquals("application/octet-stream", unnamed.mimeType)
    }

    @Test fun originalFilenameNeverGetsBinAppended() {
        assertEquals("archive.zip", metadata("archive.zip").displayName)
        assertEquals("report.PDF", metadata("report.PDF").displayName)
        assertEquals("sample.mp3", metadata("sample.mp3", "audio/mpeg").displayName)
    }

    @Test fun derivesMimeOnlyWhenTheOriginalMimeIsGeneric() {
        assertEquals("application/pdf", metadata("report.PDF").mimeType)
        assertEquals("application/octet-stream", metadata("archive.unknown").mimeType)
        assertEquals("video/mp4", metadata("movie.mp4", "video/mp4").mimeType)
    }

    @Test fun addsExtensionOnlyForKnownTypeAndExtensionlessFilename() {
        assertEquals("picture.jpg", metadata("picture", "image/jpeg").displayName)
        assertEquals("Файл", metadata("", "application/octet-stream").displayName)
        assertEquals("data", metadata("data", "application/octet-stream").displayName)
        assertEquals("picture.jpeg", metadata("picture.jpeg", "image/jpeg").displayName)
    }

    @Test fun normalizesUntrustedPathsAndControlCharacters() {
        assertEquals("report.pdf", metadata("../folder/report.pdf").displayName)
        assertEquals("clip.mp3", metadata("C:\\temp\\clip.mp3").displayName)
        assertEquals("unsafe_name.pdf", metadata("unsafe\u0000name.pdf").displayName)
    }
}
