package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeSpoilerTextureTest {
    @Test fun spoilerTextureNeverExceeds64x64() {
        assertEquals(64, SAFE_SPOILER_TEXTURE_DIM)
        assertEquals(64 * 64, safeSpoilerTexturePixels(123).size)
    }

    @Test fun allPixelsOpaqueSoOriginalCannotShowThrough() {
        val pixels = safeSpoilerTexturePixels(321)
        assertTrue(pixels.all { (it ushr 24) == 0xFF })
    }

    @Test fun deterministicTextureUsesNoSourceMediaPixels() {
        val first = safeSpoilerTexturePixels(42)
        val repeated = safeSpoilerTexturePixels(42)
        val different = safeSpoilerTexturePixels(43)
        assertTrue(first.contentEquals(repeated))
        assertFalse(first.contentEquals(different))
    }

    @Test fun generatedColorsRemainDarkAndDoNotFormPhotoDetail() {
        val pixels = safeSpoilerTexturePixels(0)
        assertTrue(pixels.all {
            val red = (it ushr 16) and 255
            val green = (it ushr 8) and 255
            val blue = it and 255
            red in 0..95 && green in 0..95 && blue in 0..110
        })
    }
    @Test fun portraitNineBySixteenInTallContainerIsWidthLimited() {
        val (w, h) = spoilerFitSize(1080f, 2000f, 1080, 1920)
        assertEquals(1080f, w, 0.01f)
        assertEquals(1920f, h, 0.01f)
    }

    @Test fun landscapeSixteenByNineIsWidthLimitedWithSmallerHeight() {
        val (w, h) = spoilerFitSize(1080f, 2000f, 1920, 1080)
        assertEquals(1080f, w, 0.01f)
        assertEquals(607.5f, h, 0.01f)
    }

    @Test fun landscapeInWideContainerIsHeightLimited() {
        val (w, h) = spoilerFitSize(1000f, 400f, 1920, 1080)
        assertEquals(400f * 1920f / 1080f, w, 0.01f)
        assertEquals(400f, h, 0.01f)
    }

    @Test fun panoramaIsClampedToFiveToOne() {
        val (w, h) = spoilerFitSize(1080f, 2000f, 5000, 100)
        assertEquals(1080f, w, 0.01f)
        assertEquals(216f, h, 0.01f)
    }

    @Test fun unknownSizeFallsBackToThreeByFour() {
        val (w, h) = spoilerFitSize(1080f, 2000f, 0, 0)
        assertEquals(1080f, w, 0.01f)
        assertEquals(1440f, h, 0.01f)
    }

    @Test fun squareMediaFitsWidthOfTallContainer() {
        val (w, h) = spoilerFitSize(1080f, 2000f, 1000, 1000)
        assertEquals(1080f, w, 0.01f)
        assertEquals(1080f, h, 0.01f)
    }
}
