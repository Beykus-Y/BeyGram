package com.beykus.beygram.data.media

import com.beykus.beygram.diagnostics.report.ReportJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteDiagnosticJsonTest {
    private fun report(
        entries: List<String> = listOf("line one", "line two"),
        fallbackReason: String? = "fallback reason"
    ) = VideoNoteDiagnosticJsonReport(
        session = "session-1",
        startedAt = 1_700_000_000_000L,
        status = VideoNoteStatus.SENT,
        targetFps = 60,
        attemptedMaxFps = 30,
        sensorFps = 29.5,
        rawFps = 29.8,
        outputFps = 29.7,
        fpsLimitStage = "POLICY",
        rootCause = "NOT_ADVERTISED_IN_NORMAL_AE",
        confidence = "ESTABLISHED",
        fallbackReason = fallbackReason,
        fpsCause = "NO_NORMAL_60",
        entries = entries
    )

    @Test fun documentRoundTripsThroughTheStableSchema() {
        val document = VideoNoteDiagnosticJsonDocument(
            generatedAt = 1_700_000_000_123L,
            compact = false,
            reports = listOf(report()),
            sections = listOf(VideoNoteDiagnosticJsonSection("camera_capabilities", "survey text"))
        )
        val encoded = VideoNoteDiagnosticJson.encode(document)
        assertEquals(document, VideoNoteDiagnosticJson.decode(encoded))
    }

    @Test fun schemaFieldsAreExplicitAndVersioned() {
        val document = VideoNoteDiagnosticJsonDocument(
            generatedAt = 1L,
            compact = true,
            reports = listOf(report(entries = emptyList()))
        )
        val parsed = ReportJson.parse(VideoNoteDiagnosticJson.encode(document)) as Map<*, *>
        assertEquals(VideoNoteDiagnosticJson.SCHEMA, parsed["schema"])
        assertEquals(VideoNoteDiagnosticJson.VERSION.toLong(), parsed["schema_version"])
        assertEquals(VideoNoteEnvironment.REPORT_SCHEMA_VERSION.toLong(), parsed["report_schema"])
        assertEquals(true, parsed["compact"])
    }

    @Test fun compactReportOmitsDetailButKeepsMetricsAndFallback() {
        val source = VideoNoteDiagnosticReport(
            id = "session-1",
            startedAt = 1_700_000_000_000L,
            status = VideoNoteStatus.RECORDING,
            targetFps = 60,
            entries = listOf("detail one", "detail two"),
            rawFps = 30.0,
            outputFps = 30.0,
            attemptedMaxFps = 30,
            sensorFps = 30.0,
            fallbackReason = "no normal 60",
            fpsCause = "NO_NORMAL_60"
        )
        val compact = VideoNoteDiagnosticJson.decode(source.asJson(compact = true))!!.reports.single()
        assertTrue(compact.entries.isEmpty())
        assertEquals("no normal 60", compact.fallbackReason)
        assertEquals("NO_NORMAL_60", compact.fpsCause)
        assertEquals("POLICY", compact.fpsLimitStage)
        assertEquals(30, compact.attemptedMaxFps)

        val full = VideoNoteDiagnosticJson.decode(source.asJson(compact = false))!!.reports.single()
        assertEquals(listOf("detail one", "detail two"), full.entries)
    }

    @Test fun timingMetricsRoundTripWithoutLosingUnknownValues() {
        val timing = VideoNoteStageTiming(
            frameCount = 120,
            p50GapMs = 16.7,
            p95GapMs = 34.2,
            maxGapMs = 120.0,
            coveredMs = 2_000.0,
            timingSource = VideoNoteTimingSource.FILE_FPS,
            expectedIntervalMs = 16.7,
            expectedFrames = 121,
            estimatedDroppedFrames = 3,
            repeatedTimestamps = 1,
            reorderedTimestamps = 0,
            audioSampleCount = 2_000,
            audioGapP50Ms = 21.3,
            avStartOffsetMs = 12.0,
            avEndOffsetMs = 15.0,
            avDriftMs = 3.0,
            avVerdict = AvSyncVerdict.IN_SYNC,
            exportFrameCount = 120
        )
        val source = VideoNoteDiagnosticReport(
            id = "session-timing",
            startedAt = 1_700_000_000_000L,
            status = VideoNoteStatus.SENT,
            targetFps = 60,
            entries = emptyList(),
            attemptedMaxFps = 60,
            rawFps = 59.8,
            outputFps = 59.5,
            fpsCause = VideoNoteFpsCause.AS_REQUESTED.name,
            rawTiming = timing,
            outputTiming = timing.copy(estimatedDroppedFrames = null)
        )
        val decoded = VideoNoteDiagnosticJson.decode(source.asJson(compact = true))!!.reports.single()
        assertEquals(timing.toJsonTiming(), decoded.rawTiming)
        assertEquals(timing.copy(estimatedDroppedFrames = null).toJsonTiming(), decoded.outputTiming)
        assertTrue(decoded.entries.isEmpty())
    }

    @Test fun unknownSchemaVersionIsRejectedNotGuessed() {
        val encoded = VideoNoteDiagnosticJson.encode(
            VideoNoteDiagnosticJsonDocument(1L, false, listOf(report()))
        )
        assertNull(VideoNoteDiagnosticJson.decode(encoded.replace("\"schema_version\":1", "\"schema_version\":99")))
        assertNull(VideoNoteDiagnosticJson.decode("not json"))
        assertNull(VideoNoteDiagnosticJson.decode("{}"))
    }

    @Test fun redactedJsonStaysParseableAndKeepsNoPaths() {
        val encoded = VideoNoteDiagnosticJson.encode(
            VideoNoteDiagnosticJsonDocument(
                1L, false,
                listOf(report(entries = listOf("file /data/user/0/com.beykus.beygram.dev/files/x.mp4", "mail a@b.de")))
            )
        )
        val safe = VideoNoteReportRedactor.redact(encoded)
        assertFalse(safe.contains("/data/user"))
        assertFalse(safe.contains("a@b.de"))
        val decoded = VideoNoteDiagnosticJson.decode(safe)!!
        assertTrue(decoded.reports.single().entries.first().contains("<path>"))
        assertTrue(decoded.reports.single().entries.last().contains("<email>"))
    }
}
