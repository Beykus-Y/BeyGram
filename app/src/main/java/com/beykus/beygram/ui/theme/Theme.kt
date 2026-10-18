package com.beykus.beygram.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/** Tokens of the theme in effect. [BeyGramTheme] provides them; screens read them through beyGramPalette(). */
val LocalBeyGramTokens = compositionLocalOf { LightTokens }

internal val DarkColorScheme: ColorScheme = DarkTokens.run {
    darkColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = accentInk,
        secondary = muted, tertiary = okInk, background = bg, surface = surface, surfaceVariant = surface2,
        onBackground = text, onSurface = text, onSurfaceVariant = muted, outline = line, outlineVariant = line, error = danger,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface2,
        secondaryContainer = accentSoft, onSecondaryContainer = accentInk
    )
}

internal val LightColorScheme: ColorScheme = LightTokens.run {
    lightColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = accentInk,
        secondary = muted, tertiary = okInk, background = bg, surface = surface, surfaceVariant = surface2,
        onBackground = text, onSurface = text, onSurfaceVariant = muted, outline = line, outlineVariant = line, error = danger,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface2,
        secondaryContainer = accentSoft, onSecondaryContainer = accentInk
    )
}

/**
 * Picks one token set and builds Material's colour scheme from it, so screens never mix light and dark values.
 * The choice comes from the caller, not from the brightness of the background.
 */
@Composable
fun BeyGramTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val tokens = if (darkTheme) DarkTokens else LightTokens
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalBeyGramTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
