package com.beykus.beygram.ui.components

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Grapheme segmentation needs the Android ICU implementation, which Robolectric provides on the JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmojiPresentationRangesTest {
    @Test
    fun aZwjSequenceIsOneRange() {
        val text = "\uD83D\uDC68\u200D\uD83D\uDCBB" // 👨‍💻
        assertEquals(listOf(0..4), emojiClusterRanges(text))
    }

    @Test
    fun aFlagIsOneRange() {
        val text = "\uD83C\uDDF7\uD83C\uDDFA" // 🇷🇺
        assertEquals(listOf(0..3), emojiClusterRanges(text))
    }

    @Test
    fun aKeycapIsOneRange() {
        val text = "#\u20E3\uFE0F"
        assertEquals(listOf(0..2), emojiClusterRanges(text))
    }

    @Test
    fun aTextDefaultEmojiWithItsSelectorKeepsTheSelector() {
        assertEquals(listOf(0..1), emojiClusterRanges("\u2764\uFE0F"))
    }

    @Test
    fun plainTextHasNoEmojiRange() {
        assertEquals(emptyList<IntRange>(), emojiClusterRanges("abc 123"))
    }

    @Test
    fun mixedTextGetsSpansWithoutChangingItsOffsets() {
        val text = "a\u2764b" // a ❤ b
        val annotated = emojiFontText(text)
        assertEquals(text, annotated.text)
        // AnnotatedString spans use an exclusive end, exactly on the UTF-16 offsets of the original text.
        assertEquals(listOf(1..2), annotated.spanStyles.map { it.start..it.end })
        assertEquals(FontFamily.Default, annotated.spanStyles.single().item.fontFamily)
    }
}