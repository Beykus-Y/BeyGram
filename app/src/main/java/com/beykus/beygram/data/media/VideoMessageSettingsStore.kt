package com.beykus.beygram.data.media

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ComposerRecordingMode { Voice, Video }
enum class VideoQualityPreset { Standard, High, Maximum, Custom }
enum class VideoProcessingMode { Automatic, Direct, Transcode }
enum class VideoFrameRatePriority(val minimumFps: Int) {
    MaximumQuality(24), Balanced(35), MaximumSmoothness(55)
}

data class VideoMessageSettings(
    val preset: VideoQualityPreset = VideoQualityPreset.High,
    val customSide: Int = 480,
    val customFps: Int = 30,
    val customVideoBitrateKbps: Int = 1500,
    val customAudioBitrateKbps: Int = 96,
    val processing: VideoProcessingMode = VideoProcessingMode.Automatic,
    val frameRatePriority: VideoFrameRatePriority = VideoFrameRatePriority.Balanced,
    val longRecordingEnabled: Boolean = false,
    val mirrorFrontCamera: Boolean = true
)

/**
 * Переключатель отзеркаливания нужен только на API 33 и выше: по документации CameraX Preview.setMirrorMode
 * до API 33 не действует, а превью фронтальной камеры отражает сам PreviewView. Там запись всегда отражается,
 * как и превью, поэтому переключатель не показываем.
 */
internal fun videoMirrorToggleSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU

/** App-private settings for chat recording. These preferences do not touch the TDLib database. */
class VideoMessageSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<VideoMessageSettings> = _settings.asStateFlow()
    private val _recordingMode = MutableStateFlow(readRecordingMode())
    val recordingMode: StateFlow<ComposerRecordingMode> = _recordingMode.asStateFlow()

    @Synchronized
    fun updateSettings(transform: (VideoMessageSettings) -> VideoMessageSettings) {
        val next = transform(_settings.value).validated()
        check(preferences.edit()
            .putString(KEY_PRESET, next.preset.name)
            .putInt(KEY_CUSTOM_SIDE, next.customSide)
            .putInt(KEY_CUSTOM_FPS, next.customFps)
            .putInt(KEY_CUSTOM_VIDEO_BITRATE, next.customVideoBitrateKbps)
            .putInt(KEY_CUSTOM_AUDIO_BITRATE, next.customAudioBitrateKbps)
            .putString(KEY_PROCESSING, next.processing.name)
            .putString(KEY_FRAME_RATE_PRIORITY, next.frameRatePriority.name)
            .putBoolean(KEY_LONG_RECORDING, next.longRecordingEnabled)
            .putBoolean(KEY_MIRROR_FRONT_CAMERA, next.mirrorFrontCamera)
            .commit()){ "Не удалось сохранить настройки видеосообщений" }
        _settings.value = next
    }

    @Synchronized
    fun setRecordingMode(mode: ComposerRecordingMode) {
        check(preferences.edit().putString(KEY_RECORDING_MODE, mode.name).commit()) { "Не удалось сохранить режим записи" }
        _recordingMode.value = mode
    }

    private fun readSettings() = VideoMessageSettings(
        preset = preferences.getString(KEY_PRESET, null).enumOr(VideoQualityPreset.High),
        customSide = preferences.getInt(KEY_CUSTOM_SIDE, 480).takeIf { it in SIDES } ?: 480,
        customFps = preferences.getInt(KEY_CUSTOM_FPS, 30).takeIf { it in FRAME_RATES } ?: 30,
        customVideoBitrateKbps = preferences.getInt(KEY_CUSTOM_VIDEO_BITRATE, 1500).takeIf { it in VIDEO_BITRATES } ?: 1500,
        customAudioBitrateKbps = preferences.getInt(KEY_CUSTOM_AUDIO_BITRATE, 96).takeIf { it in AUDIO_BITRATES } ?: 96,
        processing = preferences.getString(KEY_PROCESSING, null).enumOr(VideoProcessingMode.Automatic),
        frameRatePriority = preferences.getString(KEY_FRAME_RATE_PRIORITY, null).enumOr(VideoFrameRatePriority.Balanced),
        longRecordingEnabled = preferences.getBoolean(KEY_LONG_RECORDING, false),
        mirrorFrontCamera = preferences.getBoolean(KEY_MIRROR_FRONT_CAMERA, true)
    )

    private fun readRecordingMode() = preferences.getString(KEY_RECORDING_MODE, null).enumOr(ComposerRecordingMode.Video)

    private inline fun <reified T : Enum<T>> String?.enumOr(fallback: T): T =
        this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback

    private fun VideoMessageSettings.validated() = copy(
        customSide = customSide.takeIf { it in SIDES } ?: 480,
        customFps = customFps.takeIf { it in FRAME_RATES } ?: 30,
        customVideoBitrateKbps = customVideoBitrateKbps.takeIf { it in VIDEO_BITRATES } ?: 1500,
        customAudioBitrateKbps = customAudioBitrateKbps.takeIf { it in AUDIO_BITRATES } ?: 96
    )

    companion object {
        val SIDES = listOf(384, 480, 640)
        val FRAME_RATES = listOf(24, 30, 60)
        val VIDEO_BITRATES = listOf(1000, 1500, 2000, 2500)
        val AUDIO_BITRATES = listOf(64, 96, 128)
        private const val PREFERENCES = "video-message-settings"
        private const val KEY_PRESET = "preset"
        private const val KEY_CUSTOM_SIDE = "custom_side"
        private const val KEY_CUSTOM_FPS = "custom_fps"
        private const val KEY_CUSTOM_VIDEO_BITRATE = "custom_video_bitrate"
        private const val KEY_CUSTOM_AUDIO_BITRATE = "custom_audio_bitrate"
        private const val KEY_PROCESSING = "processing"
        private const val KEY_FRAME_RATE_PRIORITY = "frame_rate_priority"
        private const val KEY_LONG_RECORDING = "long_recording"
        private const val KEY_MIRROR_FRONT_CAMERA = "mirror_front_camera"
        private const val KEY_RECORDING_MODE = "recording_mode"
    }
}
