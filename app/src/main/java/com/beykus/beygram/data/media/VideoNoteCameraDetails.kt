package com.beykus.beygram.data.media

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.CamcorderProfile
import android.os.Build
import android.util.Size
import com.beykus.beygram.R
import java.util.Locale

/**
 * Passive, read-only extension of the camera survey (issue #234, §2). Everything here is what the
 * camera advertises; nothing opens the lens. The text never says a mode "does not work": a
 * missing key is reported as "no data".
 */
internal object VideoNoteCameraDetails {
    private val streamSizes = listOf(
        Size(1920, 1080), Size(1280, 720), Size(854, 480), Size(720, 720), Size(640, 640), Size(640, 480)
    )
    private val profileQualities = listOf(
        "1080p" to CamcorderProfile.QUALITY_1080P,
        "720p" to CamcorderProfile.QUALITY_720P,
        "480p" to CamcorderProfile.QUALITY_480P,
        "HIGH" to CamcorderProfile.QUALITY_HIGH
    )

    fun describe(context: Context, manager: CameraManager, id: String): String = runCatching {
        val c = manager.getCameraCharacteristics(id)
        val noData = context.getString(R.string.loss_no_data)
        val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        buildString {
            appendLine(context.getString(R.string.camera_detail_capabilities,
                capabilities.joinToString { capabilityName(it) }.ifBlank { noData }))
            appendLine(context.getString(R.string.camera_detail_manual_sensor,
                yesNoUnknown(context, c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.let {
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in it
                })))
            val active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val pixels = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val physical = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            appendLine(context.getString(R.string.camera_detail_sensor,
                active?.let { "${it.width()}x${it.height()}" } ?: noData,
                pixels?.let { "${it.width}x${it.height}" } ?: noData,
                physical?.let { String.format(Locale.US, "%.2fx%.2f mm", it.width, it.height) } ?: noData,
                c.get(CameraCharacteristics.SENSOR_ORIENTATION)?.toString() ?: noData))
            val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            appendLine(context.getString(R.string.camera_detail_lens,
                focal?.joinToString { String.format(Locale.US, "%.2f", it) } ?: noData,
                c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.let { String.format(Locale.US, "%.1f", it) } ?: noData))
            val exposure = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val maxFrameNs = c.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)
            val compensation = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            appendLine(context.getString(R.string.camera_detail_exposure,
                exposure?.let { "${it.lower / 1000} µs..${it.upper / 1_000_000} ms" } ?: noData,
                maxFrameNs?.let { String.format(Locale.US, "%.1f", it / 1_000_000.0) } ?: noData,
                compensation?.let { "${it.lower}..${it.upper}" } ?: noData))
            appendLine(context.getString(R.string.camera_detail_stabilization,
                c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?.joinToString() ?: noData,
                c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.joinToString() ?: noData,
                c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)?.joinToString() ?: noData))
            val sessionKeys = runCatching { c.availableSessionKeys?.size }.getOrNull()
            val requestKeys = runCatching { c.availableCaptureRequestKeys.size }.getOrNull()
            appendLine(context.getString(R.string.camera_detail_keys, sessionKeys?.toString() ?: noData, requestKeys?.toString() ?: noData))
            if (Build.VERSION.SDK_INT >= 35) {
                val setup = runCatching { manager.isCameraDeviceSetupSupported(id) }.getOrNull()
                appendLine(context.getString(R.string.camera_detail_device_setup, yesNoUnknown(context, setup)))
            } else {
                appendLine(context.getString(R.string.camera_detail_device_setup_old, Build.VERSION.SDK_INT))
            }
            appendLine(context.getString(R.string.camera_detail_stream_heading))
            appendLine(streamMatrix(context, c))
            append(profiles(context, id))
        }
    }.getOrElse { context.getString(R.string.camera_report_failed, it.javaClass.simpleName) }

    /**
     * Per-format ceilings. These are separate streams' limits; several streams at once can run slower,
     * and a ceiling is a calculation from the minimum frame duration, not a measurement.
     */
    private fun streamMatrix(context: Context, c: CameraCharacteristics): String {
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return context.getString(R.string.loss_no_data)
        val formats = listOf("PRIVATE" to ImageFormat.PRIVATE, "YUV_420_888" to ImageFormat.YUV_420_888)
        val lines = formats.flatMap { (name, format) ->
            val sizes = runCatching { map.getOutputSizes(format)?.toSet().orEmpty() }.getOrDefault(emptySet())
            streamSizes.map { size ->
                if (size !in sizes) return@map context.getString(R.string.camera_detail_stream_absent, name, size.width, size.height)
                val minNs = runCatching { map.getOutputMinFrameDuration(format, size) }.getOrDefault(0L)
                val stallNs = runCatching { map.getOutputStallDuration(format, size) }.getOrDefault(0L)
                context.getString(R.string.camera_detail_stream_line, name, size.width, size.height,
                    if (minNs > 0) String.format(Locale.US, "%.1f", 1e9 / minNs) else "?",
                    String.format(Locale.US, "%.1f", stallNs / 1e6))
            }
        }
        return lines.joinToString("\n")
    }

    /** Declared recording profiles (not capture results); only cameras whose id is a number have them. */
    private fun profiles(context: Context, id: String): String {
        val numeric = id.toIntOrNull() ?: return context.getString(R.string.camera_detail_profiles_none, id)
        val lines = profileQualities.mapNotNull { (label, quality) ->
            runCatching {
                if (!CamcorderProfile.hasProfile(numeric, quality)) return@runCatching null
                val p = CamcorderProfile.get(numeric, quality) ?: return@runCatching null
                context.getString(R.string.camera_detail_profile_line, label, p.videoFrameWidth, p.videoFrameHeight,
                    p.videoFrameRate, p.videoBitRate / 1000)
            }.getOrNull()
        }
        return if (lines.isEmpty()) context.getString(R.string.camera_detail_profiles_none, id)
        else context.getString(R.string.camera_detail_profiles_heading) + "\n" + lines.joinToString("\n")
    }

    internal fun capabilityName(value: Int): String = when (value) {
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "READ_SENSOR_SETTINGS"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST_CAPTURE"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "CONSTRAINED_HIGH_SPEED_VIDEO"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "MONOCHROME"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA -> "SECURE_IMAGE_DATA"
        else -> value.toString()
    }
}
