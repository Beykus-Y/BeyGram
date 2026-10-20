package com.beykus.beygram.data.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticActivationGuardTest {
    @Test fun existingProfileStartsOnlyFromExplicitAutoChoice() {
        val guard = AutomaticActivationGuard()
        val generation = guard.selectAuto(true, true)
        assertNotNull(generation)
        assertTrue(guard.permitsActivation(generation!!, true, true))
        assertNull(guard.afterImport(true, true))
    }

    @Test fun pendingInitialImportIsConsumedOnlyOnce() {
        val guard = AutomaticActivationGuard()
        assertNull(guard.selectAuto(true, false))
        assertNull(guard.afterImport(true, false))
        val generation = guard.afterImport(true, true)
        assertNotNull(generation)
        assertTrue(guard.permitsActivation(generation!!, true, true))
        assertNull(guard.afterImport(true, true))
    }

    @Test fun manualChoiceCancelsPendingImportAndQueuedActivation() {
        val guard = AutomaticActivationGuard()
        val queued = guard.selectAuto(true, true)!!
        guard.selectManualRoute()
        assertFalse(guard.permitsActivation(queued, true, true))

        guard.selectAuto(true, false)
        guard.selectManualRoute()
        assertNull(guard.afterImport(true, true))
    }

    @Test fun disablingAutoInvalidatesPreviousSelection() {
        val guard = AutomaticActivationGuard()
        val queued = guard.selectAuto(true, true)!!
        guard.selectAuto(false, true)
        assertFalse(guard.permitsActivation(queued, false, true))
        assertNull(guard.afterImport(false, true))
    }

    @Test fun refreshedSubscriptionCannotHijackManuallySelectedProxy() {
        val guard = AutomaticActivationGuard()
        guard.selectAuto(true, true)
        guard.selectManualRoute()
        assertNull(guard.afterImport(true, true))
        assertNull(guard.afterImport(false, true))
    }
}
