package com.beykus.beygram.media.bgwave

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgWaveStealthConsentTest {
    @Test fun noStealthNeedsNoConsent() {
        assertFalse(waveNeedsStealthConsent(holdsSends = false, warnsBeforeSending = false))
    }

    @Test fun delayedSendsOrWarningsNeedConsent() {
        assertTrue(waveNeedsStealthConsent(holdsSends = true, warnsBeforeSending = false))
        assertTrue(waveNeedsStealthConsent(holdsSends = false, warnsBeforeSending = true))
    }

    @Test fun signalWithoutConsentUnderStealthIsRefused() {
        assertFalse(waveSignalAllowed(needsConsent = true, consented = false))
        assertTrue(waveSignalAllowed(needsConsent = true, consented = true))
        assertTrue(waveSignalAllowed(needsConsent = false, consented = false))
    }
}
