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

    @Test fun failedAudioInspectionLeavesAvSnapshotUnknown() {
        val stage = videoNoteStageTiming(
            frameCount = 30,
            timing = analyzeVideoNoteFrameTiming((0 until 30).map { it * 33_333L }, declaredFps = 30),
            audio = null,
            sync = null
        )
        assertNull(stage.audioSampleCount)
        assertNull(stage.avVerdict)
        assertNull(stage.avStartOffsetMs)
        assertNull(stage.avDriftMs)
    }

    @Test fun stageTimingCarriesAudioGapsAndDrift() {
        val audio = VideoNoteAudioStats(
            sampleCount = 5,
            firstUs = 0L,
            lastUs = 1_000_000L,
            sampleRate = 48_000,
            channels = 1,
            bitrateBps = 128_000,
            timing = analyzeVideoNoteFrameTiming(listOf(0L, 21_333L, 42_666L, 64_000L, 1_000_000L))
        )
        val sync = avSyncOf(0L, 1_000_000L, audio)!!
        val stage = videoNoteStageTiming(
            frameCount = 30,
            timing = analyzeVideoNoteFrameTiming((0 until 30).map { it * 33_333L }, declaredFps = 30),
            audio = audio,
            sync = sync
        )
        assertEquals(5, stage.audioSampleCount)
        assertEquals(21.33, stage.audioGapP50Ms!!, 0.01)
        assertEquals(sync.driftMs, stage.avDriftMs!!, 0.001)
        assertEquals(AvSyncVerdict.IN_SYNC, stage.avVerdict)
    }
}
