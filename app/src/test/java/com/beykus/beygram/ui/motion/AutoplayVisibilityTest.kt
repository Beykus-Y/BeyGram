package com.beykus.beygram.ui.motion

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayVisibilityTest {
    private val viewport = Rect(0f, 0f, 1000f, 2000f)

    @Test
    fun itemInsideViewportIsFullyVisible() {
        assertEquals(1f, AutoplayVisibility.visibleFraction(Rect(100f, 100f, 300f, 300f), viewport), 0f)
    }

    @Test
    fun itemOutsideViewportOrTouchingItsEdgeIsNotVisible() {
        assertEquals(0f, AutoplayVisibility.visibleFraction(Rect(0f, 2000f, 200f, 2200f), viewport), 0f)
        assertEquals(0f, AutoplayVisibility.visibleFraction(Rect(0f, -300f, 200f, -100f), viewport), 0f)
    }

    @Test
    fun partialOverlapCountsTheSharedArea() {
        // A 200x200 item with its top 100 px above the viewport has half of its area visible.
        assertEquals(0.5f, AutoplayVisibility.visibleFraction(Rect(0f, -100f, 200f, 100f), viewport), 0f)
        // Overlap on both axes: a quarter of the item is inside the corner of the viewport.
        assertEquals(0.25f, AutoplayVisibility.visibleFraction(Rect(-100f, -100f, 100f, 100f), viewport), 0f)
    }

    @Test
    fun emptyItemIsNeverVisible() {
        assertEquals(0f, AutoplayVisibility.visibleFraction(Rect(10f, 10f, 10f, 50f), viewport), 0f)
    }

    @Test
    fun halfOfTheAreaIsTheThreshold() {
        assertTrue(AutoplayVisibility.meetsVisibleThreshold(0.5f))
        assertFalse(AutoplayVisibility.meetsVisibleThreshold(0.49f))
        assertFalse(AutoplayVisibility.meetsVisibleThreshold(0f))
    }

    @Test
    fun motionSettingsDecideWhetherAutoplayIsAllowed() {
        assertTrue(AutoplayVisibility.motionAllows(MotionSettings(), reducedMotion = false))
        assertFalse(AutoplayVisibility.motionAllows(MotionSettings().withPreset(MotionPreset.Off), reducedMotion = false))
        assertFalse(AutoplayVisibility.motionAllows(MotionSettings(), reducedMotion = true))
        val mediaDisabled = MotionSettings().withCategoryEnabled(MotionCategory.Media, enabled = false)
        assertFalse(AutoplayVisibility.motionAllows(mediaDisabled, reducedMotion = false))
        // Another category does not stop media autoplay.
        val tabsDisabled = MotionSettings().withCategoryEnabled(MotionCategory.Tabs, enabled = false)
        assertTrue(AutoplayVisibility.motionAllows(tabsDisabled, reducedMotion = false))
    }

    @Test
    fun everyConditionIsRequiredBeforeTheDwellStarts() {
        assertTrue(AutoplayVisibility.canAutoplay(motionAllowed = true, lifecycleStarted = true, visibleEnough = true))
        assertFalse(AutoplayVisibility.canAutoplay(motionAllowed = false, lifecycleStarted = true, visibleEnough = true))
        assertFalse(AutoplayVisibility.canAutoplay(motionAllowed = true, lifecycleStarted = false, visibleEnough = true))
        assertFalse(AutoplayVisibility.canAutoplay(motionAllowed = true, lifecycleStarted = true, visibleEnough = false))
    }

    @Test
    fun dwellNeedsTheFullHalfSecondAndStartsOverAfterAGap() = runTest {
        var eligible = false
        var wait = launch {
            awaitAutoplayDwell()
            eligible = true
        }
        advanceTimeBy(300)
        assertFalse(eligible)

        // Visibility drops at 300 ms: the wait is cancelled, as the gate does when the element leaves the window.
        wait.cancel()
        wait = launch {
            awaitAutoplayDwell()
            eligible = true
        }
        advanceTimeBy(499)
        assertFalse(eligible)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(eligible)
        wait.cancel()
    }
}
