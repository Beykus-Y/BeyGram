package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteFpsProbePlanTest {
    private val fhd = ProbeSize(1920, 1080)
    private val hd = ProbeSize(1280, 720)
    private val vga = ProbeSize(640, 480)
    private val uhd8k = ProbeSize(7680, 4320)
    private val ns60 = 16_666_666L

    /** Samsung M21 front: normal AE ranges end at 30, stream ceiling ≈60, no high-speed. */
    private val m21Front = ProbeCameraCaps(
        cameraId = "1", facing = ProbeFacing.FRONT,
        normalRanges = listOf(15..30, 15..15, 20..20, 24..24, 30..30, 10..30, 15..20, 8..30),
        streamSizes = listOf(fhd, hd, vga),
        minFrameDurationNs = mapOf(fhd to ns60, hd to ns60, vga to ns60)
    )

    private val capable60Back = ProbeCameraCaps(
        cameraId = "0", facing = ProbeFacing.BACK,
        normalRanges = listOf(15..30, 30..30, 15..60, 60..60),
        streamSizes = listOf(uhd8k, fhd, hd),
        minFrameDurationNs = mapOf(fhd to ns60, hd to ns60, uhd8k to 33_333_333L),
        highSpeed = mapOf(hd to listOf(120..120, 240..240))
    )

    @Test fun thirtyOnlyLensOpens30AndListsUnadvertised60() {
        val plan = planVideoNoteProbe(listOf(m21Front), ProbeScope.FRONT)
        val opened = plan.filter { it.status == ProbeModeStatus.PLANNED }
        assertEquals(listOf(30..30, 30..30, 30..30), opened.map { it.range })
        val sixty = plan.filter { it.targetFps == 60 }
        assertEquals(3, sixty.size)
        assertTrue(sixty.all { it.status == ProbeModeStatus.NOT_ADVERTISED && it.range == null })
    }

    @Test fun capableLensOpensFixed60PreferredOverVariable() {
        val plan = planVideoNoteProbe(listOf(capable60Back), ProbeScope.BACK)
        val sixty = plan.filter { it.status == ProbeModeStatus.PLANNED && it.targetFps == 60 }
        assertEquals(listOf(fhd, hd), sixty.map { it.size })
        assertTrue(sixty.all { it.range == 60..60 })
        assertTrue(plan.none { it.status == ProbeModeStatus.NOT_ADVERTISED })
    }

    @Test fun unadvertisedOrTemplateOnlySizesAreNeverOpened() {
        val plan = planVideoNoteProbe(listOf(capable60Back), ProbeScope.ALL)
        assertTrue(plan.none { it.size == uhd8k })
        assertTrue(plan.none { it.size == ProbeSize(720, 720) || it.size == ProbeSize(640, 640) })
    }

    @Test fun squareSizesAreCheckedWhenAdvertised() {
        val square = ProbeSize(720, 720)
        val caps = m21Front.copy(streamSizes = listOf(square), minFrameDurationNs = mapOf(square to ns60))
        val plan = planVideoNoteProbe(listOf(caps), ProbeScope.FRONT)
        assertTrue(plan.any { it.size == square && it.status == ProbeModeStatus.PLANNED })
    }

    @Test fun highSpeedIsListedSeparatelyAndNeverOpened() {
        val plan = planVideoNoteProbe(listOf(capable60Back), ProbeScope.BACK)
        assertTrue(plan.none { it.status == ProbeModeStatus.PLANNED && it.targetFps > 60 })
        val onlyHighSpeed = capable60Back.copy(normalRanges = listOf(15..30, 30..30))
        val hdSixty = planVideoNoteProbe(listOf(onlyHighSpeed), ProbeScope.BACK)
            .single { it.size == hd && it.targetFps == 60 }
        assertEquals(ProbeModeStatus.HIGH_SPEED_ONLY, hdSixty.status)
        assertNull(hdSixty.range)
    }

    @Test fun slowStreamIsNotCompatibleWith60() {
        val slow = capable60Back.copy(minFrameDurationNs = mapOf(fhd to 33_333_333L, hd to ns60))
        val row = planVideoNoteProbe(listOf(slow), ProbeScope.BACK).single { it.size == fhd && it.targetFps == 60 }
        assertEquals(ProbeModeStatus.STREAM_TOO_SLOW, row.status)
    }

    @Test fun scopeFiltersLenses() {
        val plan = planVideoNoteProbe(listOf(m21Front, capable60Back), ProbeScope.FRONT)
        assertTrue(plan.all { it.cameraId == "1" })
    }

    @Test fun openedModesAreBoundedPerRun() {
        val many = (0 until 10).map { capable60Back.copy(cameraId = "$it") }
        val plan = planVideoNoteProbe(many, ProbeScope.ALL)
        assertEquals(PROBE_MAX_OPENED_MODES, plan.count { it.status == ProbeModeStatus.PLANNED })
        assertTrue(plan.any { it.status == ProbeModeStatus.SKIPPED_LIMIT })
    }

    @Test fun measuresGapsDropsAndRepeatedTimestamps() {
        val interval = 16_666_667L
        val ts = (0 until 60).map { it * interval }.toMutableList()
        ts.removeAt(30) // one dropped frame -> one double-length gap
        ts.add(ts.last()) // a repeated timestamp
        val stats = measureProbeFrames(ts, List(ts.size) { 8_000_000L }, 60)
        assertEquals(1, stats.longGaps)
        assertEquals(1, stats.nonIncreasing)
        assertEquals(16.7, stats.p50GapMs!!, 0.1)
        assertEquals(33.3, stats.maxGapMs!!, 0.1)
        assertEquals(8.0, stats.medianExposureMs!!, 0.01)
        assertEquals(ProbeVerdict.REACHED, classifyProbe(stats, 60, 0))
    }

    @Test fun duplicatedTimestampsDoNotInflateTheRate() {
        // A HAL repeating every 30 FPS timestamp once must not read as 60 FPS.
        val ts = (0 until 60).flatMap { listOf(it * 33_333_333L, it * 33_333_333L) }
        val stats = measureProbeFrames(ts, emptyList(), 60)
        assertEquals(30.0, stats.measuredFps!!, 0.1)
        assertEquals(60, stats.nonIncreasing)
        assertEquals(ProbeVerdict.BELOW_UNEXPLAINED, classifyProbe(stats, 60, null))
    }

    @Test fun thirtyDeliveredFor60WithLongExposureIsLowLight() {
        val ts = (0 until 60).map { it * 33_333_333L }
        val stats = measureProbeFrames(ts, List(60) { 33_000_000L }, 60)
        assertEquals(30.0, stats.measuredFps!!, 0.1)
        assertEquals(ProbeVerdict.LOW_LIGHT, classifyProbe(stats, 60, 0))
    }

    @Test fun shortExposureButHotPhoneIsThermal() {
        val ts = (0 until 60).map { it * 33_333_333L }
        val stats = measureProbeFrames(ts, List(60) { 5_000_000L }, 60)
        assertEquals(ProbeVerdict.THERMAL, classifyProbe(stats, 60, PROBE_THERMAL_SEVERE))
        assertEquals(ProbeVerdict.BELOW_UNEXPLAINED, classifyProbe(stats, 60, 1))
        assertEquals(ProbeVerdict.BELOW_UNEXPLAINED, classifyProbe(stats, 60, null))
    }

    @Test fun noFramesIsNotReportedAsZeroFps() {
        assertEquals(ProbeVerdict.NO_FRAMES, classifyProbe(measureProbeFrames(emptyList(), emptyList(), 30), 30, 0))
        assertEquals(ProbeVerdict.NO_FRAMES, classifyProbe(measureProbeFrames(listOf(1L, 2L), emptyList(), 30), 30, 0))
    }
}
