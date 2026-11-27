package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageMediaSizeTest {
    @Test fun commonVisualRatiosSurviveThePaneAndHeightLimits() {
        for ((width, height) in listOf(1 to 1, 16 to 9, 9 to 16, 21 to 9)) {
            for (pane in listOf(180f, 360f, 800f)) {
                val available = pane - 24
                val size = singleMediaSize(pane, available, width, height)
                assertEquals(width.toFloat() / height, size.width.value / size.height.value, 0.001f)
                assertTrue(size.width.value in 1f..minOf(available, 416f))
                assertTrue(size.height.value in 1f..360f)
            }
        }
    }

    @Test fun extremeAndUnknownMediaUseBoundedSlots() {
        val wide = singleMediaSize(360f, 336f, 4000, 100)
        val tall = singleMediaSize(360f, 336f, 100, 4000)
        val unknown = singleMediaSize(360f, 336f, 0, 0)
        assertEquals(3.2f, wide.width.value / wide.height.value, 0.001f)
        assertEquals(0.4f, tall.width.value / tall.height.value, 0.001f)
        assertEquals(1.5f, unknown.width.value / unknown.height.value, 0.001f)
        assertTrue(bubbleDecodeDimension(4000, 100, 732f, 360f, fit = true) <
            bubbleDecodeDimension(4000, 100, 732f, 360f))
    }

    @Test fun stickerPreservesRatioAndUsesTheActualPane() {
        val narrow = sentStickerSize(200f, 160f, 128, 256)
        val wide = sentStickerSize(800f, 760f, 128, 256)
        assertEquals(45f, narrow.width.value, 0.001f)
        assertEquals(90f, narrow.height.value, 0.001f)
        assertEquals(104f, wide.width.value, 0.001f)
        assertEquals(208f, wide.height.value, 0.001f)
    }
}
