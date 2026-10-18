package com.beykus.beygram.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colour roles of one theme. Each colour family has a light and a dark set.
 * Material's colour scheme and beyGramPalette() are both built from the same set, so they cannot disagree.
 */
data class BeyGramTokens(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentInk: Color,
    val onAccent: Color,
    val bubbleOut: Color,
    val ok: Color,
    val okInk: Color,
    val warn: Color,
    val warnInk: Color,
    val danger: Color
)

/** The light set of the Warm preset. */
val LightTokens = BeyGramTokens(
    bg = Color(0xFFFAF8F5), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF3EFEA), line = Color(0xFFECE7E0),
    text = Color(0xFF1E1B18), muted = Color(0xFF67625B),
    accent = Color(0xFF6A4FDC), accentSoft = Color(0xFFEEE9FE), accentInk = Color(0xFF4B33B5), onAccent = Color(0xFFFFFFFF),
    bubbleOut = Color(0xFFE6DEFD),
    ok = Color(0xFFDDF5EC), okInk = Color(0xFF0F6B57), warn = Color(0xFFFFF1D6), warnInk = Color(0xFF7A4B00),
    danger = Color(0xFFB42318)
)

/** The dark set of the Warm preset. */
val DarkTokens = BeyGramTokens(
    bg = Color(0xFF161417), surface = Color(0xFF1F1C21), surface2 = Color(0xFF2A262C), line = Color(0xFF2E2A30),
    text = Color(0xFFF3EFEA), muted = Color(0xFFA39C95),
    accent = Color(0xFFA894FF), accentSoft = Color(0xFF2D2750), accentInk = Color(0xFFCFC4FF), onAccent = Color(0xFF16131F),
    bubbleOut = Color(0xFF2E2952),
    ok = Color(0xFF15332B), okInk = Color(0xFF8BE0CF), warn = Color(0xFF3A2D16), warnInk = Color(0xFFFFD28A),
    danger = Color(0xFFF97066)
)

/** Graphite, light: neutral greys with a cooler blue accent. */
val GraphiteLightTokens = BeyGramTokens(
    bg = Color(0xFFF5F6F8), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFECEEF2), line = Color(0xFFDFE2E8),
    text = Color(0xFF1B1D22), muted = Color(0xFF596170),
    accent = Color(0xFF3B5BDB), accentSoft = Color(0xFFE3E9FB), accentInk = Color(0xFF2B45A8), onAccent = Color(0xFFFFFFFF),
    bubbleOut = Color(0xFFDCE4F8),
    ok = Color(0xFFDDF3EA), okInk = Color(0xFF0F6B57), warn = Color(0xFFFFF1D6), warnInk = Color(0xFF7A4B00),
    danger = Color(0xFFB42318)
)

/** Graphite, dark: neutral greys with a cooler blue accent. */
val GraphiteDarkTokens = BeyGramTokens(
    bg = Color(0xFF16181C), surface = Color(0xFF1F2227), surface2 = Color(0xFF2A2E35), line = Color(0xFF2C3038),
    text = Color(0xFFEEF0F3), muted = Color(0xFF9AA1AC),
    accent = Color(0xFF8FA6FF), accentSoft = Color(0xFF26305A), accentInk = Color(0xFFC5D1FF), onAccent = Color(0xFF111527),
    bubbleOut = Color(0xFF2A3360),
    ok = Color(0xFF15332B), okInk = Color(0xFF8BE0CF), warn = Color(0xFF3A2D16), warnInk = Color(0xFFFFD28A),
    danger = Color(0xFFF97066)
)

/** Avatar colours from the design system's chat palette. */
val AvatarPalette = listOf(
    Color(0xFFC2593F), Color(0xFF5B4BC4), Color(0xFF2F8A7B),
    Color(0xFFA9731D), Color(0xFF3A78C2), Color(0xFFB0476E)
)
