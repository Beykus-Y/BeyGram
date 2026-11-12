package com.beykus.beygram.playback

import com.beykus.beygram.media.bgmix.BgMixAudible
import org.junit.Assert.assertEquals
import org.junit.Test

class BgMixAlignmentTest {

    @Test
    fun aSpeakerThatIsAheadOfExoPlayerGivesAPositiveOffset() {
        // The cursor is at 10.2 s with 0.2 s queued: the speaker plays 10.0 s while ExoPlayer is at 9.9 s.
        val audible = BgMixAudible(cursorSeconds = 10.2, bufferedSeconds = 0.2)
        assertEquals(100.0, bgMixAlignmentOffsetMs(audible, exoMs = 9_900.0), 1e-6)
    }

    @Test
    fun anAlignedSpeakerHasNoOffset() {
        val audible = BgMixAudible(cursorSeconds = 5.1, bufferedSeconds = 0.1)
        assertEquals(0.0, bgMixAlignmentOffsetMs(audible, exoMs = 5_000.0), 1e-6)
    }

    @Test
    fun theSeekTargetIsExoPlayersPositionPlusTheQueuedAudio() {
        val audible = BgMixAudible(cursorSeconds = 10.2, bufferedSeconds = 0.2)
        assertEquals(10_100.0, bgMixAlignmentSeekMs(audible, exoMs = 9_900.0), 1e-6)
    }

    @Test
    fun aSeekToTheTargetIsHeardWhereExoPlayerIsOnceTheQueuedAudioHasPlayedOut() {
        // The queued 0.2 s play out first, and ExoPlayer moves on by the same 0.2 s meanwhile: 9.9 s becomes 10.1 s.
        val exoMs = 9_900.0
        val seekMs = bgMixAlignmentSeekMs(BgMixAudible(cursorSeconds = 10.2, bufferedSeconds = 0.2), exoMs)
        assertEquals(exoMs + 200.0, seekMs, 1e-6)
    }
}
