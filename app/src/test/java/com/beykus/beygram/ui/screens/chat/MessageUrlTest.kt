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
}
