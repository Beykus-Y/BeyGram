package com.beykus.beygram.diagnostics.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the Codex review of #1201: trusted numeric metrics are preserved at the *structured* boundary,
 * where the attribute key is known, while free text stays strictly redacted.
 */
class ReportPrivacyAttributeTest {
    @Test
    fun `a trusted numeric attribute keeps its digits`() {
        assertEquals("24641536", ReportPrivacy.sanitizeAttribute("file_size_bytes", "24641536", 100))
        assertEquals("1048576", ReportPrivacy.sanitizeAttribute("prefix_bytes", "1048576", 100))
        assertEquals("8388608", ReportPrivacy.sanitizeAttribute("bytes_read", "8388608", 100))
        assertEquals("149", ReportPrivacy.sanitizeAttribute("last_seek_to_ready_ms", "149", 100))
    }

    @Test
    fun `an unknown key still redacts a long number`() {
        assertEquals("[скрыто: номер]", ReportPrivacy.sanitizeAttribute("message_id", "79161234567", 100))
        assertEquals("[скрыто: номер]", ReportPrivacy.sanitizeAttribute("custom_field", "1048576", 100))
    }

    @Test
    fun `a trusted key with a phone-shaped value is still redacted`() {
        val sanitized = ReportPrivacy.sanitizeAttribute("bytes_read", "+7 916 123-45-67", 100)
        assertFalse(sanitized.contains("916"))
        assertTrue(sanitized.contains("[скрыто"))
    }

    @Test
    fun `a trusted key with free text is fully sanitized`() {
        val sanitized = ReportPrivacy.sanitizeAttribute("bytes_read", "видео 79161234567 https://example.com/x", 100)
        assertFalse(sanitized.contains("79161234567"))
        assertFalse(sanitized.contains("example.com"))
    }

    @Test
    fun `free text is never trusted by substring`() {
        // The old regex allowlist could keep a long number merely because "file_size_bytes=" appeared nearby.
        val text = "в сообщении 79161234567 и рядом file_size_bytes=1048576"
        assertFalse(ReportPrivacy.redact(text).contains("79161234567"))
    }

    @Test
    fun `a numeric attribute is length-limited`() {
        assertEquals("12345", ReportPrivacy.sanitizeAttribute("file_size_bytes", "123456789", 5))
    }
}