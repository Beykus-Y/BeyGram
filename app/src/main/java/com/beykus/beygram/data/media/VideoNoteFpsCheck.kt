package com.beykus.beygram.data.media

import android.content.Context
import android.os.Build
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.AppLocale
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class VideoNoteFpsCheckState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val report: String = ""
)

/**
 * Process-owned "check real FPS" run: survives leaving the screen and rotation, runs one camera
 * mode at a time and can be cancelled. Never starts by itself: only [start] from a user action.
 */
internal object VideoNoteFpsCheck {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val _state = MutableStateFlow(VideoNoteFpsCheckState())
    val state: StateFlow<VideoNoteFpsCheckState> = _state.asStateFlow()

    @Synchronized
    fun start(context: Context, scopeOfLenses: ProbeScope, settings: VideoMessageSettings): Boolean {
        if (job?.isActive == true) return false
        // Reports follow the in-app language, which the application context lacks below API 33.
        val app = AppLocale.context(context.applicationContext)
        _state.value = VideoNoteFpsCheckState(running = true)
        job = scope.launch { run(app, scopeOfLenses, settings) }
        return true
    }

    private val ADVERTISED_HIGH = setOf(ProbeModeStatus.PLANNED, ProbeModeStatus.SKIPPED_LIMIT, ProbeModeStatus.STREAM_TOO_SLOW)

    fun cancel() { job?.cancel() }

    /**
     * Recording must not compete with the check for a camera: starting a video note stops it.
     * Returns the stopping check, whose cleanup still releases the lens, or null when none ran.
     */
    fun cancelForRecording(): Job? {
        val running = job?.takeIf { it.isActive } ?: return null
        running.cancel()
        return running
    }

