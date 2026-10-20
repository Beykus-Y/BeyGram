package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteHighSpeedProbeTest {
    @Test
    fun `poco style ranges give the variable and the fixed 120`() {
        val picked = VideoNoteHighSpeedProbe.highSpeedCandidates(listOf(30..120, 120..120, 30..240, 240..240))
        assertEquals(listOf(30..120, 120..120), picked)
    }

    @Test
    fun `a native 60 to 120 range is taken and 240 is never measured`() {
        val picked = VideoNoteHighSpeedProbe.highSpeedCandidates(listOf(60..120, 120..120, 240..240))
        assertEquals(listOf(60..120, 120..120), picked)
    }

    @Test
    fun `nothing reaching 60 gives no candidates`() {
        assertEquals(emptyList<IntRange>(), VideoNoteHighSpeedProbe.highSpeedCandidates(listOf(30..30, 15..30)))
    }

    @Test
    fun `fixed 120 is taken even when a fixed 60 is listed first`() {
        val picked = VideoNoteHighSpeedProbe.highSpeedCandidates(listOf(60..60, 30..120, 120..120))
        assertEquals(listOf(30..120, 120..120), picked)
    }

    @Test
    fun `a short burst then a stall is not a sustained 60`() {
        val burst = List(20) { it * 16_666_667L }
        assertFalse(VideoNoteHighSpeedProbe.sustained(measureProbeFrames(burst, emptyList(), 60)))
        val steady = List(120) { it * 16_666_667L }
        assertTrue(VideoNoteHighSpeedProbe.sustained(measureProbeFrames(steady, emptyList(), 60)))
    }
}
