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
