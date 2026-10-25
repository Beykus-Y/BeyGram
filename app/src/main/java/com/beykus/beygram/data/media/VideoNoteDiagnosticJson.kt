package com.beykus.beygram.data.media

import com.beykus.beygram.diagnostics.report.ReportJson

/**
 * Versioned machine-readable form of the video-note diagnostics (issue #234, §1/§18).
 *
 * The schema is intentionally boring: every future version must keep the old keys readable.
 * It contains only numbers, statuses and report text lines; no frames, audio, account IDs,
 * chat IDs, file paths or message contents. Exported text additionally passes
 * [VideoNoteReportRedactor] before it leaves the app.
 */
internal object VideoNoteDiagnosticJson {
    const val SCHEMA = "beygram.video-note.diagnostics"
    const val VERSION = 1

    fun encode(document: VideoNoteDiagnosticJsonDocument): String = buildString {
        append('{')
        append("\"schema\":").append(ReportJson.quote(SCHEMA))
        append(",\"schema_version\":").append(VERSION)
        append(",\"report_schema\":").append(document.reportSchema)
        append(",\"generated_at\":").append(document.generatedAt)
        append(",\"compact\":").append(document.compact)
        append(",\"reports\":[")
        document.reports.forEachIndexed { index, report ->
            if (index > 0) append(',')
            appendReport(report)
        }
        append("],\"sections\":[")
        document.sections.forEachIndexed { index, section ->
            if (index > 0) append(',')
            append('{')
            append("\"id\":").append(ReportJson.quote(section.id))
            append(",\"text\":").append(ReportJson.quote(section.text))
            append('}')
        }
        append("]}")
    }

    private fun StringBuilder.appendReport(report: VideoNoteDiagnosticJsonReport) {
        append('{')
        append("\"session\":").append(ReportJson.quote(report.session))
        append(",\"started_at\":").append(report.startedAt)
        append(",\"status\":").append(ReportJson.quote(report.status))
        append(",\"target_fps\":").append(report.targetFps)
        append(",\"attempted_max_fps\":").append(report.attemptedMaxFps?.toString() ?: "null")
        append(",\"sensor_fps\":").append(number(report.sensorFps))
        append(",\"raw_fps\":").append(number(report.rawFps))
        append(",\"output_fps\":").append(number(report.outputFps))
        append(",\"fps_limit_stage\":").append(ReportJson.quote(report.fpsLimitStage))
        append(",\"root_cause\":").append(report.rootCause?.let { ReportJson.quote(it) } ?: "null")
        append(",\"confidence\":").append(report.confidence?.let { ReportJson.quote(it) } ?: "null")
        append(",\"fallback_reason\":").append(report.fallbackReason?.let { ReportJson.quote(it) } ?: "null")
        append(",\"fps_cause\":").append(report.fpsCause?.let { ReportJson.quote(it) } ?: "null")
        append(",\"entries\":[")
        report.entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append(ReportJson.quote(entry))
        }
        append("]}")
    }

    private fun number(value: Double?): String =
        value?.takeIf { it.isFinite() }?.toString() ?: "null"

    /** Strict reader used by tests and by future import paths. Null = not this schema/version. */
    fun decode(text: String): VideoNoteDiagnosticJsonDocument? {
        val root = ReportJson.parse(text) as? Map<*, *> ?: return null
        if (root["schema"] != SCHEMA) return null
        if (root.number("schema_version")?.toInt() != VERSION) return null
        val reportSchema = root.number("report_schema")?.toInt() ?: return null
        val generatedAt = root.number("generated_at")?.toLong() ?: return null
        val compact = root["compact"] as? Boolean ?: return null
        val reports = ArrayList<VideoNoteDiagnosticJsonReport>()
        val rawReports = root["reports"] as? List<*> ?: return null
        for (item in rawReports) {
            val map = item as? Map<*, *> ?: return null
            reports += map.toReport() ?: return null
        }
        val sections = ArrayList<VideoNoteDiagnosticJsonSection>()
        val rawSections = root["sections"] as? List<*> ?: return null
        for (item in rawSections) {
            val map = item as? Map<*, *> ?: return null
            sections += VideoNoteDiagnosticJsonSection(
                id = map.string("id") ?: return null,
                text = map.string("text") ?: return null
            )
        }
        return VideoNoteDiagnosticJsonDocument(generatedAt, compact, reports, sections, reportSchema)
    }

    private fun Map<*, *>.toReport(): VideoNoteDiagnosticJsonReport? {
        val entries = ArrayList<String>()
        val rawEntries = this["entries"] as? List<*> ?: return null
        for (item in rawEntries) {
            entries += item as? String ?: return null
        }
        return VideoNoteDiagnosticJsonReport(
            session = string("session") ?: return null,
            startedAt = number("started_at")?.toLong() ?: return null,
            status = string("status") ?: return null,
            targetFps = number("target_fps")?.toInt() ?: return null,
            attemptedMaxFps = number("attempted_max_fps")?.toInt(),
            sensorFps = number("sensor_fps"),
            rawFps = number("raw_fps"),
            outputFps = number("output_fps"),
            fpsLimitStage = string("fps_limit_stage") ?: return null,
            rootCause = string("root_cause"),
            confidence = string("confidence"),
            fallbackReason = string("fallback_reason"),
            fpsCause = string("fps_cause"),
            entries = entries
        )
    }

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.number(key: String): Double? = when (val value = this[key]) {
        is Long -> value.toDouble()
        is Int -> value.toDouble()
        is Double -> value
        else -> null
    }
}

