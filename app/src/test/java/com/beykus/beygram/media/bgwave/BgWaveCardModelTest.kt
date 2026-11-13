package com.beykus.beygram.media.bgwave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BgWaveCardModelTest {
    private val sid = "102132435465768798a9bacbdcedfe0f"

    @Test fun offerBecomesInviteCard() {
        val state = bgWaveCardStateFor(BgWaveDecodeResult.Ok(1, sid, 1763000000L, 1763000120L, 7L, 1L, 3))
        assertEquals(BgWaveCardState.Invite(sid, 1763000120L, 3), state)
    }

    @Test fun answerCancelEndMapToSignals() {
        assertEquals(BgWaveCardState.Signal(BgWaveSignalKind.ANSWER, sid),
            bgWaveCardStateFor(BgWaveDecodeResult.Ok(2, sid, 0L, 1L, 0L, 0L, 1)))
        assertEquals(BgWaveCardState.Signal(BgWaveSignalKind.CANCEL, sid),
            bgWaveCardStateFor(BgWaveDecodeResult.Ok(3, sid, 0L, 1L, 0L, 0L, 0)))
        assertEquals(BgWaveCardState.Signal(BgWaveSignalKind.END, sid),
            bgWaveCardStateFor(BgWaveDecodeResult.Ok(4, sid, 0L, 1L, 0L, 0L, 0)))
    }

    @Test fun unknownTypeIsSafeText() {
        assertEquals(BgWaveCardState.SafeText(BgWaveCardKeys.INVALID),
            bgWaveCardStateFor(BgWaveDecodeResult.Ok(9, sid, 0L, 1L, 0L, 0L, 0)))
    }

    @Test fun rejectionsBecomeSafeTextWithOwnKeys() {
        assertEquals(BgWaveCardState.SafeText(BgWaveCardKeys.UNSUPPORTED_VERSION),
            bgWaveCardStateFor(BgWaveDecodeResult.Rejected("UNSUPPORTED_VERSION")))
        assertEquals(BgWaveCardState.SafeText(BgWaveCardKeys.EXPIRED),
            bgWaveCardStateFor(BgWaveDecodeResult.Rejected("EXPIRED")))
        assertEquals(BgWaveCardState.SafeText(BgWaveCardKeys.NOT_YET_VALID),
            bgWaveCardStateFor(BgWaveDecodeResult.Rejected("NOT_YET_VALID")))
        assertEquals(BgWaveCardState.SafeText(BgWaveCardKeys.UNAVAILABLE),
            bgWaveCardStateFor(BgWaveDecodeResult.Rejected("unavailable")))
        assertEquals(BgWaveCardState.SafeText(BgWaveCardKeys.INVALID),
            bgWaveCardStateFor(BgWaveDecodeResult.Rejected("MALFORMED")))
    }

    @Test fun ordinaryTextIsNotTouched() {
        assertNull(bgWaveCardStateFor(BgWaveDecodeResult.Rejected("NOT_WAVE")))
    }
}
