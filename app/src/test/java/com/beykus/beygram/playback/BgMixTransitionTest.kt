package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.media.bgmix.BgMixQueueItem
import com.beykus.beygram.media.bgmix.BgMixStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BgMixTransitionTest {

    private fun track(key: String) = PlaybackItem(
        key = key,
        kind = MediaKind.Audio,
        path = "/music/$key.mp3",
        title = key,
        durationSeconds = 180
    )

    private val items = listOf(track("a"), track("b"), track("c"))

    /** Native ids 10, 11, 12 stand for queue indexes 0, 1, 2. */
    private val indexOfNative: (Long) -> Int? = { id -> (id - 10L).toInt().takeIf { it in items.indices } }
    private val itemAt: (Int) -> PlaybackItem? = { items.getOrNull(it) }

    private fun queueItem(id: Long, state: String) = BgMixQueueItem(
        id = id, name = "file$id.mp3", state = state, progress = 1f, bpm = 120.0, durationSeconds = 180.0, error = ""
    )

    private fun status(
        active: Boolean,
        progress: Double = 0.0,
        paused: Boolean = false,
        preparing: Boolean = false,
        queue: List<BgMixQueueItem>
    ) = BgMixStatus(
        paused = paused,
        master = 0,
        transitionActive = active,
        transitionAvailable = true,
        transitionType = "blend",
        transitionProgress = progress,
        preparingNext = preparing,
        preparingName = "",
        preparingProgress = 0f,
        lastError = "",
        status = "",
        decks = emptyList(),
        queue = queue
    )

    private fun next(previous: BgMixTransitionUi?, status: BgMixStatus, previousIndex: Int) =
        nextBgMixTransition(previous, status, previousIndex, indexOfNative, itemAt)

    @Test
    fun preparingTheNextTrackShowsNothing() {
        val s = status(active = false, preparing = true, queue = listOf(queueItem(10, "playing"), queueItem(11, "preparing")))
        assertNull(next(null, s, 0))
    }

    @Test
    fun runningTransitionNamesTheTrackAfterTheOutgoingOne() {
        val s = status(active = true, progress = 0.1, queue = listOf(queueItem(10, "playing"), queueItem(11, "ready"), queueItem(12, "pending")))
        val ui = next(null, s, 0)
        assertNotNull(ui)
        assertEquals("b", ui!!.target?.key)
        assertEquals("a", ui.fromKey)
        assertEquals(10L, ui.fromNativeId)
        assertEquals(0.1f, ui.progress, 0.0001f)
    }

    @Test
    fun targetStaysWhenTheIncomingTrackTakesOverHalfway() {
        val first = next(null, status(active = true, progress = 0.2, queue = listOf(queueItem(10, "playing"), queueItem(11, "ready"))), 0)
        // The engine already shows "b" as current (index 1); the line must still read "b", not the track after it.
        val later = next(first, status(active = true, progress = 0.6, queue = listOf(queueItem(10, "played"), queueItem(11, "playing"), queueItem(12, "ready"))), 1)
        assertEquals("b", later?.target?.key)
        assertEquals("a", later?.fromKey)
        assertEquals(0.6f, later!!.progress, 0.0001f)
    }

    @Test
    fun incomingTrackAlreadyPlayingOnTheFirstTickIsStillTheTarget() {
        val ui = next(null, status(active = true, progress = 0.05, queue = listOf(queueItem(10, "played"), queueItem(11, "playing"))), 0)
        assertEquals("b", ui?.target?.key)
    }

    @Test
    fun unknownTargetGivesALineWithoutATitle() {
        val ui = next(null, status(active = true, progress = 0.3, queue = listOf(queueItem(10, "playing"), queueItem(99, "ready"))), 0)
        assertNotNull(ui)
        assertNull(ui!!.target)
    }

    @Test
    fun pauseIsPassedOnAndProgressNeverRunsBack() {
        val first = next(null, status(active = true, progress = 0.4, queue = listOf(queueItem(10, "playing"), queueItem(11, "ready"))), 0)
        val paused = next(first, status(active = true, progress = 0.38, paused = true, queue = listOf(queueItem(10, "playing"), queueItem(11, "ready"))), 0)
        assertTrue(paused!!.paused)
        assertEquals(0.4f, paused.progress, 0.0001f)
        val resumed = next(paused, status(active = true, progress = 0.45, queue = listOf(queueItem(10, "playing"), queueItem(11, "ready"))), 0)
        assertFalse(resumed!!.paused)
    }

    @Test
    fun endOfTransitionClearsTheLine() {
        val first = next(null, status(active = true, progress = 0.9, queue = listOf(queueItem(10, "playing"), queueItem(11, "ready"))), 0)
        assertNull(next(first, status(active = false, queue = listOf(queueItem(10, "played"), queueItem(11, "playing"))), 1))
    }

    @Test
    fun backToBackTransitionTakesANewPair() {
        val first = next(null, status(active = true, progress = 0.95, queue = listOf(queueItem(10, "playing"), queueItem(11, "ready"), queueItem(12, "ready"))), 0)
        val second = next(first, status(active = true, progress = 0.02, queue = listOf(queueItem(10, "played"), queueItem(11, "playing"), queueItem(12, "ready"))), 1)
        assertEquals("c", second?.target?.key)
        assertEquals("b", second?.fromKey)
    }

    @Test
    fun invalidProgressIsClamped() {
        val ui = next(null, status(active = true, progress = Double.NaN, queue = listOf(queueItem(10, "playing"), queueItem(11, "ready"))), 0)
        assertEquals(0f, ui!!.progress, 0f)
        val over = next(null, status(active = true, progress = 1.7, queue = listOf(queueItem(10, "playing"), queueItem(11, "ready"))), 0)
        assertEquals(1f, over!!.progress, 0f)
    }
}
