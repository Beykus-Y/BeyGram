package com.beykus.beygram.diagnostics.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPrivacyTest {

    private fun assertRedacted(marker: String, sensitive: String) {
        val result = ReportPrivacy.redact("событие: $sensitive конец")
        assertFalse("не должен выжить: $sensitive → $result", result.contains(sensitive))
        assertTrue(result.contains("[скрыто"))
    }

    @Test
    fun `bot token is removed`() {
        assertRedacted("[скрыто: токен]", "123456789:AAEk4Jf3-Example-TokenValue_1234567890")
    }

    @Test
    fun `phone numbers are removed`() {
        assertRedacted("[скрыто: номер]", "+7 999 123-45-67")
        assertRedacted("[скрыто: номер]", "89991234567")
    }

    @Test
    fun `jwt-like tokens are removed`() {
        assertRedacted("[скрыто: токен]", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJVadQssw5c")
    }

    @Test
    fun `proxy and subscription urls are removed`() {
        assertRedacted("[скрыто: ссылка]", "vmess://eyJhZGQiOiIxLjIuMy40In0=")
        assertRedacted("[скрыто: ссылка]", "https://user:pass@example.com/sub?token=secret")
        val http = ReportPrivacy.redact("https://example.com/path")
        assertFalse(http.contains("example.com"))
    }

    @Test
    fun `ip addresses v4 and v6 are removed`() {
        assertRedacted("[скрыто: адрес]", "149.154.175.50:443")
        assertRedacted("[скрыто: адрес]", "[2001:db8::1]:443")
    }

    @Test
    fun `hostnames are removed`() {
        assertRedacted("[скрыто: хост]", "my-proxy-provider.example.com:8443")
    }

    @Test
    fun `keyed secrets are removed regardless of case`() {
        assertRedacted("[скрыто]", "api_hash = 0123456789abcdef0123456789abcdef")
        assertRedacted("[скрыто]", "TOKEN: AbCdEf123456")
        assertRedacted("[скрыто]", "password=pass1234")
    }

    @Test
    fun `nonce-like base64 strings are removed`() {
        val nonce = ReportNonce.newNonce()
        val result = ReportPrivacy.redact("nonce=$nonce")
        assertFalse(result.contains(nonce))
    }

    @Test
    fun `plain technical text survives`() {
        val text = "Соединение: подключено; Wi-Fi, попытка 3, код ошибки 429"
        assertEquals(text, ReportPrivacy.redact(text))
    }

    @Test
    fun `sanitize strips control characters and limits by code points`() {
        val sanitized = ReportPrivacy.sanitize("a\u0001b\nc\td", 100)
        assertEquals("ab\nc\td", sanitized)
        val emoji = "🙂🙂🙂"
        // Обрезание не оставляет «половинки» суррогатной пары.
        assertTrue(ReportPrivacy.sanitize(emoji, 2).length % 2 == 0)
    }

    @Test
    fun `timestamps and version strings survive`() {
        val text = "2026-10-17 12:00:00.123 BeyGram/1.0-dev (Android 36) status=200"
        assertEquals(text, ReportPrivacy.redact(text))
    }
}
