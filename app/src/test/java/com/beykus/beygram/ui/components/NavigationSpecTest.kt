package com.beykus.beygram.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.data.appearance.DesignStyle
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
        assertEquals(0.dp, default.barElevation)
    }

    @Test fun everyStyleGetsItsOwnBarStructure() {
        val byStyle = DesignStyle.entries.associateWith { navigationSpecFor(designFor(it)) }

        assertFalse("Material 2 must not draw a selection pill", byStyle.getValue(DesignStyle.Material2).showSelectionPill)
        assertTrue("Material 2 separates the bar with a divider", byStyle.getValue(DesignStyle.Material2).divider)

        assertEquals(64.dp, byStyle.getValue(DesignStyle.Material3).pillWidth)
        assertFalse(byStyle.getValue(DesignStyle.Material3).floating)

        assertTrue(byStyle.getValue(DesignStyle.LiquidGlass).floating)
        assertTrue(byStyle.getValue(DesignStyle.Signature).floating)

        // Two floating styles must still differ, otherwise the selector would lie.
        assertTrue(
            byStyle.getValue(DesignStyle.LiquidGlass) != byStyle.getValue(DesignStyle.Signature)
        )
    }

    @Test fun floatingBarsAreNeverFlatAndFlatBarsAreNeverFloating() {
        DesignStyle.entries.forEach { style ->
            val spec = navigationSpecFor(designFor(style))
            val design = designFor(style)
            assertEquals(design.components.floatingNavigation, spec.floating)
            if (spec.floating) assertTrue("a floating bar needs a shadow", spec.barElevation > 0.dp)
        }
    }
}
