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
}
