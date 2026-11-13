package com.beykus.beygram.media.bgwave

import org.junit.Assert.assertEquals
import org.junit.Test

class BgWaveNativeParseTest {

    @Test fun parsesOkSummary() {
        val parsed = parseDecodeResult("ok|1|0123456789abcdef0123456789abcdef|1800000000|1800000300|3|42|2")
        assertEquals(
            BgWaveDecodeResult.Ok(1, "0123456789abcdef0123456789abcdef", 1800000000L, 1800000300L, 3L, 42L, 2),
            parsed,
        )
    }

    @Test fun errorKeepsReasonIncludingPipes() {
        assertEquals(BgWaveDecodeResult.Rejected("bad|field"), parseDecodeResult("err|bad|field"))
    }

    @Test fun malformedAndNullAreRejectedWithoutThrowing() {
        assertEquals(BgWaveDecodeResult.Rejected("null"), parseDecodeResult(null))
        assertEquals(BgWaveDecodeResult.Rejected("malformed"), parseDecodeResult("ok|1|x"))
        assertEquals(BgWaveDecodeResult.Rejected("malformed"), parseDecodeResult("ok|a|s|1|2|3|4|5"))
    }

    // Real output of the BG-Media C++ codec on tests/fixtures/wave_v1 (cancel: caps 0, nonce 3).
    @Test fun parsesRealCancelSummaryFromBgMediaFixture() {
        assertEquals(
            BgWaveDecodeResult.Ok(3, "102132435465768798a9bacbdcedfe0f", 1763000010L, 1763000070L, 0L, 3L, 0),
            parseDecodeResult("ok|3|102132435465768798a9bacbdcedfe0f|1763000010|1763000070|0|3|0"),
        )
    }

    @Test fun parsesRealOfferSummaryFromBgMediaFixture() {
        assertEquals(
            BgWaveDecodeResult.Ok(1, "102132435465768798a9bacbdcedfe0f", 1763000000L, 1763000120L, 7L, 1L, 3),
            parseDecodeResult("ok|1|102132435465768798a9bacbdcedfe0f|1763000000|1763000120|7|1|3"),
        )
    }

    // Copied byte-for-byte from BG-Media tests/fixtures/wave_v1/cancel.uri.txt.
    @Test fun cancelFixtureUrisAreTheBgMediaBytes() {
        assertEquals("bg://wave/v1/QlcBAxAhMkNUZXaHmKm6y9zt_g8AAAAAaRU-ygAAAABpFT8GAAAAAAAAAAAAAAADAAAHc2hhLTI1NgAAAAA", BgWaveNativeFixtures.CANCEL_BG_URI)
        assertEquals("https://beygram.beykus.fun/#bgwave-v1.QlcBAxAhMkNUZXaHmKm6y9zt_g8AAAAAaRU-ygAAAABpFT8GAAAAAAAAAAAAAAADAAAHc2hhLTI1NgAAAAA", BgWaveNativeFixtures.CANCEL_HTTPS_URI)
    }
}
