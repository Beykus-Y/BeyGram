package com.beykus.beygram.ui.screens.playback

import androidx.compose.ui.graphics.Color
import com.beykus.beygram.ui.theme.MinAccentContrast
import com.beykus.beygram.ui.theme.MinTextContrast
import com.beykus.beygram.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverColorTest {
    private val accent = Color(0xFF3B5BDB)

    @Test fun solidColourIsItsOwnDominantColour() {
        assertEquals(0xFFFF0000.toInt(), dominantCoverColor(IntArray(1024) { 0xFFFF0000.toInt() }))
    }

    @Test fun vividSubjectBeatsGreyBackground() {
        val pixels = IntArray(1024) { i -> if (i < 900) 0xFF808080.toInt() else 0xFF0040FF.toInt() }
        val picked = dominantCoverColor(pixels)!!
        assertTrue("expected a blue tint", (picked and 0xFF) > ((picked shr 16) and 0xFF))
    }

    @Test fun pixelsWithoutColourGiveNoColour() {
        assertNull(dominantCoverColor(IntArray(64) { 0xFF101010.toInt() }))
        assertNull(dominantCoverColor(IntArray(64) { 0xFFF8F8F8.toInt() }))
        assertNull(dominantCoverColor(IntArray(64) { 0x00FF0000 }))
        assertNull(dominantCoverColor(IntArray(0)))
    }

    @Test fun dominantColourIsOpaque() {
        val picked = dominantCoverColor(IntArray(256) { 0x80336699.toInt() })!!
        assertEquals(0xFF, picked ushr 24)
        assertEquals(0xFF336699.toInt(), picked)
    }

    @Test fun readableInkAlwaysReachesTextContrast() {
        for (level in 0..255 step 5) {
            val background = Color(level, level, level)
            assertTrue("ink on level $level", contrastRatio(readableInk(background), background) >= MinTextContrast)
        }
    }

    @Test fun sourceEqualToBaseLeavesTheSurfaceAlone() {
        val base = Color(0xFFF4F1EA)
        val result = coverSurface(base, base, Color(0xFF1A1A1A), Color(0xFF666666), accent, FULL_COVER_TINT)
        assertEquals(base, result.surface)
    }

    @Test fun themeTextStaysWhenItStillReads() {
        val text = Color(0xFF1A1A1A)
        val result = coverSurface(Color.White, Color(0xFFFFF3C4), text, Color(0xFF555555), accent, MINI_COVER_TINT)
        assertEquals(text, result.text)
    }

    @Test fun darkCoverOnDarkThemeSwitchesTextWhenNeeded() {
        val base = Color(0xFF111418)
        val result = coverSurface(base, Color(0xFFFFFF80), Color(0xFFE8ECF0), Color(0xFF9AA3AE), accent, FULL_COVER_TINT)
        assertTrue(contrastRatio(result.text, result.surface) >= MinTextContrast)
        assertTrue(contrastRatio(result.muted, result.surface) >= MinTextContrast)
    }

    @Test fun tintNeverDropsTextOrAccentBelowTheThemeFloor() {
        val themes = listOf(
            Triple(Color.White, Color(0xFF1A1A1A), Color(0xFF6B6B6B)),
            Triple(Color(0xFF111418), Color(0xFFE8ECF0), Color(0xFF9AA3AE))
        )
        val covers = buildList<Color> {
            for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) add(Color(r, g, b))
        }
        for ((base, text, muted) in themes) {
            for (cover in covers) {
                val result = coverSurface(base, cover, text, muted, accent, FULL_COVER_TINT)
                assertTrue(
                    "text on ${result.surface}",
                    contrastRatio(result.text, result.surface) >= minOf(MinTextContrast, contrastRatio(text, base))
                )
                assertTrue(
                    "muted on ${result.surface}",
                    contrastRatio(result.muted, result.surface) >= minOf(MinTextContrast, contrastRatio(muted, base))
                )
                assertTrue(
                    "accent on ${result.surface}",
                    contrastRatio(accent, result.surface) >= minOf(MinAccentContrast, contrastRatio(accent, base))
                )
            }
        }
    }
}
