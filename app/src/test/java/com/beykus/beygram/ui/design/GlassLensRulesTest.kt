package com.beykus.beygram.ui.design

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassLensRulesTest {
    @Test fun floatingPaneGetsTheLensWhenTheStyleSamplesTheBackdrop() {
        assertTrue(glassLensWanted(lensRefraction = true, edge = GlassEdge.Rim, samplesBackdrop = true, lensOnFreeEdge = false))
    }

    @Test fun noLensWithoutRefractionOrWithoutASampledBackdrop() {
        assertFalse(glassLensWanted(lensRefraction = false, edge = GlassEdge.Rim, samplesBackdrop = true, lensOnFreeEdge = false))
        assertFalse(glassLensWanted(lensRefraction = true, edge = GlassEdge.Rim, samplesBackdrop = false, lensOnFreeEdge = false))
    }

    @Test fun fullWidthChromeGetsTheLensOnlyWhenItOptsIn() {
        assertFalse(glassLensWanted(true, GlassEdge.BottomHairline, samplesBackdrop = true, lensOnFreeEdge = false))
        assertTrue(glassLensWanted(true, GlassEdge.BottomHairline, samplesBackdrop = true, lensOnFreeEdge = true))
    }

    @Test fun bottomComposerChromeNeverGetsTheLensThroughThisRule() {
        assertFalse(glassLensWanted(true, GlassEdge.TopHairline, samplesBackdrop = true, lensOnFreeEdge = true))
    }

    @Test fun rimLeavesEverySideClosed() {
        assertArrayEquals(booleanArrayOf(false, false, false, false), freeEdgeSides(GlassEdge.Rim))
    }

    @Test fun topChromeBendsOnlyAlongItsBottomEdge() {
        // Left, top, right are window edges; the bottom meets the content.
        assertArrayEquals(booleanArrayOf(true, true, true, false), freeEdgeSides(GlassEdge.BottomHairline))
    }

    @Test fun bottomChromeBendsOnlyAlongItsTopEdge() {
        assertArrayEquals(booleanArrayOf(true, false, true, true), freeEdgeSides(GlassEdge.TopHairline))
    }
}
