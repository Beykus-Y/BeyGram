package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoNoteAvSyncTest {
    private fun audio(first: Long, last: Long) = VideoNoteAudioStats(100, first, last, 48_000, 1, 128_000)

    @Test fun matchingStartAndEndIsInSync() {
        val sync = avSyncOf(0L, 10_000_000L, audio(10_000L, 9_990_000L))!!
        assertEquals(AvSyncVerdict.IN_SYNC, sync.verdict)
        assertEquals(10.0, sync.startOffsetMs, 0.01)
        assertEquals(-10.0, sync.endOffsetMs, 0.01)
    }

    @Test fun lateAudioFromTheStartIsAStartOffset() {
        val sync = avSyncOf(0L, 10_000_000L, audio(250_000L, 10_250_000L))!!
        assertEquals(AvSyncVerdict.START_OFFSET, sync.verdict)
    }

    @Test fun offsetGrowingOverTheClipIsDrift() {
        val sync = avSyncOf(0L, 10_000_000L, audio(0L, 9_700_000L))!!
        assertEquals(AvSyncVerdict.DRIFT, sync.verdict)
        assertEquals(-300.0, sync.driftMs, 0.01)
    }

    @Test fun shortClipIsNotJudgedForDrift() {
        val sync = avSyncOf(0L, 500_000L, audio(0L, 300_000L))!!
        assertEquals(AvSyncVerdict.IN_SYNC, sync.verdict)
    }

    @Test fun missingAudioIsReportedNotJudged() {
        assertEquals(AvSyncVerdict.NO_AUDIO, avSyncOf(0L, 1_000_000L, null)!!.verdict)
    }

    @Test fun brokenVideoRangeGivesNoAnswer() {
        assertNull(avSyncOf(5L, 1L, audio(0L, 1L)))
    }
}
