package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewOnceStealthGateTest {
    @Test fun refusedViewOnceAsksUntilTheUserConsents() {
        assertTrue(viewOnceNeedsConsent(isViewOnce = true, allowed = false, consented = false))
        assertFalse(viewOnceNeedsConsent(isViewOnce = true, allowed = false, consented = true))
    }

    @Test fun allowedOpeningOrOrdinaryMediaNeverAsks() {
        assertFalse(viewOnceNeedsConsent(isViewOnce = true, allowed = true, consented = false))
        assertFalse(viewOnceNeedsConsent(isViewOnce = false, allowed = false, consented = false))
    }
}
