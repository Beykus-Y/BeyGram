package com.beykus.beygram.media.bgwave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class WaveCodecTest {

    private val envelope = WaveEnvelope(
        type = WaveMessageType.OFFER,
        sidHex = "0123456789abcdef0123456789abcdef",
        nonceHex = "fedcba9876543210fedcba9876543210",
        issuedAt = 1800000000L,
        expiresAt = 1800000300L,
        caps = listOf("opus48s", "p2p1"),
        signal = "ice-1",
    )
    private val now = 1800000100L

    // Golden fixtures. Computed once from the v1 payload above; any change here is a wire break.
    private val payloadGolden =
        "v=1;t=offer;s=0123456789abcdef0123456789abcdef;i=1800000000;e=1800000300;" +
            "c=opus48s,p2p1;n=fedcba9876543210fedcba9876543210;g=aWNlLTE"
    private val bodyGolden =
        "dj0xO3Q9b2ZmZXI7cz0wMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjtpPTE4MDAwMDAwMDA7" +
            "ZT0xODAwMDAwMzAwO2M9b3B1czQ4cyxwMnAxO249ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA7Zz1hV05sTFRF"

    private fun internalLink(payload: String): String =
        WaveProtocol.INTERNAL_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.US_ASCII))

    @Test
    fun encodeMatchesGoldenFixtures() {
        assertEquals(payloadGolden, WaveCodec.encodePayload(envelope))
        assertEquals(WaveProtocol.INTERNAL_PREFIX + bodyGolden, WaveCodec.encodeInternal(envelope))
        assertEquals(WaveProtocol.TEXT_LINK_PREFIX + bodyGolden, WaveCodec.encodeTextLink(envelope))
    }

    @Test
    fun decodesInternalAndTextLinkRoundTrip() {
        assertEquals(
            WaveDecodeResult.Ok(envelope),
            WaveCodec.decode(WaveCodec.encodeInternal(envelope), now),
        )
        assertEquals(
            WaveDecodeResult.Ok(envelope),
            WaveCodec.decode("  " + WaveCodec.encodeTextLink(envelope) + "\n", now),
        )
    }

    @Test
    fun emptyCapsAndSignalRoundTrip() {
        val bare = envelope.copy(caps = emptyList(), signal = "")
        assertEquals(WaveDecodeResult.Ok(bare), WaveCodec.decode(WaveCodec.encodeInternal(bare), now))
    }

    @Test
    fun foreignTextIsNotWave() {
        assertEquals(
            WaveDecodeResult.Rejected(WaveRejectReason.NOT_WAVE),
            WaveCodec.decode("https://example.com/#bgwave", now),
        )
    }

    @Test
    fun unknownVersionIsReportedNotMalformed() {
        val v2 = payloadGolden.replace("v=1;", "v=2;")
        assertEquals(
            WaveDecodeResult.Rejected(WaveRejectReason.UNSUPPORTED_VERSION),
            WaveCodec.decode(internalLink(v2), now),
        )
    }

    @Test
    fun duplicateOrUnknownFieldsAreMalformed() {
        assertEquals(
            WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED),
            WaveCodec.decode(internalLink("$payloadGolden;i=1800000000"), now),
        )
        assertEquals(
            WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED),
            WaveCodec.decode(internalLink("$payloadGolden;x=1"), now),
        )
    }

    @Test
    fun invalidBase64AndEmptyBodyAreMalformed() {
        assertEquals(
            WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED),
            WaveCodec.decode(WaveProtocol.INTERNAL_PREFIX + "!!!", now),
        )
        assertEquals(
            WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED),
            WaveCodec.decode(WaveProtocol.INTERNAL_PREFIX + "A", now),
        )
    }

    @Test
    fun expiredAndNotYetValidAreRejected() {
        assertEquals(
            WaveDecodeResult.Rejected(WaveRejectReason.EXPIRED),
            WaveCodec.decode(WaveCodec.encodeInternal(envelope), 1800000300L + 301L),
        )
        assertEquals(
            WaveDecodeResult.Rejected(WaveRejectReason.NOT_YET_VALID),
            WaveCodec.decode(WaveCodec.encodeInternal(envelope), 1800000000L - 301L),
        )
        // Within the clock skew window the envelope is still accepted.
        assertTrue(WaveCodec.decode(WaveCodec.encodeInternal(envelope), 1800000300L + 300L) is WaveDecodeResult.Ok)
    }

    @Test
    fun overlongTtlIsRejected() {
        val long = payloadGolden.replace("e=1800000300", "e=1800000601")
        assertEquals(
            WaveDecodeResult.Rejected(WaveRejectReason.TTL_TOO_LONG),
            WaveCodec.decode(internalLink(long), 1800000000L),
        )
    }

    @Test
    fun oversizedLinkIsRejectedBeforeDecoding() {
        val huge = WaveProtocol.INTERNAL_PREFIX + "A".repeat(WaveProtocol.MAX_LINK_CHARS)
        assertEquals(WaveDecodeResult.Rejected(WaveRejectReason.TOO_LARGE), WaveCodec.decode(huge, now))
    }

    @Test
    fun invalidFieldsCannotBeEncoded() {
        val badSid = envelope.copy(sidHex = "xyz")
        try {
            WaveCodec.encodePayload(badSid)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
