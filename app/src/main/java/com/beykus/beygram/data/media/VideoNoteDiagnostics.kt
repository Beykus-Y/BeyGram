package com.beykus.beygram.data.media

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import android.util.Log
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.AppLocale
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Status keys saved in a report. The settings screen turns them into localized labels. */
internal object VideoNoteStatus {
    const val QUEUED = "queued"
    const val PREVIEW = "preview"
    const val READY = "ready"
    const val PROCESSING_FAILED = "processing_failed"
    const val SENDING = "sending"
    const val SENT = "sent"
    const val SEND_FAILED = "send_failed"
    const val CANCELLED = "cancelled"
    const val RECORDING = "recording"
    const val PROCESSING = "processing"
    const val RECORDING_FAILED = "recording_failed"
    const val DISCARDED = "discarded"
}

/**
 * On-device, opt-in video-note diagnostics. Never store media, message contents, chat IDs,
 * phone numbers or file paths. Reports live in noBackupFilesDir and are shared only by user action.
 */
data class VideoNoteDiagnosticReport(
    val id: String,
    val startedAt: Long,
    val status: String,
    val targetFps: Int,
    val entries: List<String>,
    val rawFps: Double? = null,
    val outputFps: Double? = null,
    /** Upper bound of the range actually handed to the camera; null = camera default. */
    val attemptedMaxFps: Int? = null,
    /** Frame rate of the sensor results received while recording; null = not collected. */
    val sensorFps: Double? = null,
    /** Localized explanation of the FPS fallback; survives app restarts with the report. */
    val fallbackReason: String? = null,
    /** Stable enum key of [VideoNoteFpsCause]; safe for machine-readable reports. */
    val fpsCause: String? = null,
    /** Timestamp/A/V metrics of the RAW recording; null when the stage was not measured. */
    val rawTiming: VideoNoteStageTiming? = null,
    /** Timestamp/A/V metrics of the exported MP4; null when the stage was not measured. */
    val outputTiming: VideoNoteStageTiming? = null
) {
    /** [statusLabel] turns a [VideoNoteStatus] key into the localized label shown in the list. */
    fun asText(context: Context, statusLabel: (String) -> String, compact: Boolean = false): String = buildString {
        fun fps(value: Double?) = value?.let { String.format(Locale.US, "%.2f", it) }
            ?: context.getString(R.string.loss_report_not_measured)
        val stage = videoNoteFpsLimitStage(targetFps, attemptedMaxFps, rawFps, outputFps)
        appendLine(context.getString(R.string.vn_report_heading))
        appendLine(context.getString(R.string.vn_report_session, id))
        appendLine(context.getString(R.string.vn_report_time, Date(startedAt).toString()))
        appendLine(context.getString(R.string.vn_report_status, statusLabel(status)))
        appendLine(context.getString(R.string.vn_report_target, targetFps,
            attemptedMaxFps?.toString() ?: context.getString(R.string.camera_report_range_default)))
        appendLine(context.getString(R.string.vn_report_raw, fps(rawFps)))
        appendLine(context.getString(R.string.vn_report_final, fps(outputFps)))
        appendLine(context.getString(R.string.vn_report_stage, fpsLimitStageText(context, stage)))
        rawTiming?.let {
            appendLine(context.getString(R.string.vn_report_raw_timing))
            stageTimingLines(context, it).forEach(::appendLine)
        }
        outputTiming?.let {
            appendLine(context.getString(R.string.vn_report_output_timing))
            stageTimingLines(context, it).forEach(::appendLine)
        }
        if (compact) {
            appendLine(context.getString(R.string.camera_report_device,
                Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT))
            fallbackReason?.let { appendLine(context.getString(R.string.vn_report_fallback, it)) }
        }
        if (fpsCause != null) {
            appendLine(context.getString(R.string.vn_report_advice,
                videoNoteQualityAdvice(context, fpsCause, attemptedMaxFps ?: targetFps, stage)))
        }
        if (sensorFps != null || rawFps != null || outputFps != null) {
            appendLine(rootCauseText(context, diagnoseVideoNoteFps(VideoNoteStageEvidence(
                targetFps = attemptedMaxFps ?: targetFps, sensorFps = sensorFps, rawFps = rawFps, exportedFps = outputFps))))
        }
        if (!compact) {
            appendLine()
            entries.forEach { appendLine(it) }
        }
    }

    /** Versioned machine-readable report; [compact] omits the detailed text lines. */
    fun asJson(compact: Boolean = false): String = VideoNoteDiagnosticJson.encode(
        VideoNoteDiagnosticJsonDocument(
            generatedAt = System.currentTimeMillis(),
            compact = compact,
            reports = listOf(toJsonReport(compact))
        )
    )
}

