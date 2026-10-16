package com.beykus.beygram.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.beykus.beygram.ui.theme.DarkTokens
import com.beykus.beygram.ui.theme.LightTokens

data class BeyGramPalette(
    val accent: Color,
    val accentSoft: Color,
    val accentInk: Color,
    val onAccent: Color,
    val screen: Color,
    val surface: Color,
    val surface2: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val incomingBubble: Color,
    val outgoingBubble: Color,
    val disabled: Color,
    val danger: Color,
    val warningSurface: Color,
    val warningText: Color,
    val successSurface: Color,
    val successText: Color
)

@Composable
fun beyGramPalette(): BeyGramPalette {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val t = if (isDarkTheme) DarkTokens else LightTokens
    // Accent-bearing roles follow the colour scheme so a user-chosen accent can flow through the theme later.
    val scheme = MaterialTheme.colorScheme

    return BeyGramPalette(
        accent = scheme.primary,
        accentSoft = scheme.primaryContainer,
        accentInk = scheme.onPrimaryContainer,
        onAccent = scheme.onPrimary,
        screen = scheme.background,
        surface = scheme.surface,
        surface2 = scheme.surfaceVariant,
        textPrimary = scheme.onSurface,
        textSecondary = scheme.onSurfaceVariant,
        divider = scheme.outlineVariant,
        incomingBubble = scheme.surface,
        outgoingBubble = t.bubbleOut,
        disabled = scheme.surfaceVariant,
        danger = scheme.error,
        warningSurface = t.warn,
        warningText = t.warnInk,
        successSurface = t.ok,
        successText = t.okInk
    )
}
