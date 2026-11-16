package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StealthRiskGuardTest {
    @Test fun anUnconfirmedRiskyActionAsksWhileWarningsAreOn() {
        assertTrue(stealthRiskNeedsConfirmation(warns = true, confirmed = false))
    }

    @Test fun aConfirmedScreenRunsLaterRiskyActionsWithoutAsking() {
        assertFalse(stealthRiskNeedsConfirmation(warns = true, confirmed = true))
    }

    @Test fun riskyActionsRunAtOnceWhenWarningsAreOff() {
        assertFalse(stealthRiskNeedsConfirmation(warns = false, confirmed = false))
        assertFalse(stealthRiskNeedsConfirmation(warns = false, confirmed = true))
    }
}
