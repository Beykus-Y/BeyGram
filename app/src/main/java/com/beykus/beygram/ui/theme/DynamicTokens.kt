package com.beykus.beygram.ui.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.ThemePreset

/** Colours from the system wallpaper exist from Android 12. Older systems have no such scheme. */
@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
val SystemColorsAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The tokens to draw with. Like [tokensFor], but [ThemePreset.MaterialYou] takes its colours from the system wallpaper.
 * Where the system has no such colours the Warm set is used, so a stored choice never leaves the app without a theme.
 */
@Composable
fun resolveTokens(preset: ThemePreset, mode: ThemeMode, systemDark: Boolean): BeyGramTokens {
    if (preset != ThemePreset.MaterialYou || !SystemColorsAvailable) return tokensFor(preset, mode, systemDark)
    val context = LocalContext.current
    val dark = mode.isDark(systemDark)
    // The wallpaper's colours reach the app as a configuration change, so the scheme is read again whenever it changes.
    val configuration = LocalConfiguration.current
    return remember(context, configuration, dark, mode == ThemeMode.Amoled) {
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        tokensFromScheme(scheme, dark).let { if (mode == ThemeMode.Amoled) it.toAmoled() else it }
    }
}

/** The tokens for a saved choice: the preset (system colours where chosen), then the colours the user changed in the editor. */
@Composable
fun resolveTokens(config: AppearanceConfig, systemDark: Boolean): BeyGramTokens =
    resolveTokens(config.preset, config.mode, systemDark)
        .withOverrides(config.accentColor, config.outgoingBubbleColor, config.mode.isDark(systemDark))

/**
 * Maps a Material colour scheme onto the BeyGram roles. Backgrounds and the accent come from the scheme;
 * the status colours (ok, warning, danger) keep the Warm values for the same brightness, because their meaning must not drift with the wallpaper.
 * A text colour that would be hard to read on the outgoing bubble is swapped for the scheme's own text colour for that fill.
 */
fun tokensFromScheme(scheme: ColorScheme, dark: Boolean): BeyGramTokens {
    val status = if (dark) DarkTokens else LightTokens
    val bubbleOut = scheme.primaryContainer
    val readableOnBubble = contrastRatio(scheme.onSurface, bubbleOut) >= MinTextContrast &&
        contrastRatio(scheme.onSurfaceVariant, bubbleOut) >= MinTextContrast
    return BeyGramTokens(
        bg = scheme.background,
        surface = if (dark) scheme.surfaceContainer else scheme.surfaceContainerLowest,
        surface2 = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainer,
        line = scheme.outlineVariant,
        text = if (readableOnBubble) scheme.onSurface else scheme.onPrimaryContainer,
        muted = if (readableOnBubble) scheme.onSurfaceVariant else scheme.onPrimaryContainer,
        accent = scheme.primary,
        accentSoft = scheme.secondaryContainer,
        accentInk = scheme.onSecondaryContainer,
        onAccent = scheme.onPrimary,
        bubbleOut = bubbleOut,
        ok = status.ok, okInk = status.okInk, warn = status.warn, warnInk = status.warnInk,
        danger = scheme.error
    )
}

