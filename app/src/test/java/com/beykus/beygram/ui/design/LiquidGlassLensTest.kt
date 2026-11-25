package com.beykus.beygram.ui.design

import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
}
