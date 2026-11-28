package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One geometry is shared by the bottom dock and the pack sheet; the cell must stay a comfortable touch target. */
class StickerPickerMetricsTest {
    @Test
    fun theSharedCellFitsItsColumnAndTheMinimumTouchTarget() {
        assertEquals(65.dp, StickerPickerMetrics.columnMin)
        assertEquals(60.dp, StickerPickerMetrics.cell)
        assertTrue(StickerPickerMetrics.cell >= 48.dp)
        assertTrue(StickerPickerMetrics.cell <= StickerPickerMetrics.columnMin)
        assertTrue(StickerPickerMetrics.gap >= 0.dp)
    }
}