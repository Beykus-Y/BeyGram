package com.beykus.beygram.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondaryBackgroundEligibilityTest {
    @Test fun onlyAuthorizedConfirmedEnabledAccountStartsInBackground() {
        assertTrue(secondaryBackgroundEligible(
            routeConfirmed = true, authorized = true, enabled = true, background = true
        ))
    }

    @Test fun neverStartForUnconfirmedOrManuallyStoppedRoute() {
        assertFalse(secondaryBackgroundEligible(
            routeConfirmed = false, authorized = true, enabled = true, background = true
        ))
    }

    @Test fun disablingNotificationsOrBackgroundBlocksAutoRestore() {
        assertFalse(secondaryBackgroundEligible(
            routeConfirmed = true, authorized = true, enabled = false, background = true
        ))
        assertFalse(secondaryBackgroundEligible(
            routeConfirmed = true, authorized = true, enabled = true, background = false
        ))
    }

    @Test fun priorAuthorizationIsRequired() {
        assertFalse(secondaryBackgroundEligible(
            routeConfirmed = true, authorized = false, enabled = true, background = true
        ))
    }
}
