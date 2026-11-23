package com.beykus.beygram.perf

import com.beykus.beygram.ui.screens.chat.bubbleBarNeedsIntrinsicMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    @Test
    fun bubbleIntrinsicDiagnosticPreservesNonEmptyBottomBars() {
        // Baseline must always use the existing intrinsic measurement.
        assertTrue(bubbleBarNeedsIntrinsicMeasurement(bottomBarPresent = false, diagnosticSkip = false))
        assertTrue(bubbleBarNeedsIntrinsicMeasurement(bottomBarPresent = true, diagnosticSkip = false))

        // H2 bypasses the intrinsic query only for an actually absent comment bar.
        assertFalse(bubbleBarNeedsIntrinsicMeasurement(bottomBarPresent = false, diagnosticSkip = true))
        assertTrue(bubbleBarNeedsIntrinsicMeasurement(bottomBarPresent = true, diagnosticSkip = true))
    }

}
