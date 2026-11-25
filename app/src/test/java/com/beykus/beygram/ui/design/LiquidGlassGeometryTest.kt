package com.beykus.beygram.ui.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassGeometryTest {
    private val density = Density(1f)

    private fun radii(shape: androidx.compose.ui.graphics.Shape, width: Float, height: Float) =
        lensCornerRadii(shape, Size(width, height), density, LayoutDirection.Ltr)

    @Test
    fun rectangleHasNoCornerRadii() {
        assertEquals(LensCornerRadii.None, radii(RectangleShape, 100f, 40f))
    }

    @Test
    fun roundedCornersKeepTheirOwnRadii() {
        val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 4.dp, bottomEnd = 0.dp, bottomStart = 8.dp)
        assertEquals(LensCornerRadii(12f, 4f, 0f, 8f), radii(shape, 100f, 40f))
    }

    @Test
    fun circleUsesHalfTheShorterSideAtEveryCorner() {
        assertEquals(LensCornerRadii(30f, 30f, 30f, 30f), radii(CircleShape, 100f, 60f))
    }

    @Test
    fun radiusIsCappedAtHalfTheShorterSide() {
        assertEquals(LensCornerRadii(20f, 20f, 20f, 20f), radii(RoundedCornerShape(50.dp), 100f, 40f))
    }

    @Test
    fun thicknessIsCappedByTheShorterSide() {
        assertEquals(8f, lensThicknessPx(14f, Size(40f, 40f)), 0.001f)
        assertEquals(14f, lensThicknessPx(14f, Size(200f, 100f)), 0.001f)
    }

    @Test
    fun thicknessIsNeverBelowOnePixel() {
        assertEquals(1f, lensThicknessPx(14f, Size.Zero), 0.001f)
    }

    @Test
    fun metricsCountEveryEventOnce() {
        val before = LiquidGlassMetrics.snapshot()
        LiquidGlassMetrics.recordShaderCreated()
        LiquidGlassMetrics.recordFallbackFrame()
        val after = LiquidGlassMetrics.snapshot()
        assertEquals(before.shadersCreated + 1, after.shadersCreated)
        assertEquals(before.fallbackFrames + 1, after.fallbackFrames)
        assertEquals(before.lensEffectsBuilt, after.lensEffectsBuilt)
    }

    @Test
    fun defaultLensTuningStaysInItsPhysicalRange() {
        val tuning = DefaultLensTuning
        assertTrue(tuning.refractionIndex > 1f)
        assertTrue(tuning.strength > 0f)
        assertTrue(tuning.blurScale > 0f && tuning.blurScale <= 1f)
        assertTrue(tuning.thicknessDp > 0.dp)
    }
}
