package com.beykus.beygram.ui.components

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.icu.text.BreakIterator
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily

private const val VARIATION_TEXT = '\uFE0E'
private const val VARIATION_EMOJI = '\uFE0F'
private const val COMBINING_KEYCAP = '\u20E3'

/**
 * [emoji] as the colour emoji font draws it. Telegram sends some emoji without U+FE0F (❤ for ❤️, ❤‍🔥 for ❤️‍🔥), and the
 * app font has a glyph for some of them, so a ZWJ sequence starting with one splits into separate glyphs. The selector makes
 * the system emoji font take the whole sequence. The string itself stays as Telegram sent it, since it is the reaction's key.
 */
internal fun emojiPresentation(emoji: String): String = qualifyEmoji(emoji, ::isTextDefaultEmoji)

/**
 * The platform emoji font. Mixing emoji into a brand font (Source Sans 3 has glyphs for ❤, ♥, ♠, ♀, ♂) draws those as
 * monochrome text and can split a ZWJ sequence, so emoji clusters ask for the system font explicitly. The text itself
 * stays untouched: spans only change the font, never UTF-16 offsets used by entities or the cursor.
 */
internal val SystemEmojiFont: FontFamily = FontFamily.Default

/** True when [cluster] contains a code point that Unicode draws with the emoji font, including keycaps. */
internal fun isEmojiCluster(
    cluster: CharSequence,
    isEmoji: (Int) -> Boolean = { codePoint -> UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI) },
): Boolean {
    var index = 0
    while (index < cluster.length) {
        val codePoint = Character.codePointAt(cluster, index)
        // ASCII digits, # and * are keycap bases, not emoji on their own; a keycap cluster is caught by U+20E3.
        if ((codePoint > 0x7F && isEmoji(codePoint)) ||
            codePoint == VARIATION_EMOJI.code || codePoint == COMBINING_KEYCAP.code
        ) return true
        index += Character.charCount(codePoint)
    }
    return false
}

/**
 * UTF-16 ranges of the emoji grapheme clusters in [text]. A cluster is kept whole (ZWJ, skin tone, flags, keycaps), so a
 * span never separates the components of one emoji.
 */
internal fun emojiClusterRanges(text: CharSequence): List<IntRange> {
    if (text.isEmpty()) return emptyList()
    val value = text.toString()
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(value)
    val ranges = ArrayList<IntRange>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        if (isEmojiCluster(value.substring(start, end))) ranges += start until end
        start = end
        end = iterator.next()
    }
    return ranges
}

/** Adds the system emoji font to every emoji cluster of [text], leaving the string and all offsets unchanged. */
internal fun AnnotatedString.Builder.addEmojiFontSpans(text: CharSequence) {
    val style = SpanStyle(fontFamily = SystemEmojiFont)
    emojiClusterRanges(text).forEach { range -> addStyle(style, range.first, range.last + 1) }
}

/** [text] with the system emoji font on its emoji clusters, for one-line previews and labels. */
internal fun emojiFontText(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    addEmojiFontSpans(text)
}

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