object VideoNoteDiagnostics {
    private const val TAG = "VideoNoteDiagnostics"
    private const val MAX_REPORTS = 20
    private const val MAX_ENTRIES = 70
    private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    private const val FILE_NAME = "video-note-diagnostics.json"

    private val mutableReports = MutableStateFlow<List<VideoNoteDiagnosticReport>>(emptyList())
    val reports: StateFlow<List<VideoNoteDiagnosticReport>> = mutableReports.asStateFlow()
    private var file: AtomicFile? = null
    private var appContext: Context? = null
    // The application context has no in-app locale below API 33.
    private val localContext: Context? get() = appContext?.let(AppLocale::context)

    /** Localized text for entries written from places that have no Context at hand. */
    internal fun text(id: Int, vararg args: Any): String =
        localContext?.getString(id, *args) ?: id.toString()

    @Synchronized
    fun initialize(context: Context) {
        if (file != null) return
        appContext = context.applicationContext
        file = AtomicFile(File(context.applicationContext.noBackupFilesDir, FILE_NAME))
        val saved = runCatching {
            val storedFile = file ?: return@runCatching emptyList()
            if (!storedFile.baseFile.exists()) return@runCatching emptyList()
            val list = JSONArray(storedFile.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            (0 until list.length()).mapNotNull { i ->
                val json = list.optJSONObject(i) ?: return@mapNotNull null
                val rows = json.optJSONArray("entries") ?: JSONArray()
                VideoNoteDiagnosticReport(
                    id = json.getString("id"),
                    startedAt = json.getLong("startedAt"),
                    status = json.optString("status", "unknown"),
                    targetFps = json.optInt("targetFps", 30),
                    entries = (0 until rows.length()).map { rows.optString(it) },
                    rawFps = if (json.has("rawFps")) json.getDouble("rawFps") else null,
                    outputFps = if (json.has("outputFps")) json.getDouble("outputFps") else null,
                    attemptedMaxFps = if (json.has("attemptedMaxFps")) json.getInt("attemptedMaxFps") else null,
                    sensorFps = if (json.has("sensorFps")) json.getDouble("sensorFps") else null,
                    fallbackReason = if (json.has("fallbackReason")) json.getString("fallbackReason") else null,
                    fpsCause = if (json.has("fpsCause")) json.getString("fpsCause") else null,
                    rawTiming = json.optJSONObject("rawTiming")?.toStageTiming(),
                    outputTiming = json.optJSONObject("outputTiming")?.toStageTiming()
                )
            }
        }.onFailure { Log.w(TAG, "Cannot read stored video diagnostics: ${it.javaClass.simpleName}") }
            .getOrDefault(emptyList())
        val retained = trim(saved)
        mutableReports.value = retained
        // Expired reports are removed from disk as soon as diagnostics are opened again.
        if (retained.size != saved.size) persist(retained)
    }

    @Synchronized
    internal fun begin(
        context: Context,
        sessionId: String,
        settings: VideoMessageSettings,
        lens: String,
        decision: VideoNoteFpsDecision?,
        previewRange: IntRange?,
        supportedRanges: String,
        captureBitrate: Int
    ) {
        if (!settings.diagnosticsEnabled) return
        initialize(context)
        val now = System.currentTimeMillis()
        val default = context.getString(R.string.camera_report_range_default)
        val start = listOfNotNull(
            context.getString(R.string.camera_report_device, Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT),
            context.getString(R.string.vn_entry_preset, settings.preset.name, settings.requestedFrameRateRange().toText()),
            context.getString(R.string.vn_entry_lens, lens, decision?.selected?.toText() ?: default,
                previewRange?.toText() ?: default),
            decision?.let { context.getString(R.string.vn_entry_decision, fpsCauseText(context, it.cause),
                it.normalCandidates.joinToString { range -> range.toText() }.ifBlank { "—" },
                yesNoUnknown(context, it.encoderSupports60)) },
            context.getString(R.string.vn_entry_advertised, supportedRanges),
            context.getString(R.string.vn_entry_bitrate, captureBitrate, yesNoUnknown(context, settings.mirrorFrontCamera)),
            context.getString(R.string.vn_entry_start_requested)
        ).map { stamp(it) }
        val report = VideoNoteDiagnosticReport(
            id = sessionId,
            startedAt = now,
            status = VideoNoteStatus.RECORDING,
            targetFps = settings.requestedFrameRateRange().last,
            entries = start,
            attemptedMaxFps = decision?.selected?.last,
            fallbackReason = decision?.let { fpsCauseText(context, it.cause) },
            fpsCause = decision?.cause?.name
        )
        persist(listOf(report) + mutableReports.value.filterNot { it.id == sessionId })
    }

    @Synchronized
    fun hasReport(id: String): Boolean = mutableReports.value.any { it.id == id }

    @Synchronized
    fun append(id: String, message: String, status: String? = null) {
        if (!hasReport(id)) return
        modify(id) { old ->
            old.copy(status = status ?: old.status, entries = (old.entries + stamp(message)).takeLast(MAX_ENTRIES))
        }
    }

    /** Sensor results seen during the recording itself; a null [stats] is logged as "no results", not "no frames". */
    internal fun recordSensor(
        id: String,
        stats: ProbeFrameStats?,
        truncated: Boolean = false,
        ae: AeCaptureSummary? = null
    ) {
        if (!hasReport(id)) return
        if (stats == null && ae == null) {
            append(id, text(R.string.vn_entry_sensor_none))
            return
        }
        fun ms(v: Double?) = v?.let { String.format(Locale.US, "%.1f", it) } ?: "?"
        val lines = mutableListOf<String>()
        if (stats != null) {
            lines += stamp(text(R.string.vn_entry_sensor, stats.frames,
                stats.measuredFps?.let { String.format(Locale.US, "%.2f", it) } ?: "?",
                ms(stats.p50GapMs), ms(stats.p95GapMs), ms(stats.maxGapMs), stats.longGaps,
                ms(stats.medianExposureMs), (stats.coverage * 100).toInt()))
        }
        ae?.let { evidence ->
            if (evidence.dominantTargetRange != null || evidence.frameDurationP50Ms != null ||
                evidence.aeStates.isNotEmpty()) {
                val states = evidence.aeStates.entries.joinToString { (state, count) ->
                    val label = localContext?.let { context -> aeStateLabel(context, state) } ?: state.toString()
                    "$label $count"
                }
                lines += stamp(text(R.string.vn_entry_sensor_ae,
                    evidence.dominantTargetRange?.toText() ?: "—",
                    evidence.distinctTargetRanges.size,
                    evidence.distinctTargetRanges.joinToString { it.toText() }.ifBlank { "—" },
                    ms(evidence.frameDurationP50Ms), ms(evidence.frameDurationP95Ms),
                    evidence.frameDurationOverBudget, states.ifBlank { "—" }))
            }
            if (evidence.videoStabilizationMode != null || evidence.opticalStabilizationMode != null) {
                val video = evidence.videoStabilizationMode
                    ?.let { value -> localContext?.let { videoStabilizationLabel(it, value) } }
                val optical = evidence.opticalStabilizationMode
                    ?.let { value -> localContext?.let { opticalStabilizationLabel(it, value) } }
                lines += stamp(text(R.string.vn_entry_sensor_stabilization,
                    video ?: "—", optical ?: "—"))
            }
        }
        if (truncated) lines += stamp(text(R.string.vn_entry_sensor_truncated))
        modify(id) { old ->
            old.copy(
                sensorFps = stats?.measuredFps ?: old.sensorFps,
                entries = (old.entries + lines).takeLast(MAX_ENTRIES)
            )
        }
    }

    /** Crop region and zoom the camera applied: input for comparing the field of view between sizes. */
    fun recordCrop(
        id: String, crop: String?, zoom: Float?,
        firstCrop: String? = null, firstZoom: Float? = null, changed: Boolean = false
    ) {
        if (crop == null && firstCrop == null) return
        val formattedZoom: (Float?) -> String = { value ->
            value?.let { String.format(Locale.US, "%.2f", it) } ?: "?"
        }
        if (crop != null) append(id, text(R.string.vn_entry_crop, crop, formattedZoom(zoom)))
        append(id, text(R.string.vn_entry_fov_observed,
            firstCrop ?: "?", formattedZoom(firstZoom), crop ?: "?", formattedZoom(zoom),
            text(if (changed) R.string.vn_entry_fov_changed else R.string.vn_entry_fov_unchanged)))
    }

    /**
     * Reads encoded sample timestamps and audio timing of one finalized file. [exportFrameCount] is
     * what Media3 Transformer reported for this export; it is compared with the container sample
     * count, never presented as a visual-quality result.
     */
    suspend fun inspect(
        id: String,
        stage: String,
        file: File,
        output: Boolean = false,
        exportFrameCount: Int? = null
    ) {
        if (!hasReport(id)) return
        // The recording's explicit attempted rate is a valid baseline when the container omits
        // KEY_FRAME_RATE. It is labelled TARGET_FPS in the report, never presented as file metadata.
        val targetFps = mutableReports.value.firstOrNull { it.id == id }
            ?.let { it.attemptedMaxFps ?: it.targetFps }
        // Run after the camera/Transformer has finalized the MP4; only encoded sample PTS are read.
        val result = withContext(Dispatchers.IO) {
            runCatching { analyzeVideoNoteFrameSamples(file, targetFps) }
        }
        val audioResult = withContext(Dispatchers.IO) { runCatching { analyzeVideoNoteAudio(file) } }
        val audio = audioResult.getOrNull()
        result.fold(
            onSuccess = { data ->
                // A failed/unreadable audio inspection is not proof that the file has no audio.
                // Keep the persisted A/V snapshot null instead of storing NO_AUDIO with zero offsets.
                val sync = if (audioResult.isFailure || audio?.sampleCount == 0) {
                    null
                } else avSyncOf(data.firstVideoUs, data.lastVideoUs, audio)
                val timing = videoNoteStageTiming(data.frameCount, data.timing, audio, sync, exportFrameCount)
                val avLine = when {
                    audioResult.isFailure -> text(R.string.vn_entry_av_failed,
                        audioResult.exceptionOrNull()?.javaClass?.simpleName ?: "?")
                    audio == null -> text(R.string.vn_entry_av_none)
                    audio.sampleCount == 0 -> text(R.string.vn_entry_av_unreadable)
                    sync == null -> text(R.string.vn_entry_av_unmeasured)
                    sync.verdict == AvSyncVerdict.NO_AUDIO -> text(R.string.vn_entry_av_none)
                    else -> {
                        fun orUnknown(v: Number?, divisor: Int = 1) =
                            v?.let { (it.toLong() / divisor).toString() } ?: "?"
                        text(R.string.vn_entry_av, orUnknown(audio.sampleRate), orUnknown(audio.channels),
                            orUnknown(audio.bitrateBps, 1000),
                            String.format(Locale.US, "%.0f", sync.startOffsetMs),
                            String.format(Locale.US, "%.0f", sync.endOffsetMs),
                            String.format(Locale.US, "%.0f", sync.driftMs),
                            text(when (sync.verdict) {
                                AvSyncVerdict.IN_SYNC, AvSyncVerdict.NO_AUDIO -> R.string.vn_av_in_sync
                                AvSyncVerdict.START_OFFSET -> R.string.vn_av_start_offset
                                AvSyncVerdict.DRIFT -> R.string.vn_av_drift
                            }))
                    }
                }
                modify(id) { old ->
                    val raw = if (output) old.rawFps else data.measuredFps
                    val final = if (output) data.measuredFps else old.outputFps
                    val finding = if (output) videoNoteFpsLimitStage(old.targetFps, old.attemptedMaxFps, raw, final) else null
                    val context = localContext ?: appContext
                    val detail = listOf(stage + ": " + (context?.let(data::summary) ?: data.frameCount)) +
                        (context?.let { stageTimingLines(it, timing) } ?: emptyList()) +
                        listOf(avLine) +
                        listOfNotNull(finding?.let { text(R.string.vn_entry_finding, stageText(it)) })
                    old.copy(
                        rawFps = raw,
                        outputFps = final,
                        rawTiming = if (output) old.rawTiming else timing,
                        outputTiming = if (output) timing else old.outputTiming,
                        entries = (old.entries + detail.map(::stamp)).takeLast(MAX_ENTRIES)
                    )
                }
            },
            onFailure = { append(id, text(R.string.vn_entry_measure_failed, stage, it.javaClass.simpleName)) }
        )
    }

    @Synchronized
    fun clear() {
        file?.delete()
        mutableReports.value = emptyList()
    }

    @Synchronized
    private fun modify(id: String, change: (VideoNoteDiagnosticReport) -> VideoNoteDiagnosticReport) {
        val current = mutableReports.value
        if (current.none { it.id == id }) return
        persist(current.map { if (it.id == id) change(it) else it })
    }

    @Synchronized
    private fun persist(current: List<VideoNoteDiagnosticReport>) {
        val bounded = trim(current)
        mutableReports.value = bounded
        val target = file ?: return
        runCatching {
            val array = JSONArray()
            bounded.forEach { report ->
                array.put(JSONObject().apply {
                    put("id", report.id)
                    put("startedAt", report.startedAt)
                    put("status", report.status)
                    put("targetFps", report.targetFps)
                    put("entries", JSONArray(report.entries))
                    report.rawFps?.let { put("rawFps", it) }
                    report.outputFps?.let { put("outputFps", it) }
                    report.attemptedMaxFps?.let { put("attemptedMaxFps", it) }
                    report.sensorFps?.let { put("sensorFps", it) }
                    report.fallbackReason?.let { put("fallbackReason", it) }
                    report.fpsCause?.let { put("fpsCause", it) }
                    report.rawTiming?.let { put("rawTiming", it.toJson()) }
                    report.outputTiming?.let { put("outputTiming", it.toJson()) }
                })
            }
            val bytes = array.toString().toByteArray(Charsets.UTF_8)
            val stream = target.startWrite()
            try {
                stream.write(bytes)
                target.finishWrite(stream)
            } catch (e: Exception) {
                target.failWrite(stream)
                throw e
            }
        }.onFailure { Log.w(TAG, "Cannot save video diagnostics: ${it.javaClass.simpleName}") }
    }

    private fun trim(reports: List<VideoNoteDiagnosticReport>): List<VideoNoteDiagnosticReport> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        return reports.filter { it.startedAt >= cutoff }.sortedByDescending { it.startedAt }.take(MAX_REPORTS)
    }

