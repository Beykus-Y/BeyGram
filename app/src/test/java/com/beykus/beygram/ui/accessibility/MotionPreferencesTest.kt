package com.beykus.beygram.ui.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPreferencesTest {
    @Test
    fun zeroAnimatorScaleReducesMotion() {
        assertTrue(isMotionReduced(animatorDurationScale = 0f, touchExplorationEnabled = false))
    }

    @Test
    fun activeScreenReaderReducesDecorativeMotion() {
        assertTrue(isMotionReduced(animatorDurationScale = 1f, touchExplorationEnabled = true))
    }

    @Test
    fun ordinaryMotionStaysEnabled() {
        assertFalse(isMotionReduced(animatorDurationScale = 1f, touchExplorationEnabled = false))
        assertFalse(isMotionReduced(animatorDurationScale = 0.5f, touchExplorationEnabled = false))
    }
}
