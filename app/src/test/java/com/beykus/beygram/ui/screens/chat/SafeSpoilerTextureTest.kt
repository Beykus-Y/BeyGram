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
}