    private fun stageText(stage: VideoNoteFpsLimitStage): String =
        localContext?.let { fpsLimitStageText(it, stage) } ?: stage.name

    private fun stamp(message: String): String {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        return "[$time] $message"
    }
}

/** Detailed but compact timing lines shared by the TXT report and the full log. */
internal fun stageTimingLines(context: Context, timing: VideoNoteStageTiming): List<String> {
    fun value(number: Number?): String = number?.toString() ?: context.getString(R.string.loss_no_data)
    fun ms(number: Double?): String = number?.let { String.format(Locale.US, "%.1f", it) }
        ?: context.getString(R.string.loss_no_data)
    val lines = mutableListOf<String>()
    if (timing.p50GapMs == null && timing.p95GapMs == null && timing.maxGapMs == null) {
        lines += context.getString(R.string.vn_entry_timing_unknown)
    } else {
        lines += context.getString(R.string.vn_report_timing_line,
            ms(timing.p50GapMs), ms(timing.p95GapMs), ms(timing.maxGapMs),
            ms(timing.coveredMs), timingSourceText(context, timing.timingSource),
            value(timing.expectedFrames), value(timing.estimatedDroppedFrames),
            timing.repeatedTimestamps ?: 0, timing.reorderedTimestamps ?: 0)
    }
    val audioCount = timing.audioSampleCount ?: 0
    if (audioCount > 0) {
        lines += context.getString(R.string.vn_report_timing_audio,
            audioCount, ms(timing.audioGapP50Ms), ms(timing.audioGapP95Ms), ms(timing.audioMaxGapMs),
            timing.audioRepeatedTimestamps ?: 0)
    }
    val verdict = timing.avVerdict
    if (verdict != null && verdict != AvSyncVerdict.NO_AUDIO) {
        lines += context.getString(R.string.vn_report_timing_av,
            ms(timing.avStartOffsetMs), ms(timing.avEndOffsetMs), ms(timing.avDriftMs),
            context.getString(when (verdict) {
                AvSyncVerdict.IN_SYNC, AvSyncVerdict.NO_AUDIO -> R.string.vn_av_in_sync
                AvSyncVerdict.START_OFFSET -> R.string.vn_av_start_offset
                AvSyncVerdict.DRIFT -> R.string.vn_av_drift
            }))
    }
    timing.exportFrameCount?.let { exported ->
        lines += context.getString(R.string.vn_report_timing_export,
            exported, timing.frameCount, timing.frameCount - exported)
    }
    return lines
}

