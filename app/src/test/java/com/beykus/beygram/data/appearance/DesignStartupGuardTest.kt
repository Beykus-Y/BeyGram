package com.beykus.beygram.data.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crash-loop guard is the only thing standing between a broken style and an app that cannot be opened, so the
 * decision itself is pinned here.
 */
class DesignStartupGuardTest {
    @Test fun aStartThatFollowsAFailedOneGivesUpTheSavedStyle() {
        assertEquals(1, MAX_UNFINISHED_STARTS)
        assertTrue(shouldFallBackToDefault(unfinishedStarts = 1, style = DesignStyle.LiquidGlass))
        assertTrue(shouldFallBackToDefault(unfinishedStarts = 1, style = DesignStyle.Material3))
        assertTrue(shouldFallBackToDefault(unfinishedStarts = 5, style = DesignStyle.Signature))
    }

    @Test fun aHealthyStartChangesNothing() {
        DesignStyle.entries.forEach { style ->
            assertFalse(shouldFallBackToDefault(unfinishedStarts = 0, style = style))
        }
    }

    @Test fun defaultIsNeverTheReasonToFallBackAgain() {
        assertFalse(shouldFallBackToDefault(unfinishedStarts = 1, style = DesignStyle.Default))
        assertFalse(shouldFallBackToDefault(unfinishedStarts = 99, style = DesignStyle.Default))
    }
}
