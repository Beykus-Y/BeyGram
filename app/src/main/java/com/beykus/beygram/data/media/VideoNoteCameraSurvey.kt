package com.beykus.beygram.data.media

import android.content.Context
import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.os.PowerManager
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import com.beykus.beygram.R
import java.util.Locale
import java.util.concurrent.TimeUnit

/** The lens the recorder would use for one facing, as the recording pipeline sees it. */
/** Capture dimensions are a request to CameraX, not measured RAW properties. */
internal enum class VideoNoteSourceQuality { HD, SD }
internal data class VideoNoteRecordingPlan(
    val decision: VideoNoteFpsDecision,
    val quality: VideoNoteSourceQuality,
    val width: Int,
    val height: Int
)

/** Only fall back to SD when a normal stream and the encoder confirm 60 FPS. */
internal fun videoNoteSourceForHighFps(
    hd60: Boolean?, sd60: Boolean?, hdEncoder60: Boolean?, sdEncoder60: Boolean?
): VideoNoteSourceQuality = if ((hd60 == false || hdEncoder60 == false) &&
    sd60 == true && sdEncoder60 == true) VideoNoteSourceQuality.SD else VideoNoteSourceQuality.HD

internal data class RecordingLens(val facing: ProbeFacing, val cameraId: String?, val ranges: List<IntRange>, val cameraInfo: CameraInfo? = null)

/**
 * Read-only hardware survey. Advertised ranges describe camera capabilities, not guaranteed
 * Preview+VideoCapture combinations. Does not open the lens or capture media.
 */
internal object VideoNoteCameraSurvey {
    private val candidates = listOf(
        Size(7680, 4320), Size(3840, 2160), Size(2560, 1440), Size(1920, 1080),
        Size(1280, 720), Size(960, 540), Size(720, 720), Size(640, 640), Size(854, 480), Size(640, 480)
    )

    fun cameraId(info: CameraInfo): String? = runCatching { Camera2CameraInfo.from(info).cameraId }.getOrNull()

    /** Source size the recorder asks for: HD for sides above 480, SD otherwise. */
    fun sourceSize(settings: VideoMessageSettings): Pair<Int, Int> {
        val side = when (settings.preset) {
            VideoQualityPreset.Standard -> 384
            VideoQualityPreset.High -> 480
            VideoQualityPreset.Maximum -> 640
            VideoQualityPreset.Custom -> settings.customSide
        }
        return if (side > 480) 1280 to 720 else 640 to 480
    }

    /**
     * The exact FPS decision the recorder makes for [settings] on [cameraId]. The recorder and
     * the diagnostics both call this, so the report cannot drift from what recording does.
     */
    fun recordingDecision(context: Context, settings: VideoMessageSettings, cameraId: String?, cameraRanges: List<IntRange>): VideoNoteFpsDecision =
        recordingPlan(context, settings, cameraId, cameraRanges).decision

