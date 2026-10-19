package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaViewerZoomTest {
    private val size = IntSize(1000, 800)
    private val center = Offset(500f, 400f)

    /** Где окажется точка [content] (от центра, без трансформации) на экране при масштабе [scale] и смещении [offset]. */
    private fun onScreen(content: Offset, scale: Float, offset: Offset): Offset = content * scale + offset

    private fun assertNear(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.01f)
        assertEquals(expected.y, actual.y, 0.01f)
    }

    @Test fun doubleTapFromFullSizeKeepsTheTouchedPointUnderTheFinger() {
        val point = center + Offset(700f, 250f)
        val zoomed = offsetAroundPoint(point, center, Offset.Zero, from = 1f, to = 2.5f)
        assertNear(point - center, onScreen(point - center, 2.5f, zoomed))
    }

    @Test fun zoomingFurtherKeepsThePointUnderTheFinger() {
        val point = center + Offset(-120f, 80f)
        val offset = Offset(100f, -50f)
        val content = (point - center - offset) * (1f / 2f)
        val zoomed = offsetAroundPoint(point, center, offset, from = 2f, to = 3f)
        assertNear(point - center, onScreen(content, 3f, zoomed))
    }

    @Test fun zoomingBackToFullSizeCentersTheImage() {
        val point = center + Offset(300f, 200f)
        val zoomed = clampImageOffset(offsetAroundPoint(point, center, Offset(40f, -30f), from = 2f, to = 1f), scale = 1f, size = size)
        assertNear(Offset.Zero, zoomed)
    }

    @Test fun offsetCannotRevealTheEmptyEdgeOfTheImage() {
        assertNear(Offset(500f, -400f), clampImageOffset(Offset(900f, -900f), scale = 2f, size = size))
    }

    @Test fun offsetInsideTheImageIsKept() {
        assertNear(Offset(100f, 50f), clampImageOffset(Offset(100f, 50f), scale = 2f, size = size))
    }

    @Test fun zoomButtonsScaleTheOffsetAroundTheCentre() {
        assertNear(Offset(150f, -75f), offsetAroundCenter(Offset(100f, -50f), from = 2f, to = 3f, size = size))
    }

    @Test fun zoomOutButtonCentersThePannedImage() {
        assertNear(Offset.Zero, offsetAroundCenter(Offset(100f, -50f), from = 2f, to = 1f, size = size))
    }
}
