package com.beykus.beygram.domain.lyrics

import com.beykus.beygram.data.music.provider.LyricLine as ProviderLyricLine
import com.beykus.beygram.data.music.provider.ProviderLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsModelsTest {

    @Test
    fun lyricWordValidationRejectsInvalidBoundsAndSurrogateSplits() {
        val line = "Hi 🎵!"
        // '🎵' is a surrogate pair at indices 3..5
        assertNull(LyricWord.validated(line, startMs = -1, endMs = 100, startCharIndex = 0, endCharIndexExclusive = 2))
        assertNull(LyricWord.validated(line, startMs = 200, endMs = 100, startCharIndex = 0, endCharIndexExclusive = 2))
        assertNull(LyricWord.validated(line, startMs = 0, endMs = 100, startCharIndex = 2, endCharIndexExclusive = 2))
        assertNull(LyricWord.validated(line, startMs = 0, endMs = 100, startCharIndex = 0, endCharIndexExclusive = line.length + 1))
        // Splitting inside surrogate pair
        assertNull(LyricWord.validated(line, startMs = 0, endMs = 100, startCharIndex = 4, endCharIndexExclusive = 5))
        assertNull(LyricWord.validated(line, startMs = 0, endMs = 100, startCharIndex = 3, endCharIndexExclusive = 4))

        val validEmoji = LyricWord.validated(line, startMs = 100, endMs = 350, startCharIndex = 3, endCharIndexExclusive = 5)
        assertNotNull(validEmoji)
        assertEquals("🎵", line.substring(validEmoji!!.startCharIndex, validEmoji.endCharIndexExclusive))
    }

    @Test
    fun providerLyricsAdaptsLosslesslyToLyricsDocument() {
        val synced = ProviderLyrics(
            lines = listOf(
                ProviderLyricLine(2_000L, "Line two"),
                ProviderLyricLine(1_000L, "Line one"),
                ProviderLyricLine(3_000L, ""),
            ),
            plainText = null,
            copyrightHolder = "Holder",
        )
        val doc = synced.toLyricsDocument()!!
        assertEquals(LyricsSyncMode.LineSynced, doc.syncMode)
        assertFalse(doc.hasWordTimings)
        assertEquals("Holder", doc.attribution.copyrightHolder)
        assertEquals(listOf("Line one", "Line two", ""), doc.lines.map { it.text })
        assertTrue(doc.lines[2].isInstrumental)
        assertEquals(-1, doc.activeLineIndex(500L))
        assertEquals(0, doc.activeLineIndex(1_500L))
        assertEquals(1, doc.activeLineIndex(2_000L))
        assertEquals(1, doc.toSyncedLyrics().activeIndex(2_000L))

        val plain = ProviderLyrics(lines = emptyList(), plainText = "First\nSecond", copyrightHolder = null)
        val plainDoc = plain.toLyricsDocument()!!
        assertEquals(LyricsSyncMode.Plain, plainDoc.syncMode)
        assertEquals(-1, plainDoc.activeLineIndex(1_000L))
        assertEquals(2, plainDoc.lines.size)

        val empty = ProviderLyrics(lines = emptyList(), plainText = "   ", copyrightHolder = null)
        assertNull(empty.toLyricsDocument())
    }
}
