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
        assertFalse(defaults.glassBypass)
    }

    @Test
    fun updateOnlyAppliesOnProfBuild() {
        ProfDiagnostics.update(ProfAbFlags(navInstant = true, bubbleNoIntrinsic = true, glassBypass = true))
        val expected = ProfDiagnostics.isProfBuild
        assertEquals(expected, ProfDiagnostics.navInstant)
        assertEquals(expected, ProfDiagnostics.bubbleNoIntrinsic)
        assertEquals(expected, ProfDiagnostics.glassBypass)
        ProfDiagnostics.update(ProfAbFlags())
        assertFalse(ProfDiagnostics.navInstant)
        assertFalse(ProfDiagnostics.bubbleNoIntrinsic)
        assertFalse(ProfDiagnostics.glassBypass)
    }
}