    /** Choose SD at high FPS only if HD is explicitly unable to do it and SD
     * has a compatible normal camera stream plus H.264 encoder. */
    fun recordingPlan(context: Context, settings: VideoMessageSettings, cameraId: String?, cameraRanges: List<IntRange>, cameraInfo: CameraInfo? = null): VideoNoteRecordingPlan {
        val requested = settings.requestedFrameRateRange()
        val manager = context.getSystemService(CameraManager::class.java)
        val characteristics = cameraId?.let { id -> runCatching { manager.getCameraCharacteristics(id) }.getOrNull() }
        val normalRanges = characteristics?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.map { it.lower..it.upper }
        val (requestedWidth, requestedHeight) = sourceSize(settings)
        val nativeQuality = if (requestedWidth > 640) VideoNoteSourceQuality.HD else VideoNoteSourceQuality.SD
        // CameraX Quality.SD may mean 720×480 rather than 640×480. Never test
        // an invented size against Camera2 and assume the Recorder will select it.
        val actualHd = qualityResolution(cameraInfo, Quality.HD)
        val actualSd = qualityResolution(cameraInfo, Quality.SD)
        val original = if (nativeQuality == VideoNoteSourceQuality.HD) actualHd else actualSd
        val high = requested.last >= 50
        val cameraAllows60 = cameraRanges.any { it.last >= 60 } &&
            normalRanges?.any { it.last >= 60 } != false
        val hdEncoder = if (high && original != null) encoderSupportsAvc(original.first, original.second, 60)
            else null
        val sdEncoder = if (high && actualSd != null) encoderSupportsAvc(actualSd.first, actualSd.second, 60)
            else null
        val quality = if (high && nativeQuality == VideoNoteSourceQuality.HD &&
            cameraAllows60 && actualHd != null && actualSd != null) {
            videoNoteSourceForHighFps(
                sourceSupports60(characteristics, actualHd.first, actualHd.second),
                sourceSupports60(characteristics, actualSd.first, actualSd.second),
                hdEncoder, sdEncoder
            )
        } else nativeQuality
        val resolved = if (quality == VideoNoteSourceQuality.SD) actualSd else actualHd
        val (width, height) = resolved ?: (requestedWidth to requestedHeight)
        val encoder = if (quality == VideoNoteSourceQuality.SD) sdEncoder else hdEncoder
        // Camera2 stream ceiling is a filter, not a guarantee Preview + capture will sustain 60.
        val stream60 = if (high && resolved != null) sourceSupports60(characteristics, width, height) else null
        val selected = safeVideoNoteFpsSelection(
            requested, cameraRanges, normalRanges, encoder, stream60
        )
        return VideoNoteRecordingPlan(selected, quality, width, height)
    }

    private fun qualityResolution(info: CameraInfo?, quality: Quality): Pair<Int, Int>? =
        info?.let { runCatching { QualitySelector.getResolution(it, quality) }.getOrNull() }
            ?.let { it.width to it.height }

    /** Validate active Recorder quality before switching lenses, not only the AE range.
     * A failed check leaves the persistent recording bound to its original lens. */
    fun compatibleHighFpsSwitch(
        context: Context, target: CameraInfo, quality: VideoNoteSourceQuality,
        currentSize: Pair<Int, Int>, range: IntRange
    ): Boolean {
        val requestedQuality = if (quality == VideoNoteSourceQuality.SD) Quality.SD else Quality.HD
        val actual = qualityResolution(target, requestedQuality) ?: return false
        if (actual != currentSize) return false
        val cameraId = cameraId(target) ?: return false
        val characteristics = runCatching {
            context.getSystemService(CameraManager::class.java).getCameraCharacteristics(cameraId)
        }.getOrNull() ?: return false
        val ae = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.any { it.lower <= range.first && it.upper >= range.last } == true
        return ae && sourceSupports60(characteristics, actual.first, actual.second) == true &&
            encoderSupportsAvc(actual.first, actual.second, 60) == true
    }

    /** Null = unknown; no stream duration metadata cannot be treated as proof of 60. */
    private fun sourceSupports60(c: CameraCharacteristics?, width: Int, height: Int): Boolean? {
        val map = c?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        val size = Size(width, height)
        val supported = map.getOutputSizes(MediaRecorder::class.java)?.contains(size) ?: return null
        if (!supported) return false
        val ns = runCatching { map.getOutputMinFrameDuration(MediaRecorder::class.java, size) }.getOrNull()
        return ns?.takeIf { it > 0 }?.let { it <= (1_000_000_000L + 59L) / 60L }
    }

    /** Lenses the recorder picks for front and back. Blocks briefly; call off the main thread. */
    fun recordingLenses(context: Context): List<RecordingLens> = runCatching {
        val provider = ProcessCameraProvider.getInstance(context).get(5, TimeUnit.SECONDS)
        listOf(ProbeFacing.FRONT to CameraSelector.DEFAULT_FRONT_CAMERA, ProbeFacing.BACK to CameraSelector.DEFAULT_BACK_CAMERA)
            .mapNotNull { (facing, selector) ->
                val info = runCatching { selector.filter(provider.availableCameraInfos).firstOrNull() }.getOrNull()
                    ?: return@mapNotNull null
                RecordingLens(facing, cameraId(info), info.supportedFrameRateRanges.map { it.lower..it.upper }, info)
            }
    }.getOrDefault(emptyList())

