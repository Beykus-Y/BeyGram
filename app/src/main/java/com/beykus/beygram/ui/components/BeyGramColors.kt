package com.beykus.beygram.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.beykus.beygram.ui.theme.BeyGramTokens
import com.beykus.beygram.ui.theme.LocalBeyGramTokens

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
    val successText: Color,
    val messageText: Color,
    val messageTime: Color
)

/** Screen colours of the theme in effect, all taken from [LocalBeyGramTokens]. */
@Composable
fun beyGramPalette(): BeyGramPalette {
    val tokens = LocalBeyGramTokens.current
    return remember(tokens) { tokens.toPalette() }
}

internal fun BeyGramTokens.toPalette() = BeyGramPalette(
    accent = accent,
    accentSoft = accentSoft,
    accentInk = accentInk,
    onAccent = onAccent,
    screen = bg,
    surface = surface,
    surface2 = surface2,
    textPrimary = text,
    textSecondary = muted,
    divider = line,
    incomingBubble = surface,
    outgoingBubble = bubbleOut,
    disabled = surface2,
    danger = danger,
    warningSurface = warn,
    warningText = warnInk,
    successSurface = ok,
    successText = okInk,
    messageText = bubbleText,
    messageTime = bubbleTime
)
