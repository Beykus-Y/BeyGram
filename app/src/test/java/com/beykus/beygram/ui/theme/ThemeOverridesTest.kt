package com.beykus.beygram.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeOverridesTest {
    @Test fun noOverridesLeavesTokensUnchanged() {
        assertEquals(LightTokens, LightTokens.withOverrides(null, null, dark = false))
    }

    @Test fun accentBringsItsDerivedRoles() {
        val red = 0xFFD6336C.toInt()
        val result = LightTokens.withOverrides(red, null, dark = false)
        assertEquals(Color(red), result.accent)
        assertTrue(result.bubbleOut != LightTokens.bubbleOut)
        assertEquals(LightTokens.bg, result.bg)
    }

    @Test fun bubbleColorWinsOverTheAccentDerivedOne() {
        val result = LightTokens.withOverrides(0xFF3B5BDB.toInt(), 0xFFFFE8C2.toInt(), dark = false)
        assertEquals(Color(0xFFFFE8C2), result.bubbleOut)
    }

    @Test fun textOnAccentIsTheMoreReadableOfBlackAndWhite() {
        assertEquals(Color.Black, LightTokens.withOverrides(0xFFFFEB3B.toInt(), null, dark = false).onAccent)
        assertEquals(Color.White, LightTokens.withOverrides(0xFF1B3A8A.toInt(), null, dark = false).onAccent)
    }

    @Test fun contrastRatioSpansOneToTwentyOne() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Color.White, Color.White), 0.01f)
    }

    @Test fun presetsRaiseNoWarning() {
        assertFalse(contrastIssues(LightTokens, null).any)
        assertFalse(contrastIssues(DarkTokens, null).any)
    }

    @Test fun darkBubbleUnderDarkTextIsFlagged() {
        val dark = 0xFF2E2952.toInt()
        assertTrue(contrastIssues(LightTokens, dark).bubbleText)
    }

    @Test fun unreadableBubbleColorFallsBackToThePresetOne() {
        val result = LightTokens.withOverrides(null, 0xFF2E2952.toInt(), dark = false)
        assertEquals(LightTokens.bubbleOut, result.bubbleOut)
    }

    @Test fun accentInkReadsOnTheSoftFill() {
        val result = LightTokens.withOverrides(0xFFD6336C.toInt(), null, dark = false)
        assertTrue(contrastRatio(result.accentInk, result.accentSoft) >= MinTextContrast)
    }
}
