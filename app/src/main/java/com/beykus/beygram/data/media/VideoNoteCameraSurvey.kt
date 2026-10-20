package com.beykus.beygram.data.media

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.os.Build
import android.os.PowerManager
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import java.util.Locale

/**
 * Read-only hardware survey. Camera2 ranges describe advertised camera capabilities, not
 * guaranteed CameraX Preview+VideoCapture combinations. Does not open the lens or capture media.
 */
internal object VideoNoteCameraSurvey {
    private val candidates = listOf(
        Size(7680, 4320), Size(3840, 2160), Size(2560, 1440),
        Size(1920, 1080), Size(1280, 720), Size(960, 540), Size(640, 480)
    )

    fun cameraId(info: CameraInfo): String? = runCatching { Camera2CameraInfo.from(info).cameraId }.getOrNull()

    fun report(context: Context): String = buildString {
        appendLine("BeyGram — Camera2 capability survey (без записи камеры)")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, SDK=${Build.VERSION.SDK_INT}")
        appendLine("Camera2/CameraX advertised capabilities ≠ guaranteed frame rate while recording.")
        val manager = context.getSystemService(CameraManager::class.java)
        for (id in manager.cameraIdList) {
            appendLine()
            appendLine("Camera ID: $id")
            appendLine(describeCamera(manager, id))
        }
        appendLine()
        appendLine("H.264 encoders:")
        appendLine(encoderReport())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thermal = context.getSystemService(PowerManager::class.java)?.currentThermalStatus
            appendLine("Thermal status: ${thermal ?: "n/a"} (instantaneous, not historical)")
        }
        appendLine("High-speed profiles require a constrained Camera2 session; standard CameraX VideoCapture cannot infer 60 FPS from them.")
    }

    fun describeCamera(manager: CameraManager, id: String): String = runCatching {
        val c = manager.getCameraCharacteristics(id)
        val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT -> "front"
            CameraCharacteristics.LENS_FACING_BACK -> "back"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
            else -> "unknown"
        }
        val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "unknown"
        }
        val ranges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.joinToString { "${it.lower}..${it.upper}" } ?: "unknown"
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val normalSizes = map?.getOutputSizes(MediaRecorder::class.java)?.toList().orEmpty()
        val highSpeed = runCatching {
            map?.highSpeedVideoSizes.orEmpty().joinToString { size ->
                "${size.width}x${size.height}: ${map?.getHighSpeedVideoFpsRangesFor(size)?.joinToString { "${it.lower}..${it.upper}" } ?: "n/a"}"
            }
        }.getOrDefault("unavailable")
        val sampled = candidates.joinToString(separator = "\n") { size ->
            val supported = normalSizes.contains(size)
            val minNs = if (supported) runCatching {
                map?.getOutputMinFrameDuration(MediaRecorder::class.java, size) ?: 0L
            }.getOrDefault(0L) else 0L
            val maximum = if (minNs > 0) String.format(Locale.US, "%.1f", 1_000_000_000.0 / minNs) else "unknown"
            "  ${size.width}x${size.height}: ${if (supported) "advertised" else "not advertised"}; stream frame-rate ceiling≈$maximum"
        }
        "Facing=$facing, hardware=$level\nNormal AE FPS=$ranges\nRecorder output sizes:\n$sampled\nHigh-speed (NOT normal recording): ${highSpeed.ifBlank { "none" }}"
    }.getOrElse { "Camera2 survey failed: ${it.javaClass.simpleName}" }

    fun encoderSupportsAvc(width: Int, height: Int, fps: Int): Boolean? = runCatching {
        val encoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) } }
        if (encoders.isEmpty()) return@runCatching false
        encoders.any { info -> runCatching {
            info.getCapabilitiesForType("video/avc").videoCapabilities?.areSizeAndRateSupported(width, height, fps.toDouble()) == true
        }.getOrDefault(false) }
    }.getOrNull()

    private fun encoderReport(): String = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) } }
            .joinToString(separator = "\n") { info ->
                val capabilities = runCatching { info.getCapabilitiesForType("video/avc").videoCapabilities }.getOrNull()
                "${info.name}: 1280x720@60=${capabilities?.areSizeAndRateSupported(1280, 720, 60.0)}; 640x480@60=${capabilities?.areSizeAndRateSupported(640, 480, 60.0)}"
            }.ifBlank { "No AVC encoder advertised" }
    }.getOrElse { "Codec capabilities unavailable: ${it.javaClass.simpleName}" }
}

/** Never treat high-speed-only ranges as normal CameraX recording support. */
internal fun safeVideoNoteFpsSelection(
    requested: IntRange,
    cameraXRanges: Collection<IntRange>,
    camera2NormalRanges: Collection<IntRange>?,
    encoderSupports60: Boolean?
): Pair<IntRange?, String> {
    val regular = cameraXRanges.filter { range ->
        range.first <= requested.last && (camera2NormalRanges == null ||
            camera2NormalRanges.any { it.first <= range.first && it.last >= range.last })
    }
    val can60 = requested.last >= 50 && regular.any { it.last >= 60 } && encoderSupports60 != false
    val available = if (requested.last >= 50 && !can60) regular.filter { it.last <= 30 }
        .ifEmpty { regular.filter { it.last < 50 } }
    else regular
    val picked = chooseSupportedFrameRateRange(if (can60) requested else 30..30, available)
    val reason = when {
        picked == null -> "No compatible normal FPS range advertised; CameraX default (verify actual RAW FPS)"
        requested.last >= 50 && !can60 -> "60 FPS not confirmed for normal CameraX video + H.264; fallback to ${picked.last} FPS"
        picked.last < requested.last -> "Camera range fallback: target=${requested.last}, advertised=${picked.last}"
        else -> "CameraX range requested; actual capture FPS must be measured"
    }
    return picked to reason
}
