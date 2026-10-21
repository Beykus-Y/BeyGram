package com.beykus.beygram.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
/** Keeps Material3 component colours in sync with the same BeyGram semantic tokens.
 * Unspecified Material3 defaults used to leak unrelated purple/error/secondary colours. */
internal fun BeyGramTokens.toColorScheme(dark: Boolean): ColorScheme {
    val containers = surfaceContainerTones(dark)
    val errorFill = lerp(surface, danger, if (dark) 0.17f else 0.10f)
    val bright = if (dark) surface2 else surface
    val dim = if (dark) bg else surface2
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = accentInk,
        secondary = accentInk, onSecondary = accentSoft, secondaryContainer = accentSoft, onSecondaryContainer = accentInk,
        tertiary = okInk, onTertiary = ok, tertiaryContainer = ok, onTertiaryContainer = okInk,
        background = bg, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = surface2, onSurfaceVariant = muted,
        surfaceTint = accent, surfaceBright = bright, surfaceDim = dim,
        surfaceContainerLowest = containers.lowest, surfaceContainerLow = containers.low,
        surfaceContainer = containers.normal, surfaceContainerHigh = containers.high,
        surfaceContainerHighest = containers.highest,
        outline = line, outlineVariant = line,
        inverseSurface = text, inverseOnSurface = bg, inversePrimary = onAccent,
        error = danger, onError = readableOnColor(danger), errorContainer = errorFill, onErrorContainer = text,
        scrim = Color.Black
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = accentInk,
        secondary = accentInk, onSecondary = accentSoft, secondaryContainer = accentSoft, onSecondaryContainer = accentInk,
        tertiary = okInk, onTertiary = ok, tertiaryContainer = ok, onTertiaryContainer = okInk,
        background = bg, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = surface2, onSurfaceVariant = muted,
        surfaceTint = accent, surfaceBright = bright, surfaceDim = dim,
        surfaceContainerLowest = containers.lowest, surfaceContainerLow = containers.low,
        surfaceContainer = containers.normal, surfaceContainerHigh = containers.high,
        surfaceContainerHighest = containers.highest,
        outline = line, outlineVariant = line,
        inverseSurface = text, inverseOnSurface = bg, inversePrimary = onAccent,
        error = danger, onError = readableOnColor(danger), errorContainer = errorFill, onErrorContainer = text,
        scrim = Color.Black
    )
    return scheme
}

/** High-contrast error button label, independent of the currently selected accent. */
internal fun readableOnColor(fill: Color): Color =
    if (contrastRatio(Color.White, fill) >= contrastRatio(Color.Black, fill)) Color.White else Color.Black

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
