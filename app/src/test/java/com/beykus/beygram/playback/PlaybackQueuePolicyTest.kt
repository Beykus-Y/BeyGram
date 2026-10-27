package com.beykus.beygram.playback

import androidx.media3.common.Player
import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueuePolicyTest {
    private fun track(key: String, fileId: Int, path: String = "", secret: Boolean = false) = PlaybackItem(
        key = key,
        kind = MediaKind.Audio,
        path = path,
        title = key,
        durationSeconds = 100,
        secret = secret,
        fileId = fileId
    )

    @Test
    fun nextAndPreviousFollowQueueBoundaries() {
        assertTrue(canGoNext(3, 0, PlaybackRepeatMode.Off, shuffleEnabled = false))
        assertTrue(canGoNext(3, 1, PlaybackRepeatMode.Off, shuffleEnabled = false))
        assertFalse(canGoNext(3, 2, PlaybackRepeatMode.Off, shuffleEnabled = false))
        assertTrue(canGoNext(3, 2, PlaybackRepeatMode.All, shuffleEnabled = false))
        assertTrue(canGoNext(3, 2, PlaybackRepeatMode.Off, shuffleEnabled = true))

        assertFalse(canGoPrevious(3, 0, PlaybackRepeatMode.Off, shuffleEnabled = false))
        assertTrue(canGoPrevious(3, 1, PlaybackRepeatMode.Off, shuffleEnabled = false))
        assertTrue(canGoPrevious(3, 0, PlaybackRepeatMode.All, shuffleEnabled = false))
        assertTrue(canGoPrevious(3, 0, PlaybackRepeatMode.Off, shuffleEnabled = true))
    }

    @Test
    fun repeatOneDoesNotWrapManualNextAtTheEnd() {
        assertFalse(canGoNext(3, 2, PlaybackRepeatMode.One, shuffleEnabled = false))
        assertTrue(canGoNext(3, 1, PlaybackRepeatMode.One, shuffleEnabled = false))
    }

    @Test
    fun previousRestartsTheTrackAfterTheThreshold() {
        val item = track("a", 1)
        val playing = PlaybackState(item = item, positionMs = 3_000L)
        assertTrue(playing.canPrevious)
        assertFalse(PlaybackState(item = item, positionMs = 0L).canPrevious)
    }

    @Test
    fun oneItemQueueCannotGoNextOrPrevious() {
        val state = PlaybackState(
            item = track("a", 1),
            queue = listOf(track("a", 1)),
            queueIndex = 0,
            repeatMode = PlaybackRepeatMode.All,
            positionMs = 0L
        )
        assertFalse(state.canNext)
        assertFalse(state.canPrevious)
    }

    @Test
    fun secretItemsNeverEnterThePlaybackQueue() {
        val visible = track("visible", 1)
        val secret = track("secret", 2, secret = true)
        val queue = queueForPlayback(listOf(visible, secret), index = 1)
        assertTrue(queue.items.isEmpty())
        assertEquals(-1, queue.index)

        val adjusted = queueForPlayback(listOf(visible, secret, track("last", 3)), index = 2)
        assertEquals(listOf("visible", "last"), adjusted.items.map { it.key })
        assertEquals(1, adjusted.index)
    }

    @Test
    fun secretChatMediaNeverEnterThePlaybackQueueOrPrefetch() {
        val visible = track("visible", 1, path = "/visible.mp3")
        val private = track("private", 2).copy(isSecretChat = true)
        val queue = queueForPlayback(listOf(visible, private), index = 1)
        assertTrue(queue.items.isEmpty())

        val state = PlaybackState(item = visible, queue = listOf(visible, private), queueIndex = 0)
        assertTrue(prefetchCandidates(state).isEmpty())
    }

    @Test
    fun viewOnceMediaNeverEnterThePlaybackQueueOrPrefetch() {
        val visible = track("visible", 1, path = "/visible.mp3")
        val once = track("once", 2).copy(viewOnce = true)
        assertTrue(queueForPlayback(listOf(visible, once), index = 1).items.isEmpty())
        assertTrue(prefetchCandidates(PlaybackState(item = visible, queue = listOf(visible, once), queueIndex = 0)).isEmpty())
    }

    @Test
    fun prefetchLooksOnlyAtTheNextTwoUnresolvedPublicTracks() {
        val current = track("current", 1, path = "/current.mp3")
        val first = track("first", 2)
        val resolved = track("resolved", 3, path = "/already.mp3")
        val secret = track("secret", 4, secret = true)
        val third = track("third", 5)
        val fourth = track("fourth", 6)
        val state = PlaybackState(
            item = current,
            queue = listOf(current, first, resolved, secret, third, fourth),
            queueIndex = 0
        )
        assertEquals(listOf("first", "third"), prefetchCandidates(state).map { it.key })
    }

    @Test
    fun prefetchFollowsTheActualShuffledPlaybackOrder() {
        val current = track("current", 1, path = "/current.mp3")
        val first = track("first", 2)
        val second = track("second", 3)
        val third = track("third", 4)
        val fourth = track("fourth", 5)
        val state = PlaybackState(
            item = current,
            queue = listOf(current, first, second, third, fourth),
            queueIndex = 0,
            shuffleEnabled = true
        )
        assertEquals(
            listOf("third", "second"),
            prefetchCandidates(state, playbackOrder = listOf(0, 3, 2, 1, 4)).map { it.key }
        )
    }

    @Test
    fun repeatAllPrefetchWrapsToTheStartOfTheActualOrder() {
        val first = track("first", 1)
        val second = track("second", 2)
        val third = track("third", 3)
        val fourth = track("fourth", 4, path = "/fourth.mp3")
        val state = PlaybackState(
            item = fourth,
            queue = listOf(first, second, third, fourth),
            queueIndex = 3,
            shuffleEnabled = true,
            repeatMode = PlaybackRepeatMode.All
        )
        assertEquals(
            listOf("third", "second"),
            prefetchCandidates(state, playbackOrder = listOf(0, 3, 2, 1)).map { it.key }
        )
    }

    @Test
    fun deniedPrefetchIsNotRememberedForTheNextPolicyEvaluation() {
        assertFalse(shouldRememberPrefetch(allowed = false, accepted = false))
        assertFalse(shouldRememberPrefetch(allowed = true, accepted = false))
        assertTrue(shouldRememberPrefetch(allowed = true, accepted = true))
    }

    @Test
    fun timelineAvailabilityOverridesTheShuffleFallbackForNextAndPrevious() {
        val first = track("first", 1)
        val second = track("second", 2)
        val third = track("third", 3)
        val queue = listOf(first, second, third)
        val lastInShuffle = PlaybackState(
            item = third,
            queue = queue,
            queueIndex = 2,
            shuffleEnabled = true,
            timelineHasNext = false,
            timelineHasPrevious = true
        )
        assertFalse(lastInShuffle.canNext)
        assertTrue(lastInShuffle.canPrevious)

        val firstInShuffle = PlaybackState(
            item = first,
            queue = queue,
            queueIndex = 0,
            shuffleEnabled = true,
            positionMs = 0L,
            timelineHasNext = true,
            timelineHasPrevious = false
        )
        assertTrue(firstInShuffle.canNext)
        assertFalse(firstInShuffle.canPrevious)
    }

    @Test
    fun prefetchIgnoresNonMusicAndQueueWithoutCurrentIndex() {
        val voice = PlaybackItem("voice", MediaKind.Voice, "/voice.ogg", "Voice", 3)
        assertTrue(prefetchCandidates(PlaybackState(item = voice, queue = listOf(voice), queueIndex = 0)).isEmpty())
        assertTrue(prefetchCandidates(PlaybackState(item = track("a", 1), queue = listOf(track("a", 1)), queueIndex = -1)).isEmpty())
    }

    @Test
    fun queueForPlaybackKeepsTheVisibleOrderAndIndexUsedByTheMedia3Timeline() {
        val first = track("first", 1)
        val second = track("second", 2)
        val third = track("third", 3)
        val queue = queueForPlayback(listOf(first, second, third), index = 1)
        assertEquals(listOf("first", "second", "third"), queue.items.map { it.key })
        assertEquals(1, queue.index)
        assertEquals(listOf("first", "second", "third"), queue.items.map { it.occurrenceId })
    }

    @Test
    fun repeatModeCyclesThroughTheDocumentedOrder() {
        assertEquals(PlaybackRepeatMode.All, PlaybackRepeatMode.Off.next())
        assertEquals(PlaybackRepeatMode.One, PlaybackRepeatMode.All.next())
        assertEquals(PlaybackRepeatMode.Off, PlaybackRepeatMode.One.next())
    }

    @Test
    fun asyncEndedOnlyAdvancesTheStillCurrentSource() {
        assertTrue(shouldHandleEnded("a", "a", endedToken = 4L, currentToken = 4L))
        assertFalse(shouldHandleEnded("a", "b", endedToken = 4L, currentToken = 4L))
        assertFalse(shouldHandleEnded("a", "a", endedToken = 4L, currentToken = 5L))
        assertFalse(shouldHandleEnded(null, null, endedToken = 4L, currentToken = 4L))
    }

    @Test
    fun voiceSpeedAndMusicSpeedAreIndependent() {
        assertEquals(1f, playbackSpeedFor(MediaKind.Audio, musicSpeed = 1f, voiceSpeed = 2f))
        assertEquals(2f, playbackSpeedFor(MediaKind.Voice, musicSpeed = 1f, voiceSpeed = 2f))
        assertEquals(2f, playbackSpeedFor(MediaKind.VideoNote, musicSpeed = 1f, voiceSpeed = 2f))
    }

    @Test
    fun pendingDeferredGrowingSeekKeepsPlaybackIntent() {
        assertTrue(
            intentAfterPlayWhenReadyChange(
                previousIntent = false,
                playWhenReady = false,
                reason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                pendingDeferredResume = true
            )
        )
        assertFalse(
            intentAfterPlayWhenReadyChange(
                previousIntent = false,
                playWhenReady = false,
                reason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            )
        )
    }

    @Test
    fun focusLossKeepsIntentOnlyWhileItIsTransient() {
        assertFalse(
            intentAfterPlayWhenReadyChange(
                previousIntent = true,
                playWhenReady = false,
                reason = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
            )
        )
        assertTrue(
            intentAfterPlayWhenReadyChange(
                previousIntent = true,
                playWhenReady = true,
                reason = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
            )
        )
        assertFalse(
            intentAfterPlayWhenReadyChange(
                previousIntent = true,
                playWhenReady = false,
                reason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            )
        )
        assertFalse(
            intentAfterPlayWhenReadyChange(
                previousIntent = true,
                playWhenReady = false,
                reason = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
            )
        )
        assertTrue(
            intentAfterPlayWhenReadyChange(
                previousIntent = false,
                playWhenReady = true,
                reason = Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE
            )
        )
    }
    @Test
    fun queueRowSelectionNeverCrossesAccountOrPlaysPrivateContent() {
        val now = track("now", 1)
        val next = track("next", 2)
        val differentAccount = track("foreign", 3).copy(accountId = "foreign")
        val protected = track("once", 4).copy(viewOnce = true)
        val state = PlaybackState(
            item = now,
            queue = listOf(now, next, differentAccount, protected),
            queueIndex = 0
        )
        assertTrue(canSelectQueueIndex(state, 1))
        assertFalse(canSelectQueueIndex(state, 0))
        assertFalse(canSelectQueueIndex(state, 2))
        assertFalse(canSelectQueueIndex(state, 3))
        assertFalse(canSelectQueueIndex(state, -1))
        assertFalse(canSelectQueueIndex(state, 4))
        assertFalse(canSelectQueueIndex(state.copy(item = now.copy(secret = true)), 1))
    }

    @Test
    fun sleepTimerUsesElapsedTimeAndRoundsUpVisibleRemainingMinutes() {
        assertEquals(15, sleepTimerRemainingMinutes(900_000L, 0L))
        assertEquals(15, sleepTimerRemainingMinutes(900_000L, 1L))
        assertEquals(1, sleepTimerRemainingMinutes(900_000L, 899_999L))
        assertEquals(0, sleepTimerRemainingMinutes(900_000L, 900_000L))
        assertEquals(0, sleepTimerRemainingMinutes(900_000L, 1_000_000L))
        assertEquals(setOf(15, 30, 60), SLEEP_TIMER_MINUTES)
    }

}
