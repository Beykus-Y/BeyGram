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
