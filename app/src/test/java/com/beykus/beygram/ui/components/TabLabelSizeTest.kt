package com.beykus.beygram.ui.components

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class TabLabelSizeTest {
    @Test fun upToTheCapTheLabelKeepsItsNominalSize() {
        assertEquals(12.sp, tabLabelSize(12.sp, 1f))
        assertEquals(12.sp, tabLabelSize(12.sp, 1.3f))
        assertEquals(12.sp, tabLabelSize(12.sp, MaxTabLabelFontScale))
    }

    @Test fun aboveTheCapTheRenderedSizeStaysAtTheCap() {
        for (scale in listOf(1.8f, 2f)) {
            val rendered = tabLabelSize(12.sp, scale).value * scale
            assertEquals(12f * MaxTabLabelFontScale, rendered, 0.001f)
        }
    }
}
