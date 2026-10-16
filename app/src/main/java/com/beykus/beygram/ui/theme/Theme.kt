package com.beykus.beygram.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = DarkTokens.run {
    darkColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = accentInk,
        secondary = muted, tertiary = okInk, background = bg, surface = surface, surfaceVariant = surface2,
        onBackground = text, onSurface = text, onSurfaceVariant = muted, outline = line, outlineVariant = line, error = danger,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface2,
        secondaryContainer = accentSoft, onSecondaryContainer = accentInk
    )
}

private val LightColorScheme = LightTokens.run {
    lightColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = accentInk,
        secondary = muted, tertiary = okInk, background = bg, surface = surface, surfaceVariant = surface2,
        onBackground = text, onSurface = text, onSurfaceVariant = muted, outline = line, outlineVariant = line, error = danger,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface2,
        secondaryContainer = accentSoft, onSecondaryContainer = accentInk
    )
}

@Composable
fun BeyGramTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
