package com.beykus.beygram.ui.theme

import androidx.compose.ui.graphics.luminance
import com.beykus.beygram.ui.components.toPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeyGramTokensTest {
    @Test fun lightTokensAreLightAndDarkTokensAreDark() {
        assertTrue(LightTokens.bg.luminance() > 0.5f)
        assertTrue(DarkTokens.bg.luminance() < 0.5f)
        assertTrue(GraphiteLightTokens.bg.luminance() > 0.5f)
        assertTrue(GraphiteDarkTokens.bg.luminance() < 0.5f)
    }

    @Test fun paletteTakesEachRoleFromItsTokens() {
        listOf(LightTokens, DarkTokens, GraphiteLightTokens, GraphiteDarkTokens).forEach { tokens ->
            val palette = tokens.toPalette()
            assertEquals(tokens.accent, palette.accent)
            assertEquals(tokens.bg, palette.screen)
            assertEquals(tokens.surface, palette.surface)
            assertEquals(tokens.surface2, palette.surface2)
            assertEquals(tokens.text, palette.textPrimary)
            assertEquals(tokens.muted, palette.textSecondary)
            assertEquals(tokens.line, palette.divider)
            assertEquals(tokens.bubbleOut, palette.outgoingBubble)
            assertEquals(tokens.danger, palette.danger)
            assertEquals(tokens.warn, palette.warningSurface)
            assertEquals(tokens.okInk, palette.successText)
        }
    }

    @Test fun materialSurfaceContainersHaveFiveReadableElevationLevels() {
        val palettes = listOf(
            LightTokens to false,
            DarkTokens to true,
            GraphiteLightTokens to false,
            GraphiteDarkTokens to true,
            DarkTokens.toAmoled() to true,
            GraphiteDarkTokens.toAmoled() to true
        )
        palettes.forEach { (tokens, dark) ->
            val scheme = tokens.toColorScheme(dark)
            val levels = listOf(
                scheme.surfaceContainerLowest, scheme.surfaceContainerLow,
                scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest
            )
            assertEquals("Distinct M3 surface roles for dark=$dark", 5, levels.distinct().size)
            assertEquals(if (dark) tokens.bg else tokens.surface, levels.first())
            assertEquals(tokens.surface2, levels.last())
            levels.forEach { color ->
                assertTrue("Primary text on elevated surface, dark=$dark", contrastRatio(tokens.text, color) >= MinTextContrast)
                assertTrue("Secondary text on elevated surface, dark=$dark", contrastRatio(tokens.muted, color) >= MinTextContrast)
            }
            val luminances = levels.map { it.luminance() }
            val ordered = if (dark) luminances.zipWithNext().all { (a, b) -> a < b }
                else luminances.zipWithNext().all { (a, b) -> a > b }
            assertTrue("Material elevation ordering for dark=$dark", ordered)
        }
    }

    @Test fun materialSchemeTakesItsRolesFromTheSameTokens() {
        val light = LightTokens.toColorScheme(dark = false)
        assertEquals(LightTokens.bg, light.background)
        assertEquals(LightTokens.accent, light.primary)
        assertEquals(LightTokens.text, light.onSurface)

        val dark = DarkTokens.toColorScheme(dark = true)
        assertEquals(DarkTokens.bg, dark.background)
        assertEquals(DarkTokens.accent, dark.primary)
        assertEquals(DarkTokens.muted, dark.onSurfaceVariant)
    }
}
