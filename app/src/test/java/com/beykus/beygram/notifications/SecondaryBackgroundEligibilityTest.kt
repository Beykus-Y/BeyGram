package com.beykus.beygram.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondaryBackgroundEligibilityTest {
    @Test fun secondaryOnlyColdStartRequiresIndependentlyReachableRoute() {
        val follow = com.beykus.beygram.data.tdlib.TdSecondaryRoutePreference.FOLLOW_PRIMARY
        val shared = com.beykus.beygram.data.tdlib.TdSecondaryRoutePreference.SHARED_MIHOMO
        val direct = com.beykus.beygram.data.tdlib.TdSecondaryRoutePreference.EXPLICIT_DIRECT
        assertFalse(secondaryColdStartRouteEligible(follow, legacyCanStart = false))
        assertFalse(secondaryColdStartRouteEligible(shared, legacyCanStart = false))
        assertTrue(secondaryColdStartRouteEligible(direct, legacyCanStart = false))
        assertTrue(secondaryColdStartRouteEligible(follow, legacyCanStart = true))
        assertFalse(secondaryColdStartRouteEligible(shared, legacyCanStart = true))
        assertTrue(secondaryColdStartRouteEligible(shared, legacyCanStart = true,
            sharedListenerActive = true))
        assertTrue(secondaryColdStartRouteEligible(shared, legacyCanStart = false,
            sharedListenerActive = true))
        assertFalse(secondaryColdStartRouteEligible(null, legacyCanStart = true))
    }

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
