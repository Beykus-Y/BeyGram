package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BgMixRetargetPlanTest {

    private val item = PlaybackItem("Audio:8", MediaKind.Audio, "/8.mp3", "Eight", 10)

    @Test
    fun retargetWithoutASessionIsRefused() {
        val driver = BgMixQueueDriver(File(System.getProperty("java.io.tmpdir") ?: "."))
        assertFalse(driver.retarget(listOf(item), item.key))
        assertTrue(driver.queueItems.isEmpty())
    }

    @Test
    fun appendingAtTheTailKeepsTheNativeWindow() {
        // Native queue holds a and b; c is appended at the end of the logical queue.
        val plan = bgMixRetargetPlan(
            oldKeys = listOf("a", "b"), oldCurrent = 0, oldAppendedUntil = 2,
            newKeys = listOf("a", "b", "c"), newCurrent = 0
        )
        assertEquals(BgMixRetargetPlan(emptyList(), 2), plan)
    }

    @Test
    fun playNextBeforeAnAppendedSuccessorRemovesThatSuccessor() {
        // Native queue holds a and b; x is played next, so b no longer follows a.
        val plan = bgMixRetargetPlan(
            oldKeys = listOf("a", "b", "c"), oldCurrent = 0, oldAppendedUntil = 2,
            newKeys = listOf("a", "x", "b", "c"), newCurrent = 0
        )
        assertEquals(BgMixRetargetPlan(listOf("b"), 1), plan)
    }

    @Test
    fun removingTheAppendedSuccessorRemovesIt() {
        val plan = bgMixRetargetPlan(
            oldKeys = listOf("a", "b", "c", "d"), oldCurrent = 0, oldAppendedUntil = 2,
            newKeys = listOf("a", "c", "d"), newCurrent = 0
        )
        assertEquals(BgMixRetargetPlan(listOf("b"), 1), plan)
    }

    @Test
    fun movingItemsBeyondTheNativeWindowLeavesTheNativeQueueAlone() {
        val plan = bgMixRetargetPlan(
            oldKeys = listOf("a", "b", "c", "d", "e"), oldCurrent = 0, oldAppendedUntil = 2,
            newKeys = listOf("a", "b", "d", "c", "e"), newCurrent = 0
        )
        assertEquals(BgMixRetargetPlan(emptyList(), 2), plan)
    }

    @Test
    fun playingTrackMovedToAnotherIndexKeepsItsKeyAndTheSuccessor() {
        // a plays and b is native; a moves behind c, and b still follows it.
        val plan = bgMixRetargetPlan(
            oldKeys = listOf("a", "b", "c", "d"), oldCurrent = 0, oldAppendedUntil = 2,
            newKeys = listOf("c", "a", "b", "d"), newCurrent = 1
        )
        assertEquals(BgMixRetargetPlan(emptyList(), 3), plan)
    }

    @Test
    fun playingTrackMissingFromEitherQueueIsNull() {
        val missingFromNew = bgMixRetargetPlan(
            oldKeys = listOf("a", "b"), oldCurrent = 0, oldAppendedUntil = 2,
            newKeys = listOf("b", "c"), newCurrent = 0
        )
        assertNull(missingFromNew)
        val missingFromOld = bgMixRetargetPlan(
            oldKeys = listOf("b", "c"), oldCurrent = 0, oldAppendedUntil = 2,
            newKeys = listOf("a", "b"), newCurrent = 1
        )
        assertNull(missingFromOld)
        assertNull(
            bgMixRetargetPlan(
                oldKeys = listOf("a"), oldCurrent = 1, oldAppendedUntil = 1,
                newKeys = listOf("a"), newCurrent = 0
            )
        )
    }

    @Test
    fun nothingAppendedYetRemovesNothing() {
        // Only the playing track is native; the new successor joins later through appendReady.
        val plan = bgMixRetargetPlan(
            oldKeys = listOf("a", "b"), oldCurrent = 0, oldAppendedUntil = 1,
            newKeys = listOf("a", "x", "b"), newCurrent = 0
        )
        assertEquals(BgMixRetargetPlan(emptyList(), 1), plan)
    }
}