    fun report(context: Context, settings: VideoMessageSettings): String = VideoNoteReportRedactor.redact(buildString {
        appendLine(context.getString(R.string.loss_camera_report_heading))
        append(VideoNoteEnvironment.snapshot(context))
        appendLine(context.getString(R.string.camera_report_advertised_notice))
        appendLine()
        append(recordingDecisionsReport(context, settings))
        val manager = context.getSystemService(CameraManager::class.java)
        for (id in manager.cameraIdList) {
            appendLine()
            appendLine(context.getString(R.string.camera_report_camera_id, id))
            appendLine(describeCamera(context, manager, id))
            appendLine(VideoNoteCameraDetails.describe(context, manager, id))
        }
        appendLine()
        appendLine(context.getString(R.string.camera_report_encoders))
        appendLine(encoderReport(context))
        thermalLine(context)?.let(::appendLine)
        appendLine(context.getString(R.string.camera_report_high_speed_notice))
    })

    /** "Why does normal recording pick this range" for front and back, with the current settings. */
    fun recordingDecisionsReport(context: Context, settings: VideoMessageSettings): String = buildString {
        appendLine(context.getString(R.string.camera_report_recording_heading, settings.requestedFrameRateRange().toText()))
        val lenses = recordingLenses(context)
        if (lenses.isEmpty()) appendLine(context.getString(R.string.camera_report_recording_unavailable))
        lenses.forEach { lens ->
            val plan = recordingPlan(context, settings, lens.cameraId, lens.ranges, lens.cameraInfo)
            val decision = plan.decision
            appendLine(context.getString(R.string.vn_entry_source_plan, plan.quality.name, plan.width, plan.height))
            appendLine(context.getString(R.string.camera_report_recording_lens,
                facingLabel(context, lens.facing), lens.cameraId ?: "?",
                decision.selected?.toText() ?: context.getString(R.string.camera_report_range_default),
                fpsCauseText(context, decision.cause)))
            appendLine(context.getString(R.string.camera_report_recording_candidates,
                decision.normalCandidates.joinToString { it.toText() }.ifBlank { "—" },
                yesNoUnknown(context, decision.encoderSupports60)))
        }
    }

