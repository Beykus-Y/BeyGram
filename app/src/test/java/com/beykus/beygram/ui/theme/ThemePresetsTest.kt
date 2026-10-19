package com.beykus.beygram.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.ThemePreset
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresetsTest {
    @Test fun warmFollowingSystemIsTheLookBeyGramShipsWith() {
        assertEquals(LightTokens, tokensFor(ThemePreset.Warm, ThemeMode.FollowSystem, systemDark = false))
        assertEquals(DarkTokens, tokensFor(ThemePreset.Warm, ThemeMode.FollowSystem, systemDark = true))
    }

    @Test fun explicitBrightnessIgnoresTheSystemSetting() {
        assertEquals(LightTokens, tokensFor(ThemePreset.Warm, ThemeMode.Light, systemDark = true))
        assertEquals(DarkTokens, tokensFor(ThemePreset.Warm, ThemeMode.Dark, systemDark = false))
    }

    @Test fun graphiteUsesItsOwnSets() {
        assertEquals(GraphiteLightTokens, tokensFor(ThemePreset.Graphite, ThemeMode.Light, systemDark = true))
        assertEquals(GraphiteDarkTokens, tokensFor(ThemePreset.Graphite, ThemeMode.Dark, systemDark = false))
    }

    @Test fun amoledIsDarkWithPureBlackBackground() {
        ThemePreset.entries.forEach { preset ->
            val tokens = tokensFor(preset, ThemeMode.Amoled, systemDark = false)
            assertEquals(Color.Black, tokens.bg)
            assertTrue(tokens.text.relativeLuminance() > tokens.bg.relativeLuminance())
        }
    }

    @Test fun systemColorsWithoutASchemeFallBackToWarm() {
        assertEquals(LightTokens, tokensFor(ThemePreset.MaterialYou, ThemeMode.Light, systemDark = true))
        assertEquals(DarkTokens, tokensFor(ThemePreset.MaterialYou, ThemeMode.FollowSystem, systemDark = true))
    }

    @Test fun systemSchemeMapsToReadableTokens() {
        val seeds = listOf(
            lightColorScheme() to false, darkColorScheme() to true,
            lightColorScheme(primary = Color(0xFF006C4C), primaryContainer = Color(0xFF89F8C7), onPrimaryContainer = Color(0xFF002114)) to false,
            darkColorScheme(primary = Color(0xFF6DDBAC), primaryContainer = Color(0xFF005138), onPrimaryContainer = Color(0xFF89F8C7)) to true
        )
        seeds.forEach { (scheme, dark) ->
            val tokens = tokensFromScheme(scheme, dark)
            assertEquals(scheme.primary, tokens.accent)
            assertEquals(scheme.primaryContainer, tokens.bubbleOut)
            listOf(tokens.bg, tokens.surface, tokens.surface2, tokens.bubbleOut).forEach { fill ->
                assertTrue("text, dark = $dark", contrast(tokens.text, fill) >= 4.5)
                assertTrue("muted text, dark = $dark", contrast(tokens.muted, fill) >= 4.5)
            }
            assertTrue("label on accent, dark = $dark", contrast(tokens.onAccent, tokens.accent) >= 4.5)
        }
    }

    @Test fun statusColoursDoNotFollowTheWallpaper() {
        val tokens = tokensFromScheme(lightColorScheme(primary = Color(0xFFCC0000)), dark = false)
        assertEquals(LightTokens.ok, tokens.ok)
        assertEquals(LightTokens.warn, tokens.warn)
    }

    @Test fun everyChoiceKeepsItsTextReadable() {
        ThemePreset.entries.forEach { preset ->
            ThemeMode.entries.forEach { mode ->
                listOf(false, true).forEach { systemDark ->
                    val tokens = tokensFor(preset, mode, systemDark)
                    val where = "$preset, $mode, system dark = $systemDark"
                    listOf(tokens.bg, tokens.surface, tokens.surface2, tokens.bubbleOut).forEach { fill ->
                        assertTrue("text on $where", contrast(tokens.text, fill) >= 4.5)
                        assertTrue("muted text on $where", contrast(tokens.muted, fill) >= 4.5)
                    }
                    assertTrue("accent on $where", contrast(tokens.accent, tokens.bg) >= 4.5)
                    assertTrue("label on accent on $where", contrast(tokens.onAccent, tokens.accent) >= 4.5)
                    assertTrue("accent text on soft accent on $where", contrast(tokens.accentInk, tokens.accentSoft) >= 4.5)
                    assertTrue("ok text on $where", contrast(tokens.okInk, tokens.ok) >= 4.5)
                    assertTrue("warning text on $where", contrast(tokens.warnInk, tokens.warn) >= 4.5)
                    assertTrue("danger on $where", contrast(tokens.danger, tokens.bg) >= 4.5)
                }
            }
        }
    }
}

/** WCAG contrast ratio between two colours, from 1 to 21. */
private fun contrast(a: Color, b: Color): Double {
    val (lighter, darker) = listOf(a.relativeLuminance(), b.relativeLuminance()).sortedDescending()
    return (lighter + 0.05) / (darker + 0.05)
}

/** WCAG relative luminance of an sRGB colour. */
private fun Color.relativeLuminance(): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}