    private suspend fun run(context: Context, lenses: ProbeScope, settings: VideoMessageSettings) {
        val probe = VideoNoteFpsProbe(context)
        val rows = mutableListOf<ProbeRow>()
        var stop: Int? = null
        var plan: List<ProbeMode> = emptyList()
        try {
            if (!probe.hasPermission()) {
                _state.value = VideoNoteFpsCheckState(report = context.getString(R.string.fps_check_no_permission))
                return
            }
            plan = planVideoNoteProbe(probe.cameras(), lenses)
            val opened = plan.count { it.status == ProbeModeStatus.PLANNED }
            _state.update { it.copy(total = opened) }
            withTimeout(VideoNoteFpsProbe.RUN_TIMEOUT_MS) {
                for (mode in plan) {
                    if (mode.status != ProbeModeStatus.PLANNED) { rows += ProbeRow(mode, null); continue }
                    val outcome = probe.measureWithTimeout(mode)
                    rows += ProbeRow(mode, outcome)
                    _state.update { it.copy(done = it.done + 1) }
                    if (outcome is ProbeOutcome.StoppedHot) { stop = R.string.fps_check_stopped_hot; break }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            stop = R.string.fps_check_stopped_timeout
        } catch (cancelled: CancellationException) {
            _state.value = VideoNoteFpsCheckState(report = render(context, settings, rows, plan, R.string.fps_check_stopped_cancel))
            throw cancelled
        } catch (failure: Exception) {
            _state.value = VideoNoteFpsCheckState(
                report = context.getString(R.string.fps_check_failed, failure.javaClass.simpleName))
            return
        }
        _state.value = VideoNoteFpsCheckState(report = render(context, settings, rows, plan, stop))
    }

    private fun render(context: Context, settings: VideoMessageSettings, rows: List<ProbeRow>, plan: List<ProbeMode>, stop: Int?): String = buildString {
        appendLine(context.getString(R.string.fps_check_report_heading))
        appendLine(context.getString(R.string.camera_report_device, Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT))
        appendLine(context.getString(R.string.fps_check_report_method))
        VideoNoteCameraSurvey.thermalLine(context)?.let(::appendLine)
        stop?.let { appendLine(context.getString(it)) }
        appendLine()
        append(VideoNoteCameraSurvey.recordingDecisionsReport(context, settings))
        // Modes that were planned but not reached (cancel, timeout, heat) are listed as not checked.
        val reached = rows.map { it.mode }.toSet()
        val all = rows + plan.filter { it !in reached }.map { ProbeRow(it, null) }
        all.groupBy { it.mode.cameraId }.forEach { (id, cameraRows) ->
            appendLine()
            appendLine(context.getString(R.string.fps_check_camera_heading, id, facingLabel(context, cameraRows.first().mode.facing)))
            cameraRows.forEach { appendLine(rowText(context, it)) }
        }
        appendLine()
        appendLine(conclusion(context, all))
        appendLine(context.getString(R.string.fps_check_report_notice))
    }

    private fun rowText(context: Context, row: ProbeRow): String {
        val mode = row.mode
        val ceiling = mode.streamCeilingFps?.let { String.format(Locale.US, "%.0f", it) } ?: "?"
        val head = context.getString(R.string.fps_check_row_head, mode.size.toString(), mode.targetFps,
            mode.range?.toText() ?: "—", ceiling)
        val tail = when (mode.status) {
            ProbeModeStatus.NOT_ADVERTISED -> context.getString(R.string.fps_check_row_not_advertised)
            ProbeModeStatus.STREAM_TOO_SLOW -> context.getString(R.string.fps_check_row_too_slow)
            ProbeModeStatus.HIGH_SPEED_ONLY -> context.getString(R.string.fps_check_row_high_speed)
            ProbeModeStatus.SKIPPED_LIMIT -> context.getString(R.string.fps_check_row_limit)
            ProbeModeStatus.PLANNED -> outcomeText(context, row.outcome, mode.targetFps)
        }
        return "  $head — $tail"
    }

    private fun outcomeText(context: Context, outcome: ProbeOutcome?, target: Int): String = when (outcome) {
        null -> context.getString(R.string.fps_check_row_not_reached)
        is ProbeOutcome.OpenFailed -> context.getString(R.string.fps_check_row_open_failed, outcome.reason)
        is ProbeOutcome.SessionFailed -> context.getString(R.string.fps_check_row_session_failed, outcome.reason)
        ProbeOutcome.TimedOut -> context.getString(R.string.fps_check_row_timeout)
        ProbeOutcome.Cancelled -> context.getString(R.string.fps_check_row_not_reached)
        ProbeOutcome.StoppedHot -> context.getString(R.string.fps_check_stopped_hot)
        is ProbeOutcome.Measured -> {
            val s = outcome.stats
            fun f(v: Double?) = v?.let { String.format(Locale.US, "%.1f", it) } ?: "?"
            context.getString(R.string.fps_check_row_measured, f(s.measuredFps), s.frames, f(s.p50GapMs), f(s.p95GapMs),
                f(s.maxGapMs), s.longGaps, f(s.medianExposureMs), outcome.failedCaptures) + "; " +
                verdictText(context, outcome, target) + "\n    " + detailsText(context, outcome)
        }
    }

    private fun verdictText(context: Context, outcome: ProbeOutcome.Measured, target: Int): String = when (outcome.verdict) {
        ProbeVerdict.REACHED -> context.getString(R.string.fps_verdict_reached, target)
        ProbeVerdict.LOW_LIGHT -> context.getString(R.string.fps_verdict_low_light)
        ProbeVerdict.THERMAL -> context.getString(R.string.fps_verdict_thermal)
        ProbeVerdict.BELOW_UNEXPLAINED -> context.getString(R.string.fps_verdict_unexplained)
        ProbeVerdict.STALLED -> context.getString(R.string.fps_verdict_stalled)
        // Zero results at all and results that stopped before the window are different failures.
        ProbeVerdict.NO_FRAMES -> if (outcome.counters.results == 0) context.getString(R.string.fps_verdict_no_results)
            else context.getString(R.string.fps_verdict_no_frames)
    }

    /** Raw counters of the mode, so a stalled or empty stream can be told apart on a device report. */
    private fun detailsText(context: Context, outcome: ProbeOutcome.Measured): String {
        val c = outcome.counters
        val s = outcome.stats
        val text = context.getString(R.string.fps_check_row_details,
            String.format(Locale.US, "%.0f", s.coverage * 100), s.expectedFrames,
            String.format(Locale.US, "%.0f", s.spanMs), String.format(Locale.US, "%.0f", s.windowMs),
            c.firstResultDelayMs?.toString() ?: "—", c.captureStarted, c.results,
            c.imagesAvailable, c.imagesAcquired, c.acquireErrors, c.buffersLost,
            c.cleanupMs?.toString() ?: "—")
        val extra = buildList {
            c.firstAcquireError?.let { add(context.getString(R.string.fps_check_row_acquire_error, it)) }
            if (c.sequenceAborted) add(context.getString(R.string.fps_check_row_aborted))
        }
        return (listOf(text) + extra).joinToString("; ")
    }

    /** One-line answer to "did any lens really deliver 60 FPS here". */
    private fun conclusion(context: Context, rows: List<ProbeRow>): String {
        val measured = rows.mapNotNull { row -> (row.outcome as? ProbeOutcome.Measured)?.let { row.mode to it } }
        val sixty = measured.filter { (mode, m) -> mode.targetFps >= 50 && m.verdict == ProbeVerdict.REACHED }
        val tried60 = measured.any { (mode, _) -> mode.targetFps >= 50 }
        return when {
            sixty.isNotEmpty() -> context.getString(R.string.fps_check_conclusion_60_yes,
                sixty.joinToString { (mode, _) -> "${mode.cameraId} ${mode.size}" })
            tried60 -> context.getString(R.string.fps_check_conclusion_60_tried)
            // Advertised 60 that failed to open, timed out, hit the limit or a too-slow stream
            // is "not measured", never "not advertised".
            rows.any { it.mode.targetFps >= 50 && it.mode.status in ADVERTISED_HIGH } ->
                context.getString(R.string.fps_check_conclusion_60_unmeasured)
            measured.isEmpty() -> context.getString(R.string.fps_check_conclusion_nothing)
            else -> context.getString(R.string.fps_check_conclusion_60_not_advertised)
        }
    }
}
