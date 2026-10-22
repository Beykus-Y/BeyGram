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

    @Test fun pickedMessageTextIsKeptWhileItReadsOnBothBubbles() {
        val ink = 0xFF1A1714.toInt()
        assertEquals(Color(ink), LightTokens.withOverrides(null, null, dark = false, messageTextColor = ink).bubbleText)
    }

    @Test fun pickedMessageTextThatDoesNotReadOnTheBubblesIsDropped() {
        val white = 0xFFFFFFFF.toInt()
        assertEquals(LightTokens.bubbleText, LightTokens.withOverrides(null, null, dark = false, messageTextColor = white).bubbleText)
    }

    @Test fun pickedMessageTimeFollowsTheSameRule() {
        val grey = 0xFF67625B.toInt()
        assertEquals(Color(grey), LightTokens.withOverrides(null, null, dark = false, messageTimeColor = grey).bubbleTime)
        val white = 0xFFFFFFFF.toInt()
        assertEquals(LightTokens.bubbleTime, LightTokens.withOverrides(null, null, dark = false, messageTimeColor = white).bubbleTime)
    }

    @Test fun pickedBubbleStaysDroppedWhenNeitherTextReadsOnIt() {
        val bubble = 0xFF2E2952.toInt()
        val result = LightTokens.withOverrides(null, bubble, dark = false, messageTextColor = 0xFFFFFFFF.toInt())
        assertEquals(LightTokens.bubbleOut, result.bubbleOut)
        assertEquals(LightTokens.bubbleText, result.bubbleText)
    }

    @Test fun pickedBubbleAndPickedTextThatReadTogetherAreBothKept() {
        val bubble = 0xFFFFE8C2.toInt()
        val ink = 0xFF1A1714.toInt()
        val result = LightTokens.withOverrides(null, bubble, dark = false, messageTextColor = ink)
        assertEquals(Color(bubble), result.bubbleOut)
        assertEquals(Color(ink), result.bubbleText)
    }

    @Test fun messageTextAlwaysReadsOnTheBubblesItIsShownOn() {
        val bubbles = listOf(null, 0xFFFFE8C2.toInt(), 0xFF2E2952.toInt(), 0xFFE6DEFD.toInt())
        val texts = listOf(null, 0xFF1A1714.toInt(), 0xFFFFFFFF.toInt(), 0xFFCFC4FF.toInt())
        for (base in listOf(LightTokens, DarkTokens)) {
            for (bubble in bubbles) {
                for (text in texts) {
                    val result = base.withOverrides(null, bubble, dark = false, messageTextColor = text)
                    assertTrue("bubble $bubble, text $text", contrastRatio(result.bubbleText, result.bubbleOut) >= MinTextContrast)
                    assertTrue("bubble $bubble, text $text", contrastRatio(result.bubbleText, result.surface) >= MinTextContrast)
                }
            }
        }
    }

    @Test fun unreadableMessageColourIsFlaggedAndBlocksApply() {
        val issues = contrastIssues(LightTokens, null, pickedMessageText = 0xFFFFFFFF.toInt())
        assertTrue(issues.messageColour)
        assertTrue(issues.blocking)
        assertFalse(contrastIssues(LightTokens, null, pickedMessageText = 0xFF1A1714.toInt()).any)
    }
}
