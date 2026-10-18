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
        val soft = lerp(bg, picked, if (dark) 0.22f else 0.14f)
        result = result.copy(
            accent = picked,
            onAccent = if (contrastRatio(Color.White, picked) >= contrastRatio(Color.Black, picked)) Color.White else Color.Black,
            accentSoft = soft,
            accentInk = readableInk(picked, soft, text),
            bubbleOut = lerp(bg, picked, if (dark) 0.28f else 0.2f)
        )
    }
    // A picked bubble colour is kept only while the text reads on it, so a colour chosen in the light theme
    // cannot make messages unreadable once the system switches to the dark one.
    if (outgoingBubbleColor != null && isReadableBubble(text, outgoingBubbleColor)) {
        result = result.copy(bubbleOut = Color(outgoingBubbleColor).copy(alpha = 1f))
    }
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

/** Whether text of this colour reads on a bubble of the given ARGB colour. */
internal fun isReadableBubble(text: Color, bubbleColor: Int): Boolean =
    contrastRatio(text, Color(bubbleColor).copy(alpha = 1f)) >= MinTextContrast

/** What the editor warns about for a set of tokens. */
internal data class ThemeContrastIssues(val bubbleText: Boolean, val accent: Boolean) {
    val any get() = bubbleText || accent
}

internal fun contrastIssues(tokens: BeyGramTokens, pickedBubbleColor: Int?): ThemeContrastIssues = ThemeContrastIssues(
    bubbleText = pickedBubbleColor != null && !isReadableBubble(tokens.text, pickedBubbleColor),
    accent = contrastRatio(tokens.accent, tokens.bg) < MinAccentContrast
)
