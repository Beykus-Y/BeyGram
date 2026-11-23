package com.beykus.beygram.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfDiagnosticsTest {

    @Test
    fun defaultsAreAllDisabled() {
        val defaults = ProfAbFlags()
        assertFalse(defaults.navInstant)
        assertFalse(defaults.bubbleNoIntrinsic)
        assertFalse(defaults.glassChromeOnly)
        assertFalse(defaults.underChromeBypass)
        assertFalse(defaults.glassBypass)
        assertFalse(defaults.keepTabsMounted)
    }

    @Test
    fun updateOnlyAppliesOnProfBuild() {
        ProfDiagnostics.update(
            ProfAbFlags(
                navInstant = true,
                bubbleNoIntrinsic = true,
                glassChromeOnly = true,
                underChromeBypass = false,
                keepTabsMounted = true
            )
        )
        val expected = ProfDiagnostics.isProfBuild
        assertEquals(expected, ProfDiagnostics.navInstant)
        assertEquals(expected, ProfDiagnostics.bubbleNoIntrinsic)
        assertEquals(expected, ProfDiagnostics.glassChromeOnly)
        assertFalse(ProfDiagnostics.underChromeBypass)
        assertFalse(ProfDiagnostics.glassBypass)
        assertEquals(expected, ProfDiagnostics.keepTabsMounted)

        ProfDiagnostics.update(ProfAbFlags())
        assertFalse(ProfDiagnostics.navInstant)
        assertFalse(ProfDiagnostics.bubbleNoIntrinsic)
        assertFalse(ProfDiagnostics.glassChromeOnly)
        assertFalse(ProfDiagnostics.underChromeBypass)
        assertFalse(ProfDiagnostics.keepTabsMounted)
    }
}
