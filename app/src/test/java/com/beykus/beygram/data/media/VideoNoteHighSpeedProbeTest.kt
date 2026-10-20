package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
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
}
