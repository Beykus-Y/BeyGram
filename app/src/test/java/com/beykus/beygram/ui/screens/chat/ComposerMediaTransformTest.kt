package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class ComposerMediaTransformTest {
    @Test fun geometryUsesRealCentersAndPreservesAspectRatio() {
        val result = composerMediaTransform(Rect(10f, 300f, 90f, 380f), Rect(100f, 50f, 300f, 250f))!!
        assertEquals(-150f, result.dx, 0.001f)
        assertEquals(190f, result.dy, 0.001f)
        assertEquals(0.4f, result.scale, 0.001f)
    }

    @Test fun missingBoundsNeverInventGeometry() {
        assertNull(composerMediaTransform(Rect(0f, 0f, 0f, 80f), Rect(0f, 0f, 200f, 200f)))
        assertNull(composerMediaTransform(Rect(0f, 0f, 80f, 80f), Rect(0f, 0f, 200f, 0f)))
    }

    @Test fun albumReceiptsPreserveOffscreenPositions() {
        val capture = ComposerMediaCapture(Rect(0f, 0f, 80f, 80f), 100L)
        val result = matchComposerMediaReceipts(listOf(capture, null, capture), listOf(11L, 12L, 13L), 200L)
        assertEquals(listOf(11L, 13L), result.map { it.messageId })
        assertTrue(matchComposerMediaReceipts(listOf(capture), listOf(11L, 12L), 200L).isEmpty())
        assertTrue(matchComposerMediaReceipts(listOf(capture, capture), listOf(11L, 11L), 200L).isEmpty())
        assertTrue(matchComposerMediaReceipts(listOf(capture), listOf(11L), 900L).isEmpty())
    }
}
