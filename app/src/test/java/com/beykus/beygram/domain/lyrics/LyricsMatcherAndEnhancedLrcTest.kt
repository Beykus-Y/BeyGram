package com.beykus.beygram.domain.lyrics

import com.beykus.beygram.domain.music.ExternalQueryMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMatcherAndEnhancedLrcTest {

    @Test
    fun matcherRejectsConflictingVersionsDurationMismatchAndLooseSubstrings() {
        val query = ExternalQueryMetadata(
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up",
            durationSeconds = 243,
        )
        assertNull(
            LyricsMatcher.scoreCandidate(
                query = query,
                candidateTitle = "Midnight City (Live)",
                candidateArtist = "M83",
                candidateDurationMs = 243_000L,
                syncMode = LyricsSyncMode.LineSynced,
            )
        )
        assertNull(
            LyricsMatcher.scoreCandidate(
                query = query,
                candidateTitle = "Midnight City",
                candidateArtist = "M83",
                candidateDurationMs = 250_000L, // > 2% / 2s tolerance
                syncMode = LyricsSyncMode.LineSynced,
            )
        )
        // Short substring must not match unrelated title when artist is blank
        assertNull(
            LyricsMatcher.scoreCandidate(
                query = ExternalQueryMetadata(title = "One", artist = "", album = null, durationSeconds = 180),
                candidateTitle = "One More Time",
                candidateArtist = "Daft Punk",
                candidateDurationMs = 180_000L,
                syncMode = LyricsSyncMode.LineSynced,
            )
        )
        val lineScore = LyricsMatcher.scoreCandidate(
            query = query,
            candidateTitle = "Midnight City",
            candidateArtist = "M83",
            candidateDurationMs = 243_500L,
            syncMode = LyricsSyncMode.LineSynced,
            candidateAlbum = "Hurry Up, We're Dreaming",
        )
        val wordScore = LyricsMatcher.scoreCandidate(
            query = query,
            candidateTitle = "Midnight City",
            candidateArtist = "M83",
            candidateDurationMs = 243_500L,
            syncMode = LyricsSyncMode.WordSynced,
            candidateAlbum = "Hurry Up",
        )
        assertNotNull(lineScore)
        assertNotNull(wordScore)
        assertTrue(wordScore!! > lineScore!!)
    }

    @Test
    fun enhancedLrcParsesRealWordTimingsWithoutSynthesizing() {
        val standard = "[00:01.00]Hello world\n[00:03.00]"
        val (stdMode, stdLines) = EnhancedLrcParser.parse(standard)
        assertEquals(LyricsSyncMode.LineSynced, stdMode)
        assertEquals(2, stdLines.size)
        assertTrue(stdLines[0].words.isEmpty())
        assertTrue(stdLines[1].isInstrumental)

        val enhanced = "[00:10.00]<00:10.00>Hello <00:10.50>world"
        val (wordMode, wordLines) = EnhancedLrcParser.parse(enhanced)
        assertEquals(LyricsSyncMode.WordSynced, wordMode)
        assertEquals(1, wordLines.size)
        assertEquals("Hello world", wordLines[0].text)
        assertEquals(2, wordLines[0].words.size)
        assertEquals(10_000L, wordLines[0].words[0].startMs)
        assertEquals(10_500L, wordLines[0].words[0].endMs)
        assertEquals("Hello", wordLines[0].text.substring(wordLines[0].words[0].startCharIndex, wordLines[0].words[0].endCharIndexExclusive))
    }
}
