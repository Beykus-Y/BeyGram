package com.beykus.beygram.data.music.bgmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgMusicImportFlowTest {

    private val localA = "6f1d2c3a-4b5e-4f60-8a7b-9c0d1e2f3a4b"
    private val localB = "11111111-2222-4333-8444-555555555555"
    private val library = listOf(localA to 5L, localB to 2L)

    @Test
    fun unknownPlaylistIsANewPlaylist() {
        assertEquals(
            BgMusicImportDecision.NewPlaylist,
            BgMusicImportDecision.decide("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", 1L, library),
        )
    }

    @Test
    fun emptyLibraryIsANewPlaylist() {
        assertEquals(BgMusicImportDecision.NewPlaylist, BgMusicImportDecision.decide(localA, 1L, emptyList()))
    }

    @Test
    fun sameRevisionIsAlreadyImported() {
        assertEquals(BgMusicImportDecision.SameRevision(5L), BgMusicImportDecision.decide(localA, 5L, library))
    }

    @Test
    fun higherRevisionOffersAnUpdate() {
        assertEquals(BgMusicImportDecision.NewerRevision(5L), BgMusicImportDecision.decide(localA, 6L, library))
    }

    @Test
    fun lowerRevisionIsReportedAsOlder() {
        assertEquals(BgMusicImportDecision.OlderRevision(5L), BgMusicImportDecision.decide(localA, 4L, library))
    }

    @Test
    fun playlistIdComparisonIgnoresCase() {
        assertEquals(
            BgMusicImportDecision.SameRevision(5L),
            BgMusicImportDecision.decide(localA.uppercase(), 5L, library),
        )
    }

    @Test
    fun highestLocalRevisionIsUsedWhenAnIdAppearsTwice() {
        assertEquals(
            BgMusicImportDecision.NewerRevision(5L),
            BgMusicImportDecision.decide(localA, 6L, listOf(localA to 3L, localA to 5L)),
        )
    }

    @Test
    fun fileNameAndMimeTypeAreOnlyHints() {
        assertTrue(BgMusicReader.looksLikeCandidate("Mix.bgmusic", null))
        assertTrue(BgMusicReader.looksLikeCandidate("MIX.BGMUSIC", null))
        assertTrue(BgMusicReader.looksLikeCandidate(null, "application/vnd.beygram.music+zip; charset=binary"))
        assertFalse(BgMusicReader.looksLikeCandidate("renamed.zip", "application/zip"))
        assertFalse(BgMusicReader.looksLikeCandidate(null, null))
    }

    @Test
    fun renamedArchiveIsStillJudgedByItsContent() {
        // The same bytes would be imported whatever name they were saved under.
        val archive = writeToBytes(testManifest())
        assertFalse(BgMusicReader.looksLikeCandidate("notes.txt", "text/plain"))
        assertTrue(assertSuccess(readAll(archive)).manifest.tracks.size == 3)

        // A .bgmusic name over garbage passes the hint and fails only in read().
        assertTrue(BgMusicReader.looksLikeCandidate("broken.bgmusic", null))
        assertInvalid(readAll("garbage".toByteArray()), BgMusicInvalidReason.NotZip)
    }
}
