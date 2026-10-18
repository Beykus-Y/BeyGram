package com.beykus.beygram.ui.theme

import androidx.compose.ui.graphics.Color
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.ThemePreset

/**
 * The tokens to draw with for a colour family and a brightness choice.
 * [ThemeMode.Amoled] takes the dark set and replaces its greys with black, so it stays dark whatever the system says.
 */
fun tokensFor(preset: ThemePreset, mode: ThemeMode, systemDark: Boolean): BeyGramTokens {
    val dark = mode.isDark(systemDark)
    val tokens = when (preset) {
        ThemePreset.Graphite -> if (dark) GraphiteDarkTokens else GraphiteLightTokens
        // The system colours need a Context. resolveTokens() supplies them; this pure path is the fallback.
        ThemePreset.Warm, ThemePreset.MaterialYou -> if (dark) DarkTokens else LightTokens
    }
    return if (mode == ThemeMode.Amoled) tokens.toAmoled() else tokens
}

internal fun BeyGramTokens.toAmoled(): BeyGramTokens = copy(
    bg = Color(0xFF000000),
    surface = Color(0xFF121214),
    surface2 = Color(0xFF1C1C20),
    line = Color(0xFF26262B)
)
