package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaViewerTransformTest {
    private val thumbnail = Rect(100f, 200f, 300f, 400f)

    @Test
    fun withoutOriginThePageKeepsTheOldGrow() {
        val transform = mediaViewerTransform(null, pageWidth = 1000f, pageHeight = 2000f, travel = 1f)
        assertEquals(0.96f, transform.scale, 0.0001f)
        assertEquals(0f, transform.offsetX, 0f)
        assertEquals(0f, transform.offsetY, 0f)
        assertEquals(0f, transform.cornerRadiusDp, 0f)
    }

    @Test
    fun atRestThePageIsFullScreenAndUnmoved() {
        val transform = mediaViewerTransform(thumbnail, pageWidth = 1000f, pageHeight = 2000f, travel = 0f)
        assertEquals(1f, transform.scale, 0f)
        assertEquals(0f, transform.offsetX, 0f)
        assertEquals(0f, transform.offsetY, 0f)
        assertEquals(0f, transform.cornerRadiusDp, 0f)
    }

    @Test
    fun fullTravelLandsOnTheThumbnailCenterAndWidth() {
        val transform = mediaViewerTransform(thumbnail, pageWidth = 1000f, pageHeight = 2000f, travel = 1f)
        assertEquals(0.2f, transform.scale, 0.0001f)
        assertEquals(-300f, transform.offsetX, 0.0001f)
        assertEquals(-700f, transform.offsetY, 0.0001f)
        assertTrue(transform.cornerRadiusDp > 0f)
    }

    @Test
    fun aThinThumbnailIsClampedToTheMinimumScale() {
        val thin = Rect(0f, 0f, 10f, 10f)
        val transform = mediaViewerTransform(thin, pageWidth = 1000f, pageHeight = 2000f, travel = 1f)
        assertEquals(0.1f, transform.scale, 0.0001f)
    }

    @Test
    fun intensityAboveFullKeepsTheScalePositiveAndOnTheThumbnail() {
        val full = mediaViewerTransform(thumbnail, pageWidth = 1000f, pageHeight = 2000f, travel = 1f)
        val expressive = mediaViewerTransform(thumbnail, pageWidth = 1000f, pageHeight = 2000f, travel = 1.5f)
        assertEquals(full.scale, expressive.scale, 0.0001f)
        assertEquals(full.offsetX, expressive.offsetX, 0.0001f)
        assertEquals(full.offsetY, expressive.offsetY, 0.0001f)
        assertEquals(full.cornerRadiusDp, expressive.cornerRadiusDp, 0.0001f)
    }

    @Test
    fun anEmptyOriginFallsBackToTheOldGrow() {
        val transform = mediaViewerTransform(Rect.Zero, pageWidth = 1000f, pageHeight = 2000f, travel = 1f)
        assertEquals(0.96f, transform.scale, 0.0001f)
        assertEquals(0f, transform.cornerRadiusDp, 0f)
    }

    @Test
    fun offscreenOriginFallsBackToNeutralGrow() {
        val below = Rect(100f, 2100f, 300f, 2300f)
        val above = Rect(100f, -300f, 300f, -10f)
        val right = Rect(1050f, 200f, 1250f, 400f)
        for (offscreen in listOf(below, above, right)) {
            val transform = mediaViewerTransform(offscreen, pageWidth = 1000f, pageHeight = 2000f, travel = 1f)
            assertEquals(0.96f, transform.scale, 0.0001f)
            assertEquals(0f, transform.offsetX, 0f)
            assertEquals(0f, transform.offsetY, 0f)
            assertEquals(0f, transform.cornerRadiusDp, 0f)
        }
    }

    @Test
    fun partiallyVisibleOriginRemainsEligible() {
        val partial = Rect(-50f, 200f, 150f, 400f)
        assertTrue(isOriginVisibleInWindow(partial, 1000f, 2000f))
        assertFalse(isOriginVisibleInWindow(Rect(0f, 2000f, 100f, 2100f), 1000f, 2000f))
    }

    @Test
    fun targetBoundsAccountForInsetsAndFooter() {
        val insetTarget = Rect(0f, 100f, 1000f, 1900f) // center (500, 1000)
        val transform = mediaViewerTransform(thumbnail, pageWidth = 1000f, pageHeight = 2000f, travel = 1f, target = insetTarget)
        assertEquals(0.2f, transform.scale, 0.0001f)
        assertEquals(-300f, transform.offsetX, 0.0001f)
        assertEquals(-700f, transform.offsetY, 0.0001f)

        val asymmetricTarget = Rect(0f, 100f, 1000f, 1700f) // center (500, 900)
        val asymmetric = mediaViewerTransform(thumbnail, pageWidth = 1000f, pageHeight = 2000f, travel = 1f, target = asymmetricTarget)
        assertEquals(-600f, asymmetric.offsetY, 0.0001f)
    }
}
