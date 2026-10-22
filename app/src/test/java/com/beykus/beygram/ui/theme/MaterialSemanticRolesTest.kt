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
                assertTrue(contrastRatio(c.inversePrimary, c.inverseSurface) >= 4.5f)
                assertEquals(tokens.accentSoft, c.primaryFixed)
                assertEquals(tokens.bubbleOut, c.primaryFixedDim)
                assertEquals(tokens.accentInk, c.onPrimaryFixed)
                assertEquals(tokens.accentSoft, c.secondaryFixed)
                assertEquals(tokens.accentInk, c.onSecondaryFixed)
                assertEquals(tokens.ok, c.tertiaryFixed)
                assertEquals(tokens.okInk, c.onTertiaryFixed)
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

    @Test fun inverseSnackbarActionReadableWithExtremeAccentOverrides() {
        listOf(0xFFFFE11A.toInt(), 0xFF0C0F27.toInt(), 0xFFFF6200.toInt()).forEach { picked ->
            listOf(LightTokens to false, DarkTokens to true).forEach { (base, dark) ->
                val c = base.withOverrides(picked, null, dark).toColorScheme(dark)
                assertTrue(contrastRatio(c.inversePrimary, c.inverseSurface) >= 4.5f)
            }
        }
    }

    @Test fun userAccentOverridePropagatesIntoAllMappedSurfaceRoles() {
        val tokens = LightTokens.withOverrides(0xFF176BCC.toInt(), null, dark = false)
        val scheme = tokens.toColorScheme(false)
        assertEquals(tokens.accent, scheme.primary)
        assertEquals(tokens.accent, scheme.surfaceTint)
        assertEquals(tokens.accentInk, scheme.onSecondaryContainer)
    }
}
