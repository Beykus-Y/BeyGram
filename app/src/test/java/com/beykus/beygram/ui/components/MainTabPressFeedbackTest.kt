package com.beykus.beygram.ui.components

import androidx.compose.ui.graphics.Color
import com.beykus.beygram.ui.theme.DarkTokens
import com.beykus.beygram.ui.theme.LightTokens
import com.beykus.beygram.ui.theme.toAmoled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules behind the bottom-navigation press capsule: short, theme-aware and never a second selected pill. */
class MainTabPressFeedbackTest {

    @Test
    fun fadeStaysInTheShortFadeRangeFromTheIssue() {
        assertTrue(TabPressFadeMillis in 150..250)
    }

    @Test
    fun capsuleTakesItsHueFromTheThemeAccentInEveryTheme() {
        val light = LightTokens.toPalette()
        val dark = DarkTokens.toPalette()
        val amoled = DarkTokens.toAmoled().toPalette()

        listOf(light, dark, amoled).forEach { palette ->
            val capsule = tabPressCapsuleColor(palette.accent, selected = false, progress = 1f)
            assertEquals(palette.accent.red, capsule.red, 0f)
            assertEquals(palette.accent.green, capsule.green, 0f)
            assertEquals(palette.accent.blue, capsule.blue, 0f)
            assertEquals(TabPressAlpha, capsule.alpha, 0f)
        }
        // The three themes really do use different accents, so the capsule follows them.
        assertNotEquals(light.accent, dark.accent)
        assertEquals(Color(0xFF000000), amoled.screen)
        assertEquals(dark.accent, amoled.accent)
    }

    @Test
    fun selectedTabOnlyDeepensItsPillInsteadOfReplacingIt() {
        assertTrue(TabPressSelectedAlpha > 0f)
        assertTrue(TabPressSelectedAlpha < TabPressAlpha)
        assertEquals(
            TabPressSelectedAlpha,
            tabPressCapsuleColor(LightTokens.accent, selected = true, progress = 1f).alpha,
            0f
        )
    }

    @Test
    fun progressFadesTheCapsuleAndIsClamped() {
        val accent = DarkTokens.accent
        assertEquals(0f, tabPressCapsuleColor(accent, selected = false, progress = 0f).alpha, 0f)
        assertEquals(0f, tabPressCapsuleColor(accent, selected = false, progress = -1f).alpha, 0f)
        assertEquals(TabPressAlpha, tabPressCapsuleColor(accent, selected = false, progress = 2f).alpha, 0f)
        assertEquals(
            TabPressAlpha * 0.5f,
            tabPressCapsuleColor(accent, selected = false, progress = 0.5f).alpha,
            0.0001f
        )
    }

    @Test
    fun scaleIsSubtleAndReturnsToFullSizeWithoutGoingBelowThePressedValue() {
        assertEquals(1f, tabPressScale(0f), 0f)
        assertEquals(1f, tabPressScale(-1f), 0f)
        assertEquals(TabPressedScale, tabPressScale(1f), 0f)
        assertEquals(TabPressedScale, tabPressScale(2f), 0f)
        assertTrue(TabPressedScale > 0.9f && TabPressedScale < 1f)
    }
}
