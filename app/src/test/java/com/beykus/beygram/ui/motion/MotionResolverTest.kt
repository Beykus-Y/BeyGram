package com.beykus.beygram.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionResolverTest {
    @Test
    fun balancedDefaultUsesBaseDurationAndFullIntensity() {
        val spec = resolveMotion(MotionToken.NavigationPush, MotionSettings(), reduced = false)
        assertEquals(MotionToken.NavigationPush.baseDurationMillis, spec.durationMillis)
        assertEquals(1f, spec.intensity, 0f)
        assertFalse(spec.isInstant)
    }

    @Test
    fun speedDividesDurationAndIsClamped() {
        val fast = resolveMotion(MotionToken.TabSelect, MotionSettings().withSpeed(2f), reduced = false)
        assertEquals(100, fast.durationMillis)

        val tooFast = resolveMotion(MotionToken.TabSelect, MotionSettings().withSpeed(10f), reduced = false)
        assertEquals(100, tooFast.durationMillis)

        val tooSlow = resolveMotion(MotionToken.TabSelect, MotionSettings().withSpeed(0.01f), reduced = false)
        assertEquals(400, tooSlow.durationMillis)
    }

    @Test
    fun reducedMotionIsAlwaysInstant() {
        val spec = resolveMotion(MotionToken.SheetEnter, MotionSettings(), reduced = true)
        assertTrue(spec.isInstant)
        assertEquals(0f, spec.intensity, 0f)
    }

    @Test
    fun masterSwitchOffIsInstant() {
        val settings = MotionSettings().withPreset(MotionPreset.Off)
        assertFalse(settings.enabled)
        assertTrue(resolveMotion(MotionToken.MicroFeedback, settings, reduced = false).isInstant)
    }

    @Test
    fun disabledCategoryOnlyAffectsItsTokens() {
        val settings = MotionSettings().withCategoryEnabled(MotionCategory.Tabs, enabled = false)
        assertTrue(resolveMotion(MotionToken.TabSelect, settings, reduced = false).isInstant)
        assertFalse(resolveMotion(MotionToken.NavigationPush, settings, reduced = false).isInstant)
    }

    @Test
    fun appearanceNavigationTokensRespectOffAndReducedMotion() {
        val off = MotionSettings().withPreset(MotionPreset.Off)
        val navigationDisabled = MotionSettings().withCategoryEnabled(MotionCategory.Navigation, enabled = false)
        for (token in listOf(MotionToken.NavigationPush, MotionToken.NavigationPop)) {
            assertTrue(resolveMotion(token, off, reduced = false).isInstant)
            assertTrue(resolveMotion(token, navigationDisabled, reduced = false).isInstant)
            assertTrue(resolveMotion(token, MotionSettings(), reduced = true).isInstant)
            assertFalse(resolveMotion(token, MotionSettings(), reduced = false).isInstant)
        }
    }

    @Test
    fun presetCopiesSpeedAndIntensityAndCustomKeepsThem() {
        val expressive = MotionSettings().withPreset(MotionPreset.Expressive)
        assertEquals(0.75f, expressive.speed, 0f)
        assertEquals(150, expressive.intensityPercent)

        val custom = expressive.withPreset(MotionPreset.Custom)
        assertEquals(expressive.speed, custom.speed, 0f)
        assertEquals(expressive.intensityPercent, custom.intensityPercent)
    }

    @Test
    fun manualSliderChangeSwitchesToCustom() {
        val settings = MotionSettings().withPreset(MotionPreset.Subtle).withIntensity(20)
        assertEquals(MotionPreset.Custom, settings.preset)
        assertEquals(20, settings.intensityPercent)
    }

    @Test
    fun zeroIntensityDoesNotMakeTheSpecInstant() {
        val spec = resolveMotion(MotionToken.MediaExpand, MotionSettings().withIntensity(0), reduced = false)
        assertFalse(spec.isInstant)
        assertEquals(0f, spec.intensity, 0f)
    }
}
