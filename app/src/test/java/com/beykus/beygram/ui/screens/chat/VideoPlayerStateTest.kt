package com.beykus.beygram.ui.screens.chat

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerStateTest {
    @Test fun unknownDurationKeepsSliderAtStart() {
        assertEquals(0f, VideoPlayerState(positionMs = 5_000L, durationMs = 0L).fraction(), 0f)
    }

    @Test fun sliderFollowsPosition() {
        assertEquals(0.5f, VideoPlayerState(positionMs = 5_000L, durationMs = 10_000L).fraction(), 0.0001f)
    }

    @Test fun sliderNeverPassesTheEnd() {
        assertEquals(1f, VideoPlayerState(positionMs = 12_000L, durationMs = 10_000L).fraction(), 0f)
    }

    @Test fun seekWithoutDurationDoesNothing() {
        assertNull(seekPositionMs(0.5f, durationMs = 0L))
    }

    @Test fun seekScalesFractionToDuration() {
        assertEquals(2_000L, seekPositionMs(0.25f, durationMs = 8_000L))
    }

    @Test fun seekClampsFractionToTheTrack() {
        assertEquals(8_000L, seekPositionMs(2f, durationMs = 8_000L))
        assertEquals(0L, seekPositionMs(-1f, durationMs = 8_000L))
    }

    @Test fun idleVideoOffersPlay() {
        assertEquals(VideoAction.Play, VideoPlayerState().primaryAction())
    }

    @Test fun playingVideoOffersPause() {
        assertEquals(VideoAction.Pause, VideoPlayerState(playing = true).primaryAction())
    }

    @Test fun finishedVideoOffersReplayFromStart() {
        assertEquals(VideoAction.Replay, VideoPlayerState().afterPlaybackState(Player.STATE_ENDED).primaryAction())
    }

    @Test fun bufferingShowsSpinnerUntilReady() {
        val buffering = VideoPlayerState(playing = true).afterPlaybackState(Player.STATE_BUFFERING)
        assertTrue(buffering.buffering)
        assertFalse(buffering.afterPlaybackState(Player.STATE_READY).buffering)
    }

    @Test fun endingPlaybackStopsThePlayingFlag() {
        val ended = VideoPlayerState(playing = true).afterPlaybackState(Player.STATE_ENDED)
        assertFalse(ended.playing)
        assertTrue(ended.ended)
    }

    @Test fun restartAfterEndClearsTheEndMark() {
        val ended = VideoPlayerState().afterPlaybackState(Player.STATE_ENDED)
        assertFalse(ended.afterPlaybackState(Player.STATE_BUFFERING).ended)
    }

    @Test fun idleStateChangesNothing() {
        val playing = VideoPlayerState(playing = true)
        assertEquals(playing, playing.afterPlaybackState(Player.STATE_IDLE))
    }

    @Test fun failureStopsSpinnerAndPlaybackAndShowsMessage() {
        val failed = VideoPlayerState(playing = true, buffering = true).afterFailure()
        assertFalse(failed.playing)
        assertFalse(failed.buffering)
        assertTrue(failed.failed)
    }
}
