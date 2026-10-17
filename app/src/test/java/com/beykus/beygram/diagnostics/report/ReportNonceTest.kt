package com.beykus.beygram.diagnostics.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.UUID

class ReportNonceTest {

    @Test
    fun `nonce is exactly 43 url-safe unpadded characters`() {
        val nonce = ReportNonce.newNonce()
        assertEquals(43, nonce.length)
        assertTrue("nonce must be URL-safe: $nonce", nonce.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        assertTrue("nonce must not contain padding", '+' !in nonce && '/' !in nonce && '=' !in nonce)
    }

    @Test
    fun `nonce decodes to exactly 32 bytes`() {
        val nonce = ReportNonce.newNonce()
        val bytes = Base64.getUrlDecoder().decode(nonce)
        assertEquals(32, bytes.size)
    }

    @Test
    fun `nonces are not repeated`() {
        val seen = mutableSetOf<String>()
        repeat(1000) { seen.add(ReportNonce.newNonce()) }
        assertEquals(1000, seen.size)
    }

    @Test
    fun `explicit random source is honored`() {
        val deterministic = object : java.security.SecureRandom() {
            override fun nextBytes(bytes: ByteArray) {
                bytes.fill(7)
            }
        }
        val first = ReportNonce.newNonce(deterministic)
        val second = ReportNonce.newNonce(deterministic)
        assertEquals(first, second)
        assertEquals("Bw", first.take(2)) // 32 байта нулей превращаются в 43 символа 'H'
    }

    @Test
    fun `report id is canonical uuid4`() {
        val id = ReportNonce.newReportId()
        val parsed = UUID.fromString(id)
        assertEquals(4, parsed.version())
        assertEquals(id, parsed.toString())
        assertNotEquals(id, ReportNonce.newReportId())
    }
}
