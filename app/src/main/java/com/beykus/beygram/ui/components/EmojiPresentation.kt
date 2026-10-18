package com.beykus.beygram.ui.components

import android.icu.lang.UCharacter
import android.icu.lang.UProperty

private const val VARIATION_TEXT = '\uFE0E'
private const val VARIATION_EMOJI = '\uFE0F'

/**
 * [emoji] as the colour emoji font draws it. Telegram sends some emoji without U+FE0F (❤ for ❤️, ❤‍🔥 for ❤️‍🔥), and the
 * app font has a glyph for some of them, so a ZWJ sequence starting with one splits into separate glyphs. The selector makes
 * the system emoji font take the whole sequence. The string itself stays as Telegram sent it, since it is the reaction's key.
 */
internal fun emojiPresentation(emoji: String): String = qualifyEmoji(emoji, ::isTextDefaultEmoji)

/** [emoji] with U+FE0F added after each [isTextDefault] code point that has no selector of its own. */
internal fun qualifyEmoji(emoji: String, isTextDefault: (Int) -> Boolean): String {
    val out = StringBuilder(emoji.length + 2)
    var index = 0
    while (index < emoji.length) {
        val codePoint = emoji.codePointAt(index)
        val next = index + Character.charCount(codePoint)
        out.appendCodePoint(codePoint)
        val hasSelector = next < emoji.length && (emoji[next] == VARIATION_TEXT || emoji[next] == VARIATION_EMOJI)
        if (isTextDefault(codePoint) && !hasSelector) out.append(VARIATION_EMOJI)
        index = next
    }
    return out.toString()
}

/** Emoji that Unicode draws as text unless a selector follows, such as ❤ or ♂. ASCII is left out: digits, # and * are keycap bases. */
private fun isTextDefaultEmoji(codePoint: Int): Boolean =
    codePoint > 0x7F && UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI) &&
        !UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_PRESENTATION)
