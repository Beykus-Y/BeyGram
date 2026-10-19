package com.beykus.beygram.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/** Tokens of the theme in effect. [BeyGramTheme] provides them; screens read them through beyGramPalette(). */
val LocalBeyGramTokens = compositionLocalOf { LightTokens }

/** Material's colour scheme with every role taken from these tokens. */
internal fun BeyGramTokens.toColorScheme(dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = accentInk,
            secondary = muted, tertiary = okInk, background = bg, surface = surface, surfaceVariant = surface2,
            onBackground = text, onSurface = text, onSurfaceVariant = muted, outline = line, outlineVariant = line, error = danger,
            surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface2,
            secondaryContainer = accentSoft, onSecondaryContainer = accentInk
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = accentInk,
            secondary = muted, tertiary = okInk, background = bg, surface = surface, surfaceVariant = surface2,
            onBackground = text, onSurface = text, onSurfaceVariant = muted, outline = line, outlineVariant = line, error = danger,
            surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface2,
            secondaryContainer = accentSoft, onSecondaryContainer = accentInk
        )
    }

/**
 * Provides one token set together with the Material colour scheme built from it, so screens never mix values from two themes.
 * [darkTheme] picks Material's dark or light scheme. [tokens] picks the colours and defaults to the Warm set for that brightness.
 */
@Composable
fun BeyGramTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    tokens: BeyGramTokens = if (darkTheme) DarkTokens else LightTokens,
    content: @Composable () -> Unit
) {
    val colorScheme = remember(tokens, darkTheme) { tokens.toColorScheme(darkTheme) }

    CompositionLocalProvider(LocalBeyGramTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
