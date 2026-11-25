package com.beykus.beygram.ui.design

import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.beykus.beygram.data.appearance.LiquidGlassLensPreset
import org.junit.Test

class LiquidGlassLensTest {
    @Test
    fun lensIsOffByDefault() {
        assertFalse(designFor(DesignStyle.LiquidGlass, GlassQuality.High).surfaces.glass!!.lensRefraction)
    }

    @Test
    fun lensFollowsTheExperimentalSwitchOnlyForLiquidGlass() {
        assertTrue(designFor(DesignStyle.LiquidGlass, GlassQuality.High, liquidGlassV2 = true).surfaces.glass!!.lensRefraction)
        assertNull(designFor(DesignStyle.Default, liquidGlassV2 = true).surfaces.glass)
    }

    @Test
    fun lensChangesOnlyTheRefractionFlag() {
        val plain = designFor(DesignStyle.LiquidGlass, GlassQuality.High).surfaces.glass!!
        val lens = designFor(DesignStyle.LiquidGlass, GlassQuality.High, liquidGlassV2 = true).surfaces.glass!!
        assertEquals(plain, lens.copy(lensRefraction = false))
    }

    @Test
    fun everyLensPresetMapsToItsOwnTuning() {
        assertEquals(LensTuningPresets.Default, LensTuningPresets.forPreset(LiquidGlassLensPreset.Standard))
        assertEquals(LensTuningPresets.Clear, LensTuningPresets.forPreset(LiquidGlassLensPreset.Clear))
        assertEquals(LensTuningPresets.Bold, LensTuningPresets.forPreset(LiquidGlassLensPreset.Bold))
        assertTrue(LensTuningPresets.Clear.blurScale < LensTuningPresets.Default.blurScale)
        assertTrue(LensTuningPresets.Bold.strength > LensTuningPresets.Default.strength)
    }
}
