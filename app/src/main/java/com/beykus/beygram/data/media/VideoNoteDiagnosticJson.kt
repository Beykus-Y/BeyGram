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
        append(",\"raw_timing\":")
        appendTiming(report.rawTiming)
        append(",\"output_timing\":")
        appendTiming(report.outputTiming)
        append(",\"entries\":[")
        report.entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append(ReportJson.quote(entry))
        }
        append("]}")
    }

    private fun StringBuilder.appendTiming(timing: VideoNoteDiagnosticJsonTiming?) {
        if (timing == null) {
            append("null")
            return
        }
        append('{')
        append("\"frame_count\":").append(timing.frameCount)
        append(",\"p50_gap_ms\":").append(number(timing.p50GapMs))
        append(",\"p95_gap_ms\":").append(number(timing.p95GapMs))
        append(",\"max_gap_ms\":").append(number(timing.maxGapMs))
        append(",\"covered_ms\":").append(number(timing.coveredMs))
        append(",\"timing_source\":").append(timing.timingSource?.let { ReportJson.quote(it) } ?: "null")
        append(",\"expected_interval_ms\":").append(number(timing.expectedIntervalMs))
        append(",\"expected_frames\":").append(timing.expectedFrames?.toString() ?: "null")
        append(",\"estimated_dropped_frames\":").append(timing.estimatedDroppedFrames?.toString() ?: "null")
        append(",\"repeated_timestamps\":").append(timing.repeatedTimestamps?.toString() ?: "null")
        append(",\"reordered_timestamps\":").append(timing.reorderedTimestamps?.toString() ?: "null")
        append(",\"audio_sample_count\":").append(timing.audioSampleCount?.toString() ?: "null")
        append(",\"audio_p50_gap_ms\":").append(number(timing.audioGapP50Ms))
        append(",\"audio_p95_gap_ms\":").append(number(timing.audioGapP95Ms))
        append(",\"audio_max_gap_ms\":").append(number(timing.audioMaxGapMs))
        append(",\"audio_repeated_timestamps\":").append(timing.audioRepeatedTimestamps?.toString() ?: "null")
        append(",\"av_start_offset_ms\":").append(number(timing.avStartOffsetMs))
        append(",\"av_end_offset_ms\":").append(number(timing.avEndOffsetMs))
        append(",\"av_drift_ms\":").append(number(timing.avDriftMs))
        append(",\"av_verdict\":").append(timing.avVerdict?.let { ReportJson.quote(it) } ?: "null")
        append(",\"export_frame_count\":").append(timing.exportFrameCount?.toString() ?: "null")
        append('}')
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
            entries = entries,
            rawTiming = (this["raw_timing"] as? Map<*, *>)?.toTiming(),
            outputTiming = (this["output_timing"] as? Map<*, *>)?.toTiming()
        )
    }

    private fun Map<*, *>.toTiming(): VideoNoteDiagnosticJsonTiming? {
        val frames = number("frame_count")?.toInt() ?: return null
        return VideoNoteDiagnosticJsonTiming(
            frameCount = frames,
            p50GapMs = number("p50_gap_ms"),
            p95GapMs = number("p95_gap_ms"),
            maxGapMs = number("max_gap_ms"),
            coveredMs = number("covered_ms"),
            timingSource = string("timing_source"),
            expectedIntervalMs = number("expected_interval_ms"),
            expectedFrames = number("expected_frames")?.toInt(),
            estimatedDroppedFrames = number("estimated_dropped_frames")?.toInt(),
            repeatedTimestamps = number("repeated_timestamps")?.toInt(),
            reorderedTimestamps = number("reordered_timestamps")?.toInt(),
            audioSampleCount = number("audio_sample_count")?.toInt(),
            audioGapP50Ms = number("audio_p50_gap_ms"),
            audioGapP95Ms = number("audio_p95_gap_ms"),
            audioMaxGapMs = number("audio_max_gap_ms"),
            audioRepeatedTimestamps = number("audio_repeated_timestamps")?.toInt(),
            avStartOffsetMs = number("av_start_offset_ms"),
            avEndOffsetMs = number("av_end_offset_ms"),
            avDriftMs = number("av_drift_ms"),
            avVerdict = string("av_verdict"),
            exportFrameCount = number("export_frame_count")?.toInt()
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
    val entries: List<String>,
    val rawTiming: VideoNoteDiagnosticJsonTiming? = null,
    val outputTiming: VideoNoteDiagnosticJsonTiming? = null
)

internal data class VideoNoteDiagnosticJsonTiming(
    val frameCount: Int,
    val p50GapMs: Double? = null,
    val p95GapMs: Double? = null,
    val maxGapMs: Double? = null,
    val coveredMs: Double? = null,
    val timingSource: String? = null,
    val expectedIntervalMs: Double? = null,
    val expectedFrames: Int? = null,
    val estimatedDroppedFrames: Int? = null,
    val repeatedTimestamps: Int? = null,
    val reorderedTimestamps: Int? = null,
    val audioSampleCount: Int? = null,
    val audioGapP50Ms: Double? = null,
    val audioGapP95Ms: Double? = null,
    val audioMaxGapMs: Double? = null,
    val audioRepeatedTimestamps: Int? = null,
    val avStartOffsetMs: Double? = null,
    val avEndOffsetMs: Double? = null,
    val avDriftMs: Double? = null,
    val avVerdict: String? = null,
    val exportFrameCount: Int? = null
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
        entries = if (compact) emptyList() else entries,
        rawTiming = rawTiming?.toJsonTiming(),
        outputTiming = outputTiming?.toJsonTiming()
    )
}

internal fun VideoNoteStageTiming.toJsonTiming(): VideoNoteDiagnosticJsonTiming = VideoNoteDiagnosticJsonTiming(
    frameCount = frameCount,
    p50GapMs = p50GapMs,
    p95GapMs = p95GapMs,
    maxGapMs = maxGapMs,
    coveredMs = coveredMs,
    timingSource = timingSource?.name,
    expectedIntervalMs = expectedIntervalMs,
    expectedFrames = expectedFrames,
    estimatedDroppedFrames = estimatedDroppedFrames,
    repeatedTimestamps = repeatedTimestamps,
    reorderedTimestamps = reorderedTimestamps,
    audioSampleCount = audioSampleCount,
    audioGapP50Ms = audioGapP50Ms,
    audioGapP95Ms = audioGapP95Ms,
    audioMaxGapMs = audioMaxGapMs,
    audioRepeatedTimestamps = audioRepeatedTimestamps,
    avStartOffsetMs = avStartOffsetMs,
    avEndOffsetMs = avEndOffsetMs,
    avDriftMs = avDriftMs,
    avVerdict = avVerdict?.name,
    exportFrameCount = exportFrameCount
)
