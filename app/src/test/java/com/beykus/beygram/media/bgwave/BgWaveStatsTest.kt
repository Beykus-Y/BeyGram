package com.beykus.beygram.media.bgwave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BgWaveStatsTest {

    @Test fun parsesAllSevenFieldsInOrder() {
        assertEquals(
            BgWaveStats(sent = 500L, received = 490L, lost = 10L, plc = 4L, jitterMs = 12.0, bitrateBps = 32000L, depthMs = 60L),
            BgWaveStats.parse("500|490|10|4|12|32000|60"),
        )
    }

    @Test fun acceptsFloatingPointFields() {
        assertEquals(
            BgWaveStats(sent = 500L, received = 490L, lost = 10L, plc = 4L, jitterMs = 12.5, bitrateBps = 32000L, depthMs = 60L),
            BgWaveStats.parse("500|490|10|4|12.500000|32000|60.000000"),
        )
    }

    @Test fun emptyInputIsNull() {
        assertNull(BgWaveStats.parse(""))
    }

    @Test fun tooFewFieldsIsNull() {
        assertNull(BgWaveStats.parse("0|0|0|0|0|0"))
        assertNull(BgWaveStats.parse("1|2|3"))
    }

    @Test fun nonNumericFieldIsNull() {
        assertNull(BgWaveStats.parse("0|0|x|0|0|0|0"))
        assertNull(BgWaveStats.parse("0|0||0|0|0|0"))
        assertNull(BgWaveStats.parse("0|0|0|0|nan|0|0"))
    }
}
