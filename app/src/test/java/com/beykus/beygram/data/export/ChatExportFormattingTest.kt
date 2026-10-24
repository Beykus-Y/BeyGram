package com.beykus.beygram.data.export

import com.beykus.beygram.domain.model.ChatExportFormat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExportFormattingTest {
    @Test fun `json escaping round trips quotes slashes and control characters`() {
        val value = "quote \" backslash \\ newline\n tab\t control \u0001 snowman ☃"
        val parsed = JSONObject("{\"value\":${jsonString(value)}}").getString("value")
        assertEquals(value, parsed)
    }

    @Test fun `json escaping replaces unpaired surrogates and escapes line separators`() {
        assertEquals("\"a\\ufffdb\"", jsonString("a\uD800b"))
        assertEquals("\"a\\u2028b\\u2029c\"", jsonString("a\u2028b\u2029c"))
    }

    @Test fun `html escaping covers text and attribute metacharacters`() {
        assertEquals(
            "&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; &#39;tag&#39;",
            htmlEscape("<script>alert(\"x\")</script> & 'tag'")
        )
    }

    @Test fun `file names cannot contain path traversal or separators`() {
        val name = safeExportFileName("../../etc/passwd", "fallback")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains(".."))
        assertTrue(name.startsWith("passwd") || name.startsWith("_"))
        assertEquals("fallback", safeExportFileName("..", "fallback"))
        assertEquals("fallback", safeExportFileName("   ", "fallback"))
    }

    @Test fun `file names are bounded and keep the requested extension`() {
        val name = safeExportFileNameWithExtension("a".repeat(300), "chat", "json")
        assertTrue(name.length <= 96)
        assertTrue(name.endsWith(".json"))
    }

    @Test fun `media entry names use a safe unique path`() {
        val media = ChatExportMedia(
            kind = com.beykus.beygram.domain.model.MediaKind.Document,
            fileId = 7,
            fileName = "../../secret report.pdf",
            mimeType = "application/pdf"
        )
        val entry = mediaEntryName(42L, media)
        assertTrue(entry.startsWith("media/42_"))
        assertTrue(entry.endsWith(".pdf"))
        assertFalse(entry.contains(".."))
        assertEquals("media/42_document.bin", mediaEntryName(42L, media.copy(fileName = "", mimeType = "application/octet-stream")))
    }

    @Test fun `date range is inclusive at the start and exclusive at the end`() {
        val range = com.beykus.beygram.domain.model.ChatExportRequest(
            format = ChatExportFormat.Json,
            fromDateSeconds = 100L,
            toDateSeconds = 200L
        )
        assertFalse(range.includesDate(99L))
        assertTrue(range.includesDate(100L))
        assertTrue(range.includesDate(199L))
        assertFalse(range.includesDate(200L))
        assertFalse(range.includesDate(0L))
        assertFalse(
            com.beykus.beygram.domain.model.ChatExportRequest(fromDateSeconds = 200L, toDateSeconds = 100L)
                .hasValidDateRange
        )
    }
}