internal fun timingSourceText(context: Context, source: VideoNoteTimingSource?): String = context.getString(when (source) {
    VideoNoteTimingSource.FILE_FPS -> R.string.vn_timing_source_file
    VideoNoteTimingSource.TARGET_FPS -> R.string.vn_timing_source_target
    VideoNoteTimingSource.MEDIAN_GAP -> R.string.vn_timing_source_median
    VideoNoteTimingSource.UNKNOWN, null -> R.string.vn_timing_source_unknown
})

private fun VideoNoteStageTiming.toJson(): JSONObject = JSONObject().apply {
    put("frameCount", frameCount)
    p50GapMs?.let { put("p50GapMs", it) }
    p95GapMs?.let { put("p95GapMs", it) }
    maxGapMs?.let { put("maxGapMs", it) }
    coveredMs?.let { put("coveredMs", it) }
    timingSource?.let { put("timingSource", it.name) }
    expectedIntervalMs?.let { put("expectedIntervalMs", it) }
    expectedFrames?.let { put("expectedFrames", it) }
    estimatedDroppedFrames?.let { put("estimatedDroppedFrames", it) }
    repeatedTimestamps?.let { put("repeatedTimestamps", it) }
    reorderedTimestamps?.let { put("reorderedTimestamps", it) }
    audioSampleCount?.let { put("audioSampleCount", it) }
    audioGapP50Ms?.let { put("audioGapP50Ms", it) }
    audioGapP95Ms?.let { put("audioGapP95Ms", it) }
    audioMaxGapMs?.let { put("audioMaxGapMs", it) }
    audioRepeatedTimestamps?.let { put("audioRepeatedTimestamps", it) }
    avStartOffsetMs?.let { put("avStartOffsetMs", it) }
    avEndOffsetMs?.let { put("avEndOffsetMs", it) }
    avDriftMs?.let { put("avDriftMs", it) }
    avVerdict?.let { put("avVerdict", it.name) }
    exportFrameCount?.let { put("exportFrameCount", it) }
}

