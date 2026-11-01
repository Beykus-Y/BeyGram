package com.beykus.beygram.ui.design

import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemTest {
    @Test fun defaultKeepsTheShapeScaleAndBubblesTheAppAlwaysHad() {
        val design = designFor(DesignStyle.Default)
        assertEquals(4.dp, design.shapes.extraSmall)
        assertEquals(8.dp, design.shapes.small)
        assertEquals(12.dp, design.shapes.medium)
        assertEquals(16.dp, design.shapes.large)
        assertEquals(28.dp, design.shapes.extraLarge)
        // Null means the existing bubbleMetricsFor() keeps full ownership of the Default geometry.
        assertNull(design.bubbles)
        assertEquals(1f, design.surfaces.containerTonalStep)
        assertEquals(NavigationVariant.PillTabs, design.components.navigation)
        assertFalse(design.components.floatingNavigation)
    }

    @Test fun allFiveStylesResolveDeterministically() {
        DesignStyle.entries.forEach { style ->
            val first = designFor(style, GlassQuality.Auto)
            val second = designFor(style, GlassQuality.Auto)
            assertEquals(style, first.style)
            assertEquals(first, second)
        }
        // Only Liquid Glass carries the glass material.
        assertNull(designFor(DesignStyle.Default).surfaces.glass)
        assertNull(designFor(DesignStyle.Material2).surfaces.glass)
        assertNull(designFor(DesignStyle.Material3).surfaces.glass)
        assertNull(designFor(DesignStyle.Signature).surfaces.glass)
        assertNotNull(designFor(DesignStyle.LiquidGlass, GlassQuality.High).surfaces.glass)
    }

    @Test fun theStylesAreVisuallyDistinctBeyondColour() {
        val designs = DesignStyle.entries.map { designFor(it, GlassQuality.High).copy(style = DesignStyle.Default) }
        assertEquals(designs.size, designs.distinct().size)

        // Different navigation structures are the visible proof that a style is not just recolouring.
        assertEquals(NavigationVariant.PillTabs, designFor(DesignStyle.Default).components.navigation)
        assertEquals(NavigationVariant.ClassicTabs, designFor(DesignStyle.Material2).components.navigation)
        assertEquals(NavigationVariant.NavigationBar, designFor(DesignStyle.Material3).components.navigation)
        assertEquals(NavigationVariant.FloatingGlass, designFor(DesignStyle.LiquidGlass).components.navigation)
        assertEquals(NavigationVariant.SignatureDock, designFor(DesignStyle.Signature).components.navigation)
    }

    @Test fun everyStyleExceptDefaultOwnsItsBubbleGeometry() {
        DesignStyle.entries.filter { it != DesignStyle.Default }.forEach { style ->
            assertNotNull("$style must define its bubble geometry", designFor(style).bubbles)
        }
        assertNotEquals(designFor(DesignStyle.Material2).bubbles, designFor(DesignStyle.LiquidGlass).bubbles)
    }

    @Test fun glassQualityNeverClaimsBlurWhereItDoesNotBlur() {
        assertTrue(designFor(DesignStyle.LiquidGlass, GlassQuality.High).usesGlass)
        assertFalse(designFor(DesignStyle.LiquidGlass, GlassQuality.Balanced).usesGlass)
        assertFalse(designFor(DesignStyle.LiquidGlass, GlassQuality.Lite).usesGlass)
        // The matte level still renders a recognisable surface instead of becoming transparent.
        assertEquals(1f, designFor(DesignStyle.LiquidGlass, GlassQuality.Lite).surfaces.surfaceAlpha)
    }

    @Test fun anExplicitQualityChoiceIsKeptAndAutoUsesTheDetectedLevel() {
        assertEquals(GlassQuality.Lite, resolveGlassQuality(GlassQuality.Lite))
        assertEquals(GlassQuality.Balanced, resolveGlassQuality(GlassQuality.Balanced, autoLevel = GlassQuality.High))
        assertEquals(GlassQuality.High, resolveGlassQuality(GlassQuality.Auto, autoLevel = GlassQuality.High))
        assertEquals(GlassQuality.Lite, resolveGlassQuality(GlassQuality.Auto, autoLevel = GlassQuality.Auto))
    }

    @Test fun automaticQualityStepsDownWhereTheFullEffectWouldBeUnsafe() {
        assertEquals(
            GlassQuality.High,
            autoGlassQuality(GlassCapabilities(true, reducedTransparency = false, constrainedPerformance = false))
        )
        // Android 11 and older has no render effect for the sampled backdrop.
        assertEquals(
            GlassQuality.Balanced,
            autoGlassQuality(GlassCapabilities(false, reducedTransparency = false, constrainedPerformance = false))
        )
        assertEquals(
            GlassQuality.Lite,
            autoGlassQuality(GlassCapabilities(true, reducedTransparency = true, constrainedPerformance = false))
        )
        assertEquals(
            GlassQuality.Balanced,
            autoGlassQuality(GlassCapabilities(true, reducedTransparency = false, constrainedPerformance = true))
        )
    }
}
