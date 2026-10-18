package com.beykus.beygram.ui.theme

import androidx.compose.ui.graphics.Color
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.ThemePreset

/**
 * The tokens to draw with for a colour family and a brightness choice.
 * [ThemeMode.Amoled] takes the dark set and replaces its greys with black, so it stays dark whatever the system says.
 */
fun tokensFor(preset: ThemePreset, mode: ThemeMode, systemDark: Boolean): BeyGramTokens {
    val dark = mode.isDark(systemDark)
    val tokens = when (preset) {
        ThemePreset.Warm -> if (dark) DarkTokens else LightTokens
        ThemePreset.Graphite -> if (dark) GraphiteDarkTokens else GraphiteLightTokens
    }
    return if (mode == ThemeMode.Amoled) tokens.toAmoled() else tokens
}

/** The tokens for a saved choice: the preset and brightness, then the colours the user changed in the editor. */
fun tokensFor(config: AppearanceConfig, systemDark: Boolean): BeyGramTokens =
    tokensFor(config.preset, config.mode, systemDark)
        .withOverrides(config.accentColor, config.outgoingBubbleColor, config.mode.isDark(systemDark))

private fun BeyGramTokens.toAmoled(): BeyGramTokens = copy(
    bg = Color(0xFF000000),
    surface = Color(0xFF121214),
    surface2 = Color(0xFF1C1C20),
    line = Color(0xFF26262B)
)
