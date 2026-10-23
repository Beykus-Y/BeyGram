package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.*
import org.junit.Test

class MessageUrlTest {
    @Test fun `plain web links and Telegram links remain navigable`() {
        assertEquals("https://example.com/path", safeMessageUrl("example.com/path"))
        assertEquals("tg://user?id=123", safeMessageUrl("tg://user?id=123"))
        assertEquals("mailto:a@example.com", safeMessageUrl("mailto:a@example.com"))
    }
    @Test fun `bot links cannot execute script file or intent schemes`() {
        listOf("javascript:alert(1)", "file:///data/data/private", "intent://foo#Intent;end", "https://").forEach { assertNull(safeMessageUrl(it)) }
    }
    @Test fun `link cards open only web urls and fall back to their host`() {
        assertEquals("https://example.com/story", safeLinkPreviewUrl("example.com/story"))
        listOf("tg://user?id=1", "mailto:a@example.com", "tel:+123", "javascript:alert(1)").forEach { assertNull(safeLinkPreviewUrl(it)) }
        assertEquals("example.com", linkPreviewHost("https://www.example.com/story"))
        assertEquals("", linkPreviewHost(""))
    }
}
