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
    }

    @Test fun paletteTakesEachRoleFromItsTokens() {
        listOf(LightTokens, DarkTokens).forEach { tokens ->
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
        assertEquals(LightTokens.bg, LightColorScheme.background)
        assertEquals(LightTokens.accent, LightColorScheme.primary)
        assertEquals(LightTokens.text, LightColorScheme.onSurface)
        assertEquals(DarkTokens.bg, DarkColorScheme.background)
        assertEquals(DarkTokens.accent, DarkColorScheme.primary)
        assertEquals(DarkTokens.muted, DarkColorScheme.onSurfaceVariant)
    }
}
