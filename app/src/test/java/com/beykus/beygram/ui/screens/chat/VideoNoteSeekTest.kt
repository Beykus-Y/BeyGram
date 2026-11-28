package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteSeekTest {
    @Test fun anglesStartAtTwelveAndAdvanceClockwise() {
        val center = Offset(100f, 100f)
        assertEquals(0f, videoNoteSeekFraction(Offset(100f, 0f), center), 0.001f)
        assertEquals(0.25f, videoNoteSeekFraction(Offset(200f, 100f), center), 0.001f)
        assertEquals(0.5f, videoNoteSeekFraction(Offset(100f, 200f), center), 0.001f)
        assertEquals(0.75f, videoNoteSeekFraction(Offset(0f, 100f), center), 0.001f)
    }

    @Test fun seamCrossingClampsInsteadOfJumpingToTheOtherEnd() {
        assertEquals(1f, advanceVideoNoteSeek(0.99f, 0.02f, 0.99f), 0.001f)
        assertEquals(0f, advanceVideoNoteSeek(0.01f, 0.98f, 0.01f), 0.001f)
        assertEquals(0.47f, advanceVideoNoteSeek(0.02f, 0.99f, 0.5f), 0.001f)
        assertEquals(0.53f, advanceVideoNoteSeek(0.99f, 0.02f, 0.5f), 0.001f)
    }

    @Test fun expandedRingHasAWiderTouchTargetThanTheStroke() {
        val center = Offset(100f, 100f)
        assertTrue(isOnVideoNoteSeekRing(Offset(100f, 15f), center, 98f, 18f))
        assertFalse(isOnVideoNoteSeekRing(center, center, 98f, 18f))
        assertFalse(isOnVideoNoteSeekRing(Offset.Zero, center, 98f, 18f))
    }
}