    fun describeCamera(context: Context, manager: CameraManager, id: String): String = runCatching {
        val c = manager.getCameraCharacteristics(id)
        val facing = facingLabel(context, VideoNoteFpsProbe.facingOf(c.get(CameraCharacteristics.LENS_FACING)))
        val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "?"
        }
        val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        val logical = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in capabilities
        val physical = runCatching { c.physicalCameraIds.sorted() }.getOrDefault(emptyList())
        val ranges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.joinToString { "${it.lower}..${it.upper}" } ?: "?"
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val normalSizes = map?.getOutputSizes(MediaRecorder::class.java)?.toList().orEmpty()
        val highSpeed = runCatching {
            map?.highSpeedVideoSizes.orEmpty().joinToString { size ->
                "${size.width}x${size.height}: ${map?.getHighSpeedVideoFpsRangesFor(size)?.joinToString { "${it.lower}..${it.upper}" } ?: "?"}"
            }
        }.getOrDefault("")
        val sampled = candidates.joinToString(separator = "\n") { size -> sizeLine(context, map, normalSizes, size) }
        // Non-standard sizes the camera lists are reported too, so the survey is not limited to the template.
        val extra = normalSizes.filter { it !in candidates }.sortedByDescending { it.width * it.height }
            .joinToString { "${it.width}x${it.height}" }
        buildString {
            appendLine(context.getString(R.string.camera_report_facing_level, facing, level))
            if (logical || physical.isNotEmpty()) {
                appendLine(context.getString(R.string.camera_report_logical, physical.joinToString().ifBlank { "—" }))
            }
            appendLine(context.getString(R.string.camera_report_normal_ranges, ranges))
            appendLine(context.getString(R.string.camera_report_sizes_heading))
            appendLine(sampled)
            if (extra.isNotBlank()) appendLine(context.getString(R.string.camera_report_other_sizes, extra))
            append(context.getString(R.string.camera_report_high_speed,
                highSpeed.ifBlank { context.getString(R.string.camera_report_none) }))
        }
    }.getOrElse { context.getString(R.string.camera_report_failed, it.javaClass.simpleName) }

    private fun sizeLine(context: Context, map: android.hardware.camera2.params.StreamConfigurationMap?, normalSizes: List<Size>, size: Size): String {
        val supported = normalSizes.contains(size)
        if (!supported) return context.getString(R.string.camera_report_size_absent, size.width, size.height)
        val minNs = runCatching { map?.getOutputMinFrameDuration(MediaRecorder::class.java, size) ?: 0L }.getOrDefault(0L)
        val ceiling = if (minNs > 0) String.format(Locale.US, "%.1f", 1_000_000_000.0 / minNs) else "?"
        return context.getString(R.string.camera_report_size_present, size.width, size.height, ceiling)
    }

    fun encoderSupportsAvc(width: Int, height: Int, fps: Int): Boolean? = runCatching {
        val encoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) } }
        if (encoders.isEmpty()) return@runCatching false
        encoders.any { info -> runCatching {
            info.getCapabilitiesForType("video/avc").videoCapabilities?.areSizeAndRateSupported(width, height, fps.toDouble()) == true
        }.getOrDefault(false) }
    }.getOrNull()

    private val encoderChecks = listOf(1920 to 1080, 1280 to 720, 720 to 720, 640 to 640, 640 to 480)

    private fun encoderReport(context: Context): String = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) } }
            .joinToString(separator = "\n") { info ->
                val capabilities = runCatching { info.getCapabilitiesForType("video/avc") }.getOrNull()
                val video = capabilities?.videoCapabilities
                val checks = encoderChecks.joinToString { (w, h) ->
                    "${w}x$h@60=${yesNoUnknown(context, video?.let { runCatching { it.areSizeAndRateSupported(w, h, 60.0) }.getOrNull() })}"
                }
                val hardware = runCatching { info.isHardwareAccelerated }.getOrNull()
                val profiles = capabilities?.profileLevels.orEmpty()
                    .mapNotNull { avcProfileLabel(it.profile) }.distinct().take(8)
                val levels = capabilities?.profileLevels.orEmpty()
                    .mapNotNull { avcLevelLabel(it.level) }.distinct().take(8)
                val bitrate = video?.bitrateRange
                val frameRates = runCatching { video?.supportedFrameRates }.getOrNull()
                val instances = runCatching { capabilities?.maxSupportedInstances }.getOrNull()
                buildString {
                    appendLine(context.getString(R.string.camera_report_encoder_line,
                        info.name, yesNoUnknown(context, hardware), checks))
                    append(context.getString(R.string.camera_report_encoder_meta,
                        profiles.joinToString().ifBlank { "?" },
                        levels.joinToString().ifBlank { "?" },
                        bitrate?.lower?.toString() ?: "?",
                        bitrate?.upper?.toString() ?: "?",
                        frameRates?.lower?.let { String.format(Locale.US, "%.1f", it) } ?: "?",
                        frameRates?.upper?.let { String.format(Locale.US, "%.1f", it) } ?: "?",
                        instances?.toString() ?: "?"))
                }
            }.ifBlank { context.getString(R.string.camera_report_no_encoder) }
    }.getOrElse { context.getString(R.string.camera_report_encoder_failed, it.javaClass.simpleName) }

    fun thermalLine(context: Context): String? {
        val status = runCatching { context.getSystemService(PowerManager::class.java)?.currentThermalStatus }.getOrNull()
            ?: return null
        return context.getString(R.string.camera_report_thermal, thermalLabel(context, status))
    }
}

internal fun IntRange.toText(): String = "$first..$last"

