package com.beykus.beygram.ui.design

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        assertEquals(NavigationVariant.SignatureMark, designFor(DesignStyle.Signature).components.navigation)
    }

    @Test fun defaultChatListKeepsTheNumbersItAlwaysHad() {
        val main = designFor(DesignStyle.Default).mainScreen
        assertEquals(30.sp, main.titleSize)
        assertEquals(FontWeight.Bold, main.titleWeight)
        assertEquals((-0.3).sp, main.titleTracking)
        assertEquals(16.dp, main.chipRadius)
        assertEquals(ChipSelectionTone.Filled, main.chipSelection)
        assertEquals(ArchiveTreatment.Tonal, main.archive)
        assertEquals(14.dp, main.archiveRadius)
        assertEquals(54.dp, main.avatarSize)
        assertEquals(FontWeight.SemiBold, main.rowTitleWeight)
        assertEquals(11.dp, main.badgeRadius)
        assertEquals(14.dp, main.headerGap)
        assertFalse("Default keeps the sentence case of the pinned label", main.sectionLabelUppercase)
    }

    @Test fun theChatListDiffersSystemicallyBetweenStyles() {
        val byStyle = DesignStyle.entries.associateWith { designFor(it).mainScreen }

        // The screen itself changes, not only the bar below it.
        assertEquals(byStyle.size, byStyle.values.distinct().size)

        // A rectangular search field must not sit next to rounded pills: Material 2 squares its chips and its title.
        assertEquals(2.dp, byStyle.getValue(DesignStyle.Material2).chipRadius)
        assertEquals(ChipSelectionTone.Underlined, byStyle.getValue(DesignStyle.Material2).chipSelection)
        assertTrue(byStyle.getValue(DesignStyle.Material2).sectionLabelUppercase)
        assertEquals(ArchiveTreatment.Card, byStyle.getValue(DesignStyle.Material2).archive)

        // Material 3 uses its outlined filter chip instead of the accent fill; Glass keeps its tonal chip.
        assertEquals(ChipSelectionTone.Filter, byStyle.getValue(DesignStyle.Material3).chipSelection)
        assertEquals(ChipSelectionTone.Tonal, byStyle.getValue(DesignStyle.LiquidGlass).chipSelection)
        assertEquals(8.dp, byStyle.getValue(DesignStyle.Material3).badgeRadius)

        // Signature drops the archive card entirely and keeps the largest hierarchy.
        assertEquals(ArchiveTreatment.Flat, byStyle.getValue(DesignStyle.Signature).archive)
        assertTrue(
            byStyle.getValue(DesignStyle.Signature).titleSize > byStyle.getValue(DesignStyle.Default).titleSize
        )
        assertEquals(FontWeight.Bold, byStyle.getValue(DesignStyle.Signature).rowTitleWeight)

        // Glass keeps a card, but with its own softer radius.
        assertEquals(ArchiveTreatment.Card, byStyle.getValue(DesignStyle.LiquidGlass).archive)
        assertEquals(20.dp, byStyle.getValue(DesignStyle.LiquidGlass).archiveRadius)
    }

    @Test fun material3MainScreenUsesOneTitleTheSearchBarAndATonalFab() {
        val m3 = designFor(DesignStyle.Material3)
        assertFalse("Material 3 drops the brand label above the title", m3.mainScreen.showBrandOverline)
        assertEquals(56.dp, m3.mainScreen.searchHeight)
        assertEquals(FontWeight.Normal, m3.mainScreen.titleWeight)
        assertTrue(m3.fab.tonal)
        assertEquals(16.dp, m3.fab.cornerRadius)
    }

    @Test fun otherStylesKeepTheirHeaderSearchAndFab() {
        DesignStyle.entries.filter { it != DesignStyle.Material3 }.forEach { style ->
            val design = designFor(style)
            assertTrue("$style keeps the brand label", design.mainScreen.showBrandOverline)
            assertEquals("$style search height", 48.dp, design.mainScreen.searchHeight)
            assertFalse("$style keeps the accent FAB", design.fab.tonal)
        }
    }

    @Test fun settingsCardsFollowTheStyleWithoutMovingDefault() {
        assertEquals(20.dp, designFor(DesignStyle.Default).shapes.groupCard)
        assertEquals(18.dp, designFor(DesignStyle.Default).shapes.listGroup)
        // Material 2 collapses the same cards into its 4dp surface instead of keeping modern rounded panels.
        assertEquals(4.dp, designFor(DesignStyle.Material2).shapes.groupCard)
        assertEquals(4.dp, designFor(DesignStyle.Material2).shapes.listGroup)
        assertEquals(12.dp, designFor(DesignStyle.Material3).shapes.groupCard)
        assertTrue(designFor(DesignStyle.Signature).shapes.groupCard < designFor(DesignStyle.LiquidGlass).shapes.groupCard)
    }

    @Test fun onlyMaterial2CapsTheCornersOfUnnamedSurfaces() {
        // Default and the modern styles keep every radius a screen asks for, so their panels do not move.
        listOf(DesignStyle.Default, DesignStyle.Material3, DesignStyle.LiquidGlass, DesignStyle.Signature).forEach { style ->
            val shapes = designFor(style).shapes
            assertNull("$style must not cap corners", shapes.cornerCap)
            assertEquals(20.dp, shapes.corner(20.dp))
            assertEquals(6.dp, shapes.corner(6.dp))
        }
        val classic = designFor(DesignStyle.Material2).shapes
        // A 20dp panel becomes the classic 4dp surface, small radii stay as small as they were, nothing turns square.
        assertEquals(4.dp, classic.corner(20.dp))
        assertEquals(4.dp, classic.corner(4.dp))
        assertEquals(2.dp, classic.corner(2.dp))
        assertEquals(0.dp, classic.corner(0.dp))
        // Dialogs and modal sheets read Material's extraLarge corner, which Material 2 keeps at its 4dp dialog radius.
        assertEquals(classic.dialog, classic.extraLarge)
        assertEquals(4.dp, classic.button)
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

    @Test fun explicitHighNeverPromisesBlurOnADeviceThatCannotDrawIt() {
        val noBlur = GlassCapabilities(false, reducedTransparency = true, constrainedPerformance = false)
        val capable = GlassCapabilities(true, reducedTransparency = false, constrainedPerformance = false)
        assertEquals(GlassQuality.Balanced, effectiveGlassQuality(GlassQuality.High, noBlur))
        assertFalse(designFor(DesignStyle.LiquidGlass, effectiveGlassQuality(GlassQuality.High, noBlur)).usesGlass)
        // The saved choice is untouched, so a capable device draws the real blur for the same setting.
        assertEquals(GlassQuality.High, effectiveGlassQuality(GlassQuality.High, capable))
        // Lower explicit choices and Auto are never raised.
        assertEquals(GlassQuality.Lite, effectiveGlassQuality(GlassQuality.Lite, capable))
        assertEquals(GlassQuality.Balanced, effectiveGlassQuality(GlassQuality.Balanced, capable))
        assertEquals(GlassQuality.Balanced, effectiveGlassQuality(GlassQuality.Auto, noBlur.copy(reducedTransparency = false)))
    }

    @Test fun glassDepthStaysQuieterThanAPlasticTray() {
        // One lens-like lift, not a heavy floating tray: every level keeps a small shadow and real glass a lighter tint.
        val high = designFor(DesignStyle.LiquidGlass, GlassQuality.High).surfaces.glass!!
        val balanced = designFor(DesignStyle.LiquidGlass, GlassQuality.Balanced).surfaces.glass!!
        val lite = designFor(DesignStyle.LiquidGlass, GlassQuality.Lite).surfaces.glass!!
        assertTrue(high.shadowElevation <= 6.dp)
        assertTrue(lite.shadowElevation < balanced.shadowElevation && balanced.shadowElevation < high.shadowElevation)
        assertTrue(high.tintAlpha < balanced.tintAlpha && balanced.tintAlpha < lite.tintAlpha)
    }

    @Test fun signatureSelectsInInkAndDrawsRowsAsTiles() {
        val signature = designFor(DesignStyle.Signature)
        assertEquals(ChipSelectionTone.Ink, signature.mainScreen.chipSelection)
        assertTrue(signature.mainScreen.rowInset > 0.dp)
        assertTrue(signature.mainScreen.rowRadius > 0.dp)
        assertTrue(signature.mainScreen.unreadPreviewEmphasis)
        // The dock is not part of Signature: Auto pins the bar, the user may still float it.
        assertFalse(signature.navigationFloating)
        assertTrue(designFor(DesignStyle.Signature, navigationPlacement = com.beykus.beygram.data.appearance.NavigationPlacement.Floating).navigationFloating)
    }

    @Test fun signatureRowAndChipTokensDoNotLeakIntoOtherStyles() {
        DesignStyle.entries.filter { it != DesignStyle.Signature }.forEach { style ->
            val main = designFor(style).mainScreen
            assertNotEquals("$style must not use ink chips", ChipSelectionTone.Ink, main.chipSelection)
            assertEquals("$style keeps full-width rows", 0.dp, main.rowInset)
            assertFalse("$style keeps the quiet preview", main.unreadPreviewEmphasis)
        }
    }
}
