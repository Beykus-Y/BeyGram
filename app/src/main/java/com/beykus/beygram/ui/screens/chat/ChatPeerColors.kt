package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.beykus.beygram.domain.model.GroupAuthorEmojiStatus
import com.beykus.beygram.domain.model.PeerAccentColor

internal val LocalChatAuthorColor = compositionLocalOf { Color.Unspecified }
internal val LocalChatAuthorStatus = compositionLocalOf<GroupAuthorEmojiStatus?> { null }

// TDLib reserves these seven IDs and leaves their exact shades to the app theme.
private val lightBuiltIns = listOf(0xB43F39, 0x9F561B, 0x7556B6, 0x37763F, 0x267181, 0x3569A6, 0xA64973)
private val darkBuiltIns = listOf(0xF39790, 0xEAAF79, 0xBCABF6, 0x93CC96, 0x83C8D5, 0x94BEF0, 0xEDA3C1)

internal fun PeerAccentColor.avatarColor(dark: Boolean): Color =
    Color((if (dark) darkBuiltIns else lightBuiltIns)[builtInId] or 0xFF000000.toInt())

internal fun PeerAccentColor.authorColor(dark: Boolean, background: Color, foreground: Color): Color {
    val rgb = (if (dark) darkRgb else lightRgb).firstOrNull()
    val accent = rgb?.let { Color(it or 0xFF000000.toInt()) } ?: avatarColor(dark)
    val backgroundLuminance = background.luminance()
    return (0..8).asSequence().map { lerp(accent, foreground, it / 8f) }.firstOrNull {
        val light = it.luminance()
        (maxOf(light, backgroundLuminance) + 0.05f) / (minOf(light, backgroundLuminance) + 0.05f) >= 4.5f
    } ?: foreground
}
