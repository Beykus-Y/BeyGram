package com.beykus.beygram.media.bgbeat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgBeatLookaheadTest {

    private fun identity(key: String) = BgBeatAnalysisIdentity(
        accountId = "acc1",
        itemKey = key,
        filePath = "/files/$key.mp3",
        fileLength = 1_000L,
        fileLastModified = 1L
    )

    private val next = identity("next")
    private val current = identity("current")
    private val other = identity("other")

    @Test
    fun neededWhenIdle() {
        assertTrue(bgBeatLookaheadNeeded(next, false, null, emptySet(), current))
    }

    @Test
    fun notNeededWhileCurrentIsAnalysing() {
        assertFalse(bgBeatLookaheadNeeded(next, true, null, emptySet(), current))
    }

    @Test
    fun notNeededWhenSameAsRunning() {
        assertFalse(bgBeatLookaheadNeeded(next, false, next, emptySet(), current))
    }

    @Test
    fun notNeededWhenAlreadyDone() {
        assertFalse(bgBeatLookaheadNeeded(next, false, null, setOf(other, next), current))
    }

    @Test
    fun notNeededWhenEqualsCompletedCurrent() {
        assertFalse(bgBeatLookaheadNeeded(current, false, null, emptySet(), current))
    }

    @Test
    fun notNeededWhenNoNext() {
        assertFalse(bgBeatLookaheadNeeded(null, false, null, emptySet(), current))
    }
}