internal data class VideoNoteDiagnosticJsonDocument(
    val generatedAt: Long,
    val compact: Boolean,
    val reports: List<VideoNoteDiagnosticJsonReport>,
    val sections: List<VideoNoteDiagnosticJsonSection> = emptyList(),
    val reportSchema: Int = VideoNoteEnvironment.REPORT_SCHEMA_VERSION
)

internal data class VideoNoteDiagnosticJsonReport(
    val session: String,
    val startedAt: Long,
    val status: String,
    val targetFps: Int,
    val attemptedMaxFps: Int?,
    val sensorFps: Double?,
    val rawFps: Double?,
    val outputFps: Double?,
    val fpsLimitStage: String,
    val rootCause: String?,
    val confidence: String?,
    val fallbackReason: String?,
    val fpsCause: String?,
    val entries: List<String>
)

internal data class VideoNoteDiagnosticJsonSection(val id: String, val text: String)

/** Builds the stable JSON view of one recording report; [compact] omits the detailed lines. */
internal fun VideoNoteDiagnosticReport.toJsonReport(compact: Boolean): VideoNoteDiagnosticJsonReport {
    val diagnosis = if (sensorFps != null || rawFps != null || outputFps != null) {
        diagnoseVideoNoteFps(VideoNoteStageEvidence(
            targetFps = attemptedMaxFps ?: targetFps,
            sensorFps = sensorFps,
            rawFps = rawFps,
            exportedFps = outputFps
        ))
    } else null
    return VideoNoteDiagnosticJsonReport(
        session = id,
        startedAt = startedAt,
        status = status,
        targetFps = targetFps,
        attemptedMaxFps = attemptedMaxFps,
        sensorFps = sensorFps?.takeIf { it.isFinite() },
        rawFps = rawFps?.takeIf { it.isFinite() },
        outputFps = outputFps?.takeIf { it.isFinite() },
        fpsLimitStage = videoNoteFpsLimitStage(targetFps, attemptedMaxFps, rawFps, outputFps).name,
        rootCause = diagnosis?.cause?.name,
        confidence = diagnosis?.confidence?.name,
        fallbackReason = fallbackReason,
        fpsCause = fpsCause,
        entries = if (compact) emptyList() else entries
    )
}
