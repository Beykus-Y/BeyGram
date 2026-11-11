package com.beykus.beygram.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesFractionsOfAllLengths() {
        val lyrics = LrcParser.parse("[00:01.5]a\n[00:02.25]b\n[00:03.125]c\n[00:04]d")
        assertEquals(listOf(1_500L, 2_250L, 3_125L, 4_000L), lyrics.lines.map { it.startMs })
        assertEquals(listOf("a", "b", "c", "d"), lyrics.lines.map { it.text })
    }

    @Test
    fun expandsRepeatedTimestampsOnOneLine() {
        val lyrics = LrcParser.parse("[00:10.00][01:20.00]chorus")
        assertEquals(listOf(10_000L, 80_000L), lyrics.lines.map { it.startMs })
        assertEquals(setOf("chorus"), lyrics.lines.map { it.text }.toSet())
    }

    @Test
    fun ignoresMetadataTagsAndSortsByTime() {
        val raw = "[ti:Song]\n[ar:Artist]\n[00:30.00]second\n[00:05.00]first"
        val lyrics = LrcParser.parse(raw)
        assertEquals(listOf("first", "second"), lyrics.lines.map { it.text })
    }

    @Test
    fun appliesOffsetTagAndClampsNegativeStarts() {
        val shifted = LrcParser.parse("[offset:+500]\n[00:01.00]a")
        assertEquals(listOf(1_500L), shifted.lines.map { it.startMs })
        val clamped = LrcParser.parse("[offset:-2000]\n[00:00.50]b\n[00:03.00]c")
        assertEquals(listOf(0L, 1_000L), clamped.lines.map { it.startMs })
    }

    @Test
    fun skipsMalformedLinesWithoutThrowing() {
        val raw = "[00:61.00]bad seconds\n[xx:yy]bad\nplain text\n[00:02.00]good"
        val lyrics = LrcParser.parse(raw)
        assertEquals(listOf("good"), lyrics.lines.map { it.text })
    }

    @Test
    fun keepsEmptyTextLinesAsPauses() {
        val lyrics = LrcParser.parse("[00:01.00]one\n[00:05.00]\n[00:09.00]two")
        assertEquals(listOf("one", "", "two"), lyrics.lines.map { it.text })
    }

    @Test
    fun rejectsBlankAndOversizedInput() {
        assertTrue(LrcParser.parse("   ").isEmpty)
        val huge = "[00:01.00]" + "x".repeat(LrcParser.MAX_INPUT_CHARS)
        assertTrue(LrcParser.parse(huge).isEmpty)
    }

    @Test
    fun activeIndexFollowsPosition() {
        val lyrics = LrcParser.parse("[00:02.00]a\n[00:04.00]b\n[00:06.00]c")
        assertEquals(-1, lyrics.activeIndex(0))
        assertEquals(-1, lyrics.activeIndex(1_999))
        assertEquals(0, lyrics.activeIndex(2_000))
        assertEquals(0, lyrics.activeIndex(3_999))
        assertEquals(1, lyrics.activeIndex(4_000))
        assertEquals(2, lyrics.activeIndex(99_000))
    }

    @Test
    fun activeIndexOnEmptyLyricsIsMinusOne() {
        assertEquals(-1, SyncedLyrics(emptyList()).activeIndex(5_000))
    }
}
