package com.beykus.beygram.ui.components

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.NavigationPlacement
import com.beykus.beygram.ui.design.designFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottom bar is the most visible piece of the interface, so the exact Default numbers are pinned here: a change
 * to them would silently change the look of every existing installation.
 */
class NavigationSpecTest {
    private val default = navigationSpecFor(designFor(DesignStyle.Default))

    @Test fun defaultBarKeepsItsOriginalMeasurements() {
        assertEquals(56.dp, default.pillWidth)
        assertEquals(32.dp, default.pillHeight)
        assertEquals(16.dp, default.pillRadius)
        assertEquals(12.sp, default.labelSize)
        assertEquals(24.dp, default.iconSize)
        assertEquals(10.dp, default.barTopPadding)
        assertEquals(8.dp, default.barBottomPadding)
        assertTrue(default.showSelectionPill)
        assertTrue(default.showLabels)
        assertFalse(default.divider)
        assertFalse(default.floating)
        assertEquals(0.dp, default.pinnedElevation)
        assertEquals(0.dp, default.elevation)
    }

    @Test fun everyStyleGetsItsOwnBarStructure() {
        val byStyle = DesignStyle.entries.associateWith { navigationSpecFor(designFor(it)) }

        assertFalse("Material 2 must not draw a selection pill", byStyle.getValue(DesignStyle.Material2).showSelectionPill)
        assertTrue("Material 2 separates the bar with a divider", byStyle.getValue(DesignStyle.Material2).divider)

        assertEquals(64.dp, byStyle.getValue(DesignStyle.Material3).pillWidth)
        assertFalse(byStyle.getValue(DesignStyle.Material3).floating)

        assertTrue(byStyle.getValue(DesignStyle.LiquidGlass).floating)
        // Signature is chat-first: pinned unless the user floats it, and never a pill.
        assertFalse(byStyle.getValue(DesignStyle.Signature).floating)
        assertTrue(byStyle.getValue(DesignStyle.Signature).indicatorMark)

        // Two floating styles must still differ, otherwise the selector would lie.
        assertTrue(
            byStyle.getValue(DesignStyle.LiquidGlass) != byStyle.getValue(DesignStyle.Signature)
        )
    }

    @Test fun material3BarFollowsTheNavigationBarSpecification() {
        val m3 = navigationSpecFor(designFor(DesignStyle.Material3))
        assertEquals(64.dp, m3.pillWidth)
        assertEquals(32.dp, m3.pillHeight)
        assertEquals(16.dp, m3.pillRadius)
        assertEquals(12.dp, m3.barTopPadding)
        assertEquals(16.dp, m3.barBottomPadding)
        // The selection is the indicator: the label keeps its weight and reads in the on-surface role.
        assertEquals(FontWeight.Medium, m3.labelWeight)
        assertEquals(FontWeight.Medium, m3.selectedLabelWeight)
        assertTrue(m3.selectedLabelOnSurface)
    }

    @Test fun otherStylesKeepTheirSelectedLabel() {
        DesignStyle.entries.filter { it != DesignStyle.Material3 }.forEach { style ->
            val spec = navigationSpecFor(designFor(style))
            assertEquals("$style label weight", FontWeight.SemiBold, spec.selectedLabelWeight)
            assertFalse("$style label colour", spec.selectedLabelOnSurface)
        }
    }

    @Test fun followTheStyleKeepsThePlacementEveryStyleHasAlwaysHad() {
        DesignStyle.entries.forEach { style ->
            val auto = designFor(style, navigationPlacement = NavigationPlacement.Auto)
            val natural = designFor(style)
            assertEquals(style, auto.style)
            assertEquals(natural.components.floatingNavigation, auto.navigationFloating)
            assertEquals(navigationSpecFor(natural), navigationSpecFor(auto))
        }
    }

    @Test fun pinnedPutsTheBarOfEveryStyleInTheOrdinaryBottomSlot() {
        DesignStyle.entries.forEach { style ->
            val design = designFor(style, navigationPlacement = NavigationPlacement.Pinned)
            val spec = navigationSpecFor(design)

            assertFalse("$style must be pinned", spec.floating)
            assertFalse(design.navigationFloating)
            // A pinned bar has no dock shadow hanging over the last row. Material 2 keeps its own app-bar elevation.
            assertEquals(
                if (style == DesignStyle.Material2) spec.pinnedElevation else 0.dp,
                spec.elevation
            )
            // The style still decides the contents: pinning must not flatten a style into another one.
            assertEquals(navigationSpecFor(designFor(style)).pillWidth, spec.pillWidth)
            assertEquals(navigationSpecFor(designFor(style)).showSelectionPill, spec.showSelectionPill)
        }
    }

    @Test fun floatingTurnsEveryStyleIntoTheSameKindOfDockWithoutChangingItsContents() {
        DesignStyle.entries.forEach { style ->
            val natural = navigationSpecFor(designFor(style))
            val forced = navigationSpecFor(designFor(style, navigationPlacement = NavigationPlacement.Floating))

            assertTrue("$style must float", forced.floating)
            assertTrue("a floating bar needs a shadow", forced.elevation > 0.dp)
            // Only the placement changed: the style's own bar is reused rather than replaced by a default dock.
            assertEquals(natural.copy(floating = true, floatingElevation = forced.floatingElevation), forced)
        }
    }

    @Test fun theTwoFloatingStylesDoNotShareOneDockGeometry() {
        val glass = navigationSpecFor(designFor(DesignStyle.LiquidGlass))
        val signature = navigationSpecFor(designFor(DesignStyle.Signature, navigationPlacement = NavigationPlacement.Floating))

        // Air, radius, elevation and the weight of the selection pill are designed per style, not copied.
        assertTrue("Glass keeps more air around the capsule", glass.horizontalMargin > signature.horizontalMargin)
        assertTrue("Glass keeps more air below the capsule", glass.bottomMargin > signature.bottomMargin)
        assertTrue("the capsule radii differ", glass.dockRadius != signature.dockRadius)
        assertTrue("Signature marks the selection instead of washing it", signature.indicatorMark && !glass.indicatorMark)
        assertTrue("Signature casts less shadow", signature.floatingElevation < glass.floatingElevation)
    }

    @Test fun floatingBarsAreNeverFlatAndFlatBarsAreNeverFloating() {
        DesignStyle.entries.forEach { style ->
            NavigationPlacement.entries.forEach { placement ->
                val design = designFor(style, navigationPlacement = placement)
                val spec = navigationSpecFor(design)
                assertEquals(design.navigationFloating, spec.floating)
                if (spec.floating) assertTrue("a floating bar needs a shadow", spec.elevation > 0.dp)
            }
        }
    }
}
