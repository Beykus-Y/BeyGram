package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.BubbleStyle
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.ListDensity
import com.beykus.beygram.ui.design.DesignBubbleGeometry
import com.beykus.beygram.ui.design.designFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A style owns the base bubble geometry, and the user's own choices still apply on top of it. Default must keep the
 * exact numbers it always had, because a saved appearance without a style must look identical after the update.
 */
class StyleBubbleMetricsTest {
    @Test fun defaultGeometryIsUntouchedForEveryBubbleShape() {
        assertEquals(
            bubbleMetricsFor(BubbleStyle.Rounded, ListDensity.Comfortable),
            bubbleMetricsFor(BubbleStyle.Rounded, ListDensity.Comfortable, cornerRadiusDp = null, geometry = null)
        )
        assertEquals(20.dp, bubbleMetricsFor(BubbleStyle.Rounded, ListDensity.Comfortable).cornerRadius)
        assertEquals(14.dp, bubbleMetricsFor(BubbleStyle.Soft, ListDensity.Comfortable).cornerRadius)
        assertEquals(8.dp, bubbleMetricsFor(BubbleStyle.Square, ListDensity.Comfortable).cornerRadius)
    }

    @Test fun aStyleSuppliesTheBaseGeometry() {
        val geometry = DesignBubbleGeometry(
            cornerRadius = 18.dp, tailRadius = 4.dp, replyRadius = 12.dp, textInset = 12.dp, textTopInset = 8.dp
        )
        val rounded = bubbleMetricsFor(BubbleStyle.Rounded, ListDensity.Comfortable, null, geometry)
        assertEquals(18.dp, rounded.cornerRadius)
        assertEquals(4.dp, rounded.tailRadius)

        // Soft subtracts from the style base, Square caps it, and both keep the tail smaller than the body.
        assertEquals(12.dp, bubbleMetricsFor(BubbleStyle.Soft, ListDensity.Comfortable, null, geometry).cornerRadius)
        assertEquals(8.dp, bubbleMetricsFor(BubbleStyle.Square, ListDensity.Comfortable, null, geometry).cornerRadius)
        assertEquals(2.dp, bubbleMetricsFor(BubbleStyle.Square, ListDensity.Comfortable, null, geometry).tailRadius)
    }

    @Test fun theExplicitRadiusTheUserPickedAlwaysWins() {
        val geometry = DesignBubbleGeometry(
            cornerRadius = 24.dp, tailRadius = 8.dp, replyRadius = 14.dp, textInset = 12.dp, textTopInset = 8.dp
        )
        assertEquals(10.dp, bubbleMetricsFor(BubbleStyle.Rounded, ListDensity.Comfortable, 10, geometry).cornerRadius)
        assertEquals(10.dp, bubbleMetricsFor(BubbleStyle.Square, ListDensity.Compact, 10, geometry).cornerRadius)
    }

    @Test fun densityStillChangesSpacingOnly() {
        val geometry = designFor(DesignStyle.Material3).bubbles
        val compact = bubbleMetricsFor(BubbleStyle.Rounded, ListDensity.Compact, null, geometry)
        val spacious = bubbleMetricsFor(BubbleStyle.Rounded, ListDensity.Spacious, null, geometry)
        assertEquals(compact.cornerRadius, spacious.cornerRadius)
        assertNotEquals(compact.itemSpacing, spacious.itemSpacing)
    }

    @Test fun eachStyleExceptDefaultBringsItsOwnBubbleShape() {
        val shapes = DesignStyle.entries
            .filter { it != DesignStyle.Default }
            .map { designFor(it).bubbles!! }
        // Material 3 and Signature may share a radius, but the whole geometry (tail, reply, insets) must differ.
        assertEquals(shapes.size, shapes.distinct().size)
    }
}