private fun JSONObject.toStageTiming(): VideoNoteStageTiming? {
    if (!has("frameCount")) return null
    fun enumValue(name: String): String? = optString(name, "").takeIf { it.isNotBlank() }
    return VideoNoteStageTiming(
        frameCount = optInt("frameCount", 0),
        p50GapMs = optDoubleOrNull("p50GapMs"),
        p95GapMs = optDoubleOrNull("p95GapMs"),
        maxGapMs = optDoubleOrNull("maxGapMs"),
        coveredMs = optDoubleOrNull("coveredMs"),
        timingSource = enumValue("timingSource")?.let { runCatching { VideoNoteTimingSource.valueOf(it) }.getOrNull() },
        expectedIntervalMs = optDoubleOrNull("expectedIntervalMs"),
        expectedFrames = optIntOrNull("expectedFrames"),
        estimatedDroppedFrames = optIntOrNull("estimatedDroppedFrames"),
        repeatedTimestamps = optIntOrNull("repeatedTimestamps"),
        reorderedTimestamps = optIntOrNull("reorderedTimestamps"),
        audioSampleCount = optIntOrNull("audioSampleCount"),
        audioGapP50Ms = optDoubleOrNull("audioGapP50Ms"),
        audioGapP95Ms = optDoubleOrNull("audioGapP95Ms"),
        audioMaxGapMs = optDoubleOrNull("audioMaxGapMs"),
        audioRepeatedTimestamps = optIntOrNull("audioRepeatedTimestamps"),
        avStartOffsetMs = optDoubleOrNull("avStartOffsetMs"),
        avEndOffsetMs = optDoubleOrNull("avEndOffsetMs"),
        avDriftMs = optDoubleOrNull("avDriftMs"),
        avVerdict = enumValue("avVerdict")?.let { runCatching { AvSyncVerdict.valueOf(it) }.getOrNull() },
        exportFrameCount = optIntOrNull("exportFrameCount")
    )
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null
