package com.beykus.beygram.ui.theme

import androidx.compose.ui.graphics.Color
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialSemanticRolesTest {
    @Test fun everyPresetMapsSemanticMaterialRolesWithoutDefaultPaletteLeakage() {
        ThemePreset.entries.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val tokens = tokensFor(preset, if (dark) ThemeMode.Dark else ThemeMode.Light, systemDark = dark)
                val c = tokens.toColorScheme(dark)
                assertEquals(tokens.accent, c.primary)
                assertEquals(tokens.accentInk, c.secondary)
                assertEquals(tokens.okInk, c.tertiary)
                assertEquals(tokens.danger, c.error)
                assertEquals(tokens.accent, c.surfaceTint)
                assertEquals(tokens.bg, c.background)
                assertEquals(tokens.text, c.inverseSurface)
                assertEquals(tokens.bg, c.inverseOnSurface)
                assertEquals(tokens.accentSoft, c.secondaryContainer)
                assertEquals(tokens.ok, c.tertiaryContainer)
                assertTrue(contrastRatio(c.onSecondary, c.secondary) >= 4.5f)
                assertTrue(contrastRatio(c.onTertiary, c.tertiary) >= 4.5f)
                assertTrue(contrastRatio(c.onError, c.error) >= 4.5f)
                assertTrue(contrastRatio(c.onErrorContainer, c.errorContainer) >= 4.5f)
            }
        }
    }

    @Test fun highContrastErrorInkWorksOnLightAndDarkErrorFills() {
        assertEquals(Color.White, readableOnColor(Color(0xFFB42318)))
        assertEquals(Color.Black, readableOnColor(Color(0xFFF97066)))
    }

    @Test fun userAccentOverridePropagatesIntoAllMappedSurfaceRoles() {
        val tokens = LightTokens.withOverrides(0xFF176BCC.toInt(), null, dark = false)
        val scheme = tokens.toColorScheme(false)
        assertEquals(tokens.accent, scheme.primary)
        assertEquals(tokens.accent, scheme.surfaceTint)
        assertEquals(tokens.accentInk, scheme.onSecondaryContainer)
    }
}
