package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgMixHandoverPolicyTest {
    @Test fun rejectsASupersededOccurrenceDuringHandover() {
        val staged = BgMixExoOccurrence("track", "window-1")
        assertTrue(staged.canFinishOn(staged.copy(), ended = false))
        assertFalse(staged.canFinishOn(staged.copy(windowUid = "window-2"), ended = false))
        assertFalse(staged.canFinishOn(staged.copy(mediaId = "next"), ended = false))
        assertFalse(staged.canFinishOn(null, ended = false))
        assertFalse(staged.canFinishOn(staged, ended = true))
    }

    @Test fun onlyAMeasuredAndCloseDeckMayCommitTheHandover() {
        // Within the tight target.
        assertTrue(BgMixAlignmentResult(alignedInTolerance = true, offsetsMs = listOf(3L)).safeToHandOver)
        // Out of the tight target but still the same moment of the song: allowed, the alternative is no BG Mix at all.
        assertTrue(BgMixAlignmentResult(alignedInTolerance = false, offsetsMs = listOf(120L)).safeToHandOver)
        // No measurement at all: the position of the deck is unknown.
        assertFalse(BgMixAlignmentResult(alignedInTolerance = false, offsetsMs = emptyList()).safeToHandOver)
        // A jump that would be heard as a repeat or a seek inside the track.
        assertFalse(BgMixAlignmentResult(alignedInTolerance = false, offsetsMs = listOf(301L)).safeToHandOver)
        assertFalse(BgMixAlignmentResult(alignedInTolerance = false, offsetsMs = listOf(-900L)).safeToHandOver)
        // Only the final measurement decides: earlier large offsets that were corrected do not block the commit.
        assertTrue(BgMixAlignmentResult(alignedInTolerance = false, offsetsMs = listOf(-900L, 20L)).safeToHandOver)
        // A failed seek after a large measurement is not hidden by an older, smaller offset.
        assertFalse(BgMixAlignmentResult(alignedInTolerance = false, offsetsMs = listOf(20L, -900L)).safeToHandOver)
    }


    private fun reason(
        enabled: Boolean = true,
        nativeAvailable: Boolean = true,
        isMusic: Boolean = true,
        systemPrivate: Boolean = false,
        isCandidate: Boolean = true,
        isRetiring: Boolean = false,
        isRejected: Boolean = false,
        isAttemptsExhausted: Boolean = false,
        playing: Boolean = true,
        intentPlaying: Boolean = true,
        ended: Boolean = false,
        waitingForNetwork: Boolean = false,
        hasDeferredSeek: Boolean = false,
        speed: Float = 1f,
        remainingMs: Long? = 30_000L,
        fileReady: Boolean = true,
    ): BgMixHandoverBlockReason = bgMixHandoverBlockReason(
        enabled = enabled,
        nativeAvailable = nativeAvailable,
        isMusic = isMusic,
        systemPrivate = systemPrivate,
        isCandidate = isCandidate,
        isRetiring = isRetiring,
        isRejected = isRejected,
        isAttemptsExhausted = isAttemptsExhausted,
        playing = playing,
        intentPlaying = intentPlaying,
        ended = ended,
        waitingForNetwork = waitingForNetwork,
        hasDeferredSeek = hasDeferredSeek,
        speed = speed,
        remainingMs = remainingMs,
        minRemainingMs = 15_000L,
        fileReady = fileReady,
    )

    @Test
    fun readyWhenAllPreconditionsHold() {
        val r = reason()
        assertEquals(BgMixHandoverBlockReason.Ready, r)
        assertTrue(r.showsPreparingIndicator())
    }

    @Test
    fun reportsEachGateAndPreconditionAccurately() {
        assertEquals(BgMixHandoverBlockReason.Disabled, reason(enabled = false))
        assertEquals(BgMixHandoverBlockReason.NativeUnavailable, reason(nativeAvailable = false))
        assertEquals(BgMixHandoverBlockReason.NotMusicOrPrivate, reason(isMusic = false))
        assertEquals(BgMixHandoverBlockReason.NotMusicOrPrivate, reason(systemPrivate = true))
        assertEquals(BgMixHandoverBlockReason.FormatNotCandidate, reason(isCandidate = false))
        assertEquals(BgMixHandoverBlockReason.Retiring, reason(isRetiring = true))
        assertEquals(BgMixHandoverBlockReason.TrackRejected, reason(isRejected = true))
        assertEquals(BgMixHandoverBlockReason.AttemptsExhausted, reason(isAttemptsExhausted = true))
        assertEquals(BgMixHandoverBlockReason.NotPlaying, reason(playing = false))
        assertEquals(BgMixHandoverBlockReason.NotPlaying, reason(intentPlaying = false))
        assertEquals(BgMixHandoverBlockReason.NotPlaying, reason(ended = true))
        assertEquals(BgMixHandoverBlockReason.NotPlaying, reason(waitingForNetwork = true))
        assertEquals(BgMixHandoverBlockReason.NotPlaying, reason(hasDeferredSeek = true))
        assertEquals(BgMixHandoverBlockReason.SpeedNotNormal, reason(speed = 1.5f))
        assertEquals(BgMixHandoverBlockReason.WindowTooShort, reason(remainingMs = 14_999L))
        assertEquals(BgMixHandoverBlockReason.Ready, reason(remainingMs = null))
        assertEquals(BgMixHandoverBlockReason.FileNotReady, reason(fileReady = false))
    }

    @Test
    fun preparingIndicatorMatchesRealEligibility() {
        assertTrue(BgMixHandoverBlockReason.Ready.showsPreparingIndicator())
        assertTrue(BgMixHandoverBlockReason.FileNotReady.showsPreparingIndicator())
        assertTrue(BgMixHandoverBlockReason.Retiring.showsPreparingIndicator())

        assertFalse(BgMixHandoverBlockReason.Disabled.showsPreparingIndicator())
        assertFalse(BgMixHandoverBlockReason.NativeUnavailable.showsPreparingIndicator())
        assertFalse(BgMixHandoverBlockReason.NotMusicOrPrivate.showsPreparingIndicator())
        assertFalse(BgMixHandoverBlockReason.FormatNotCandidate.showsPreparingIndicator())
        assertFalse(BgMixHandoverBlockReason.TrackRejected.showsPreparingIndicator())
        assertFalse(BgMixHandoverBlockReason.AttemptsExhausted.showsPreparingIndicator())
        assertFalse(BgMixHandoverBlockReason.NotPlaying.showsPreparingIndicator())
        assertFalse(BgMixHandoverBlockReason.SpeedNotNormal.showsPreparingIndicator())
        assertFalse(BgMixHandoverBlockReason.WindowTooShort.showsPreparingIndicator())
    }
}
