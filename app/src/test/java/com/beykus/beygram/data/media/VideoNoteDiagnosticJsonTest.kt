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
