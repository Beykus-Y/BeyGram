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

    private fun session(role: BgWaveRole, phase: BgWavePhase, route: String = "None", unsupported: Boolean = false) =
        BgWaveSessionState(role, "a", 7L, 7L, sid, phase, route = route, sourceUnsupported = unsupported)

    @Test fun liveHostCardShowsListenerRouteAndRole() {
        val view = bgWaveCardView(sid, 0L, true, null, session(BgWaveRole.Host, BgWavePhase.Live, "Direct"), 1L)
        assertEquals(BgWaveRole.Host, view.role)
        assertEquals(1, view.listeners)
        assertEquals(true, view.directRoute)
        assertEquals(false, view.sourceUnsupported)
    }

    @Test fun waitingHostCardHasNoListenerYet() {
        val view = bgWaveCardView(sid, 0L, true, null, session(BgWaveRole.Host, BgWavePhase.AwaitingAnswer), 1L)
        assertEquals(0, view.listeners)
        assertEquals(false, view.directRoute)
    }

    @Test fun listenerCardHasNoListenerCount() {
        val view = bgWaveCardView(sid, 0L, false, null, session(BgWaveRole.Listener, BgWavePhase.Live, "Direct"), 1L)
        assertEquals(BgWaveRole.Listener, view.role)
        assertNull(view.listeners)
    }

    @Test fun unsupportedSourceIsShownToTheLiveHostOnly() {
        assertEquals(true, bgWaveCardView(sid, 0L, true, null, session(BgWaveRole.Host, BgWavePhase.Live, unsupported = true), 1L).sourceUnsupported)
        assertEquals(false, bgWaveCardView(sid, 0L, false, null, session(BgWaveRole.Listener, BgWavePhase.Live, unsupported = true), 1L).sourceUnsupported)
        assertEquals(false, bgWaveCardView(sid, 0L, true, null, session(BgWaveRole.Host, BgWavePhase.Ended, unsupported = true), 1L).sourceUnsupported)
    }

    @Test fun failedHostKeepsItsErrorKey() {
        val failed = BgWaveSessionState(BgWaveRole.Host, "a", 7L, 7L, "", BgWavePhase.Failed, errorKey = BgWaveErrors.OFFER_TOO_LARGE)
        val view = bgWaveCardView("", 0L, true, null, failed, 1L)
        assertEquals(BgWaveErrors.OFFER_TOO_LARGE, view.errorKey)
    }
}
