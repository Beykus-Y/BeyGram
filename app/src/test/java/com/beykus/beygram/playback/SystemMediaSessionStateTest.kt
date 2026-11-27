package com.beykus.beygram.playback

import androidx.media3.common.Player
import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemMediaSessionStateTest {
    private val item = PlaybackItem("Audio:8", MediaKind.Audio, "/8.mp3", "Eight", 10)
    private val exo = PlaybackState(item = item, playing = true, intentPlaying = true)
    private val native = exo.copy(bgMixActive = true)

    @Test
    fun directNativeHandoverPublishesStateEvenWhenPlayIntentDidNotChange() {
        val events = nativeSessionEventFlags(exo, native)
        assertTrue(Player.EVENT_PLAYBACK_STATE_CHANGED in events)
        assertTrue(Player.EVENT_PLAY_WHEN_READY_CHANGED in events)
        assertTrue(Player.EVENT_IS_PLAYING_CHANGED in events)
        assertTrue(Player.EVENT_MEDIA_METADATA_CHANGED in events)
        assertTrue(Player.EVENT_AVAILABLE_COMMANDS_CHANGED in events)
    }

    @Test
    fun ordinaryPositionTickDoesNotRepublishNotification() {
        assertTrue(nativeSessionEventFlags(native, native.copy(positionMs = 50)).isEmpty())
    }

    @Test
    fun nativePauseAndBufferingPublishRelevantEvents() {
        val paused = native.copy(playing = false, intentPlaying = false)
        val pauseEvents = nativeSessionEventFlags(native, paused)
        assertTrue(Player.EVENT_PLAY_WHEN_READY_CHANGED in pauseEvents)
        assertTrue(Player.EVENT_IS_PLAYING_CHANGED in pauseEvents)
        val waiting = paused.copy(intentPlaying = true, isBuffering = true)
        assertEquals(Player.STATE_BUFFERING, nativeSessionPlaybackState(waiting))
        assertTrue(Player.EVENT_PLAYBACK_STATE_CHANGED in nativeSessionEventFlags(paused, waiting))
        assertEquals(Player.STATE_ENDED, nativeSessionPlaybackState(waiting.copy(ended = true)))
    }

    @Test
    fun nextOccurrenceOfSameTrackStillPublishesTransition() {
        val first = native.copy(queue = listOf(item, item), queueIndex = 0)
        val next = first.copy(queueIndex = 1)
        val events = nativeSessionEventFlags(first, next)
        assertTrue(Player.EVENT_MEDIA_ITEM_TRANSITION in events)
        assertTrue(Player.EVENT_MEDIA_METADATA_CHANGED in events)
        assertTrue(Player.EVENT_POSITION_DISCONTINUITY in events)
        assertFalse(sameNativeSessionOccurrence(first, next))
    }

    @Test
    fun metadataAndRealDurationAreRepublishedWithoutChangingTracks() {
        val artwork = native.copy(item = item.copy(artworkPath = "/cover.jpg"))
        assertTrue(Player.EVENT_MEDIA_METADATA_CHANGED in nativeSessionEventFlags(native, artwork))
        assertTrue(Player.EVENT_MEDIA_METADATA_CHANGED in nativeSessionEventFlags(native, native.copy(durationMs = 10_000)))
        assertFalse(Player.EVENT_MEDIA_ITEM_TRANSITION in nativeSessionEventFlags(native, artwork))
    }

    @Test
    fun pendingNativePlaybackIsNotFalselyReadyAndPlaying() {
        assertEquals(Player.STATE_BUFFERING, nativeSessionPlaybackState(native.copy(playing = false, intentPlaying = true)))
    }

    @Test
    fun handingOwnershipBackToExoDoesNotPublishAFakeNativePause() {
        // Same track continues on ExoPlayer: the notification must not flicker pause/play at the renderer switch.
        assertTrue(nativeSessionEventFlags(native, exo).isEmpty())
    }

    @Test
    fun leavingNativeForAnotherOccurrenceStillPublishesTheTransition() {
        val next = item.copy(key = "Audio:9", occurrenceId = "Audio:9")
        val after = PlaybackState(item = next, playing = true, intentPlaying = true)
        val events = nativeSessionEventFlags(native, after)
        assertTrue(Player.EVENT_MEDIA_ITEM_TRANSITION in events)
        assertTrue(Player.EVENT_MEDIA_METADATA_CHANGED in events)
        assertTrue(Player.EVENT_POSITION_DISCONTINUITY in events)
        assertFalse(sameNativeSessionOccurrence(native, after))
    }

    @Test
    fun foregroundRecoveryUsesOnlyTheActualRenderer() {
        assertTrue(canRecoverPlaybackService(exo, native = false, exoPlaying = true))
        assertTrue(canRecoverPlaybackService(native, native = true, exoPlaying = false))
        assertFalse(canRecoverPlaybackService(native.copy(playing = false), native = true, exoPlaying = true))
        assertFalse(canRecoverPlaybackService(native.copy(ended = true), native = true, exoPlaying = false))
        assertFalse(canRecoverPlaybackService(PlaybackState(), native = false, exoPlaying = true))
        assertFalse(canRecoverPlaybackService(native.copy(item = item.copy(secret = true)), native = true, exoPlaying = true))
        assertFalse(canRecoverPlaybackService(exo.copy(item = item.copy(viewOnce = true)), native = false, exoPlaying = true))
    }
}