internal fun facingLabel(context: Context, facing: ProbeFacing): String = context.getString(when (facing) {
    ProbeFacing.FRONT -> R.string.camera_facing_front
    ProbeFacing.BACK -> R.string.camera_facing_back
    ProbeFacing.EXTERNAL -> R.string.camera_facing_external
    ProbeFacing.UNKNOWN -> R.string.camera_facing_unknown
})

internal fun yesNoUnknown(context: Context, value: Boolean?): String = context.getString(when (value) {
    true -> R.string.loss_yes
    false -> R.string.loss_no
    null -> R.string.loss_no_data
})

internal fun thermalLabel(context: Context, status: Int): String = when (status) {
    PowerManager.THERMAL_STATUS_NONE -> context.getString(R.string.camera_thermal_none)
    PowerManager.THERMAL_STATUS_LIGHT -> context.getString(R.string.camera_thermal_light)
    PowerManager.THERMAL_STATUS_MODERATE -> context.getString(R.string.camera_thermal_moderate)
    PowerManager.THERMAL_STATUS_SEVERE -> context.getString(R.string.camera_thermal_severe)
    else -> context.getString(R.string.camera_thermal_critical, status)
}

/** CaptureResult state names for the recording report; unknown values stay numeric. */
internal fun aeStateLabel(context: Context, state: Int): String = context.getString(when (state) {
    CameraMetadata.CONTROL_AE_STATE_INACTIVE -> R.string.ae_state_inactive
    CameraMetadata.CONTROL_AE_STATE_SEARCHING -> R.string.ae_state_searching
    CameraMetadata.CONTROL_AE_STATE_CONVERGED -> R.string.ae_state_converged
    CameraMetadata.CONTROL_AE_STATE_LOCKED -> R.string.ae_state_locked
    CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED -> R.string.ae_state_flash_required
    CameraMetadata.CONTROL_AE_STATE_PRECAPTURE -> R.string.ae_state_precapture
    else -> R.string.ae_state_unknown
}, state)

@SuppressLint("InlinedApi") // PREVIEW_STABILIZATION is a constant added in API 33; it is inlined, so it is safe to compare on older releases.
internal fun videoStabilizationLabel(context: Context, mode: Int): String = context.getString(when (mode) {
    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF -> R.string.stabilization_off
    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON -> R.string.stabilization_on
    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION -> R.string.stabilization_preview
    else -> R.string.ae_state_unknown
}, mode)

internal fun opticalStabilizationLabel(context: Context, mode: Int): String = context.getString(when (mode) {
    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF -> R.string.stabilization_off
    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON -> R.string.stabilization_on
    else -> R.string.ae_state_unknown
}, mode)

internal fun fpsCauseText(context: Context, cause: VideoNoteFpsCause): String = context.getString(when (cause) {
    VideoNoteFpsCause.AS_REQUESTED -> R.string.fps_cause_as_requested
    VideoNoteFpsCause.CAMERA_RANGE_FALLBACK -> R.string.fps_cause_camera_fallback
    VideoNoteFpsCause.NO_NORMAL_60 -> R.string.fps_cause_no_normal_60
    VideoNoteFpsCause.ENCODER_REJECTS_60 -> R.string.fps_cause_encoder_60
    VideoNoteFpsCause.STREAM_REJECTS_60 -> R.string.fps_cause_stream_60
    VideoNoteFpsCause.NO_COMPATIBLE_RANGE -> R.string.fps_cause_no_range
})

internal fun fpsLimitStageText(context: Context, stage: VideoNoteFpsLimitStage): String = context.getString(when (stage) {
    VideoNoteFpsLimitStage.NONE -> R.string.fps_stage_none
    VideoNoteFpsLimitStage.POLICY -> R.string.fps_stage_policy
    VideoNoteFpsLimitStage.CAMERA_RUNTIME -> R.string.fps_stage_camera
    VideoNoteFpsLimitStage.EXPORT -> R.string.fps_stage_export
    VideoNoteFpsLimitStage.UNKNOWN -> R.string.fps_stage_unknown
})
