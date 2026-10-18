package com.beykus.beygram.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Smallest contrast ratio that body text needs against its background. */
internal const val MinTextContrast = 4.5f

/** Smallest contrast ratio that the accent needs against the screen background to stay visible. */
internal const val MinAccentContrast = 3f

/** WCAG contrast ratio of two opaque colours, from 1 (identical) to 21 (black on white). */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * These tokens with the colours the user picked in the editor. A null colour keeps the preset's value.
 * The accent brings its derived roles with it (soft fill, ink, text on the accent, and the outgoing bubble unless it is set itself).
 */
fun BeyGramTokens.withOverrides(accentColor: Int?, outgoingBubbleColor: Int?, dark: Boolean): BeyGramTokens {
    var result = this
    if (accentColor != null) {
        val picked = Color(accentColor).copy(alpha = 1f)
        result = result.copy(
            accent = picked,
            onAccent = if (contrastRatio(Color.White, picked) >= contrastRatio(Color.Black, picked)) Color.White else Color.Black,
            accentSoft = lerp(bg, picked, if (dark) 0.22f else 0.14f),
            accentInk = readableInk(picked, surface, text),
            bubbleOut = lerp(bg, picked, if (dark) 0.28f else 0.2f)
        )
    }
    if (outgoingBubbleColor != null) result = result.copy(bubbleOut = Color(outgoingBubbleColor).copy(alpha = 1f))
    return result
}

/** The colour moved toward [toward] until it reads on [background], or as far as it gets. */
private fun readableInk(color: Color, background: Color, toward: Color): Color {
    var ink = color
    var step = 0
    while (contrastRatio(ink, background) < MinTextContrast && step < 10) {
        step++
        ink = lerp(color, toward, step / 10f)
    }
    return ink
}

/** What the editor warns about for a set of tokens. */
internal data class ThemeContrastIssues(val bubbleText: Boolean, val accent: Boolean) {
    val any get() = bubbleText || accent
}

internal fun contrastIssues(tokens: BeyGramTokens): ThemeContrastIssues = ThemeContrastIssues(
    bubbleText = contrastRatio(tokens.text, tokens.bubbleOut) < MinTextContrast,
    accent = contrastRatio(tokens.accent, tokens.bg) < MinAccentContrast
)
