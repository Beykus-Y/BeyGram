package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEditPolicyTest {
    @Test
    fun clampKeepsCropInsideThePhotoAndAboveMinimum() {
        val clamped = clampCrop(NormalizedCrop(left = -0.4f, top = 0.9f, right = 1.5f, bottom = 0.95f))
        assertTrue(clamped.left >= 0f)
        assertTrue(clamped.top >= 0f)
        assertTrue(clamped.right <= 1f)
        assertTrue(clamped.bottom <= 1f)
        assertTrue(clamped.width >= MIN_CROP_FRACTION)
        assertTrue(clamped.height >= MIN_CROP_FRACTION)
    }

    @Test
    fun movePreservesSizeAndStopsAtBorders() {
        val crop = NormalizedCrop(0.2f, 0.2f, 0.6f, 0.6f)
        val moved = moveCrop(crop, 0.9f, -0.9f)
        assertEquals(0.4f, moved.width, 0.0001f)
        assertEquals(0.4f, moved.height, 0.0001f)
        assertEquals(0.6f, moved.left, 0.0001f)
        assertEquals(0f, moved.top, 0.0001f)
        assertEquals(1f, moved.right, 0.0001f)
        assertEquals(0.4f, moved.bottom, 0.0001f)
    }

    @Test
    fun cornerResizeAnchorsTheOppositeCorner() {
        val crop = NormalizedCrop(0.2f, 0.2f, 0.8f, 0.8f)
        val resized = resizeCrop(crop, CropEdge.TopLeft, 0.1f, 0.1f)
        assertEquals(0.3f, resized.left, 0.0001f)
        assertEquals(0.3f, resized.top, 0.0001f)
        assertEquals(0.8f, resized.right, 0.0001f)
        assertEquals(0.8f, resized.bottom, 0.0001f)

        val clamped = resizeCrop(crop, CropEdge.BottomRight, 1f, 1f)
        assertEquals(1f, clamped.right, 0.0001f)
        assertEquals(1f, clamped.bottom, 0.0001f)
    }

    @Test
    fun resizingCannotCrossTheOppositeCorner() {
        val crop = NormalizedCrop(0.3f, 0.3f, 0.7f, 0.7f)
        val resized = resizeCrop(crop, CropEdge.TopLeft, 2f, 2f)
        assertTrue(resized.left <= resized.right - MIN_CROP_FRACTION)
        assertTrue(resized.top <= resized.bottom - MIN_CROP_FRACTION)
    }

    @Test
    fun rotatingCropPreservesTheSelectedContent() {
        val crop = NormalizedCrop(0.1f, 0.2f, 0.5f, 0.6f)
        val right = rotateCrop(crop, clockwise = true)
        assertEquals(0.4f, right.left, 0.0001f)
        assertEquals(0.1f, right.top, 0.0001f)
        assertEquals(0.8f, right.right, 0.0001f)
        assertEquals(0.5f, right.bottom, 0.0001f)

        // Rotating right then left returns the original selection.
        val back = rotateCrop(right, clockwise = false)
        assertEquals(crop.left, back.left, 0.0001f)
        assertEquals(crop.top, back.top, 0.0001f)
        assertEquals(crop.right, back.right, 0.0001f)
        assertEquals(crop.bottom, back.bottom, 0.0001f)
    }

    @Test
    fun rotationReportsExpectedDimensions() {
        assertEquals(PhotoSize(400, 300), rotatedSize(400, 300, PhotoRotation.None))
        assertEquals(PhotoSize(300, 400), rotatedSize(400, 300, PhotoRotation.Clockwise90))
        assertEquals(PhotoSize(400, 300), rotatedSize(400, 300, PhotoRotation.Half))
        assertEquals(PhotoSize(300, 400), rotatedSize(400, 300, PhotoRotation.CounterClockwise90))
    }

    @Test
    fun aspectPresetFitsInsideThePhoto() {
        val square = cropForAspect(1f, 1600, 1200, NormalizedCrop())
        assertEquals(0.75f, square.width, 0.0001f)
        assertEquals(1f, square.height, 0.0001f)
        assertEquals(0.125f, square.left, 0.0001f)

        val wide = cropForAspect(16f / 9f, 1600, 1200, NormalizedCrop())
        assertEquals(1f, wide.width, 0.0001f)
        assertEquals(0.75f, wide.height, 0.0001f)

        // Free keeps the current manual selection.
        val manual = NormalizedCrop(0.2f, 0.2f, 0.7f, 0.7f)
        assertEquals(manual, cropForAspect(null, 1600, 1200, manual))
    }

    @Test
    fun pixelCropCoversTheWholeImageAndNeverCollapses() {
        assertEquals(PixelCrop(0, 0, 1000, 500), cropPixels(NormalizedCrop(), 1000, 500))
        val tiny = cropPixels(NormalizedCrop(0.999f, 0.999f, 0.9995f, 0.9995f), 1000, 500)
        assertTrue(tiny.width >= 1)
        assertTrue(tiny.height >= 1)
        val half = cropPixels(NormalizedCrop(0.25f, 0.25f, 0.75f, 0.75f), 1000, 500)
        assertEquals(250, half.left)
        assertEquals(125, half.top)
        assertEquals(500, half.width)
        assertEquals(250, half.height)
    }

    @Test
    fun scaledSizeNeverUpscales() {
        assertEquals(PhotoSize(800, 600), scaledSize(800, 600, 2560))
        assertEquals(PhotoSize(2560, 1920), scaledSize(4000, 3000, 2560))
        assertEquals(PhotoSize(1, 1), scaledSize(1, 1, 2560))
        assertEquals(PhotoSize(0, 0), scaledSize(0, 300, 2560))
    }

    @Test
    fun extremeAspectIsPaddedNotCropped() {
        assertEquals(PhotoSize(4000, 200), telegramPaddedSize(4000, 100))
        assertEquals(PhotoSize(4000, 1500), telegramPaddedSize(4000, 1500))
    }

    @Test
    fun transparencySelectsPngOutput() {
        assertEquals("image/png", photoExportMimeType(hasAlpha = true))
        assertEquals("image/jpeg", photoExportMimeType(hasAlpha = false))
        assertEquals("png", photoExportExtension(hasAlpha = true))
        assertEquals("jpg", photoExportExtension(hasAlpha = false))
    }

    @Test
    fun cropFlagsFullSelection() {
        assertTrue(NormalizedCrop().isFull)
        assertFalse(NormalizedCrop(0.1f, 0f, 1f, 1f).isFull)
    }

    @Test
    fun rotationCyclesInBothDirections() {
        assertEquals(PhotoRotation.Clockwise90, PhotoRotation.None.rotatedRight())
        assertEquals(PhotoRotation.CounterClockwise90, PhotoRotation.None.rotatedLeft())
        assertEquals(PhotoRotation.None, PhotoRotation.CounterClockwise90.rotatedRight())
    }
}
