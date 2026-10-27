package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaViewerTransformTest {
    private val thumbnail = Rect(100f, 200f, 300f, 400f)

    @Test
    fun withoutOriginThePageKeepsTheOldGrow() {
        val transform = mediaViewerTransform(null, pageWidth = 1000f, pageHeight = 2000f, travel = 1f)
        assertEquals(0.96f, transform.scale, 0.0001f)
        assertEquals(0f, transform.offsetX, 0f)
        assertEquals(0f, transform.offsetY, 0f)
    }

    @Test
    fun atRestThePageIsFullScreenAndUnmoved() {
        val transform = mediaViewerTransform(thumbnail, pageWidth = 1000f, pageHeight = 2000f, travel = 0f)
        assertEquals(1f, transform.scale, 0f)
        assertEquals(0f, transform.offsetX, 0f)
        assertEquals(0f, transform.offsetY, 0f)
    }

    @Test
    fun fullTravelLandsOnTheThumbnailCenterAndWidth() {
        val transform = mediaViewerTransform(thumbnail, pageWidth = 1000f, pageHeight = 2000f, travel = 1f)
        assertEquals(0.2f, transform.scale, 0.0001f)
        assertEquals(-300f, transform.offsetX, 0.0001f)
        assertEquals(-700f, transform.offsetY, 0.0001f)
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
    }

    @Test
    fun anEmptyOriginFallsBackToTheOldGrow() {
        val transform = mediaViewerTransform(Rect.Zero, pageWidth = 1000f, pageHeight = 2000f, travel = 1f)
        assertEquals(0.96f, transform.scale, 0.0001f)
    }
}
