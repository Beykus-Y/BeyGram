package com.beykus.beygram.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabTimelineTest {
    @Test
    fun `outgoing screen starts leaving only after the incoming one is opaque`() {
        val timeline = TabTimeline(200)
        assertEquals(140, timeline.enterMillis)
        assertEquals(60, timeline.exitMillis)
        assertEquals(200, timeline.enterMillis + timeline.exitMillis)
    }

    @Test
    fun `a very short duration still gives both phases at least one millisecond`() {
        val timeline = TabTimeline(1)
        assertTrue(timeline.enterMillis >= 1)
        assertTrue(timeline.exitMillis >= 1)
    }
}
