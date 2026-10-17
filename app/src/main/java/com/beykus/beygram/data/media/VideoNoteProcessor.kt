package com.beykus.beygram.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Re-encodes a recording into sequential square Telegram video notes, one disk-backed clip at a time. */
@OptIn(UnstableApi::class)
class VideoNoteProcessor(private val context: Context) {
    suspend fun process(source: File, settings: VideoMessageSettings): List<OutgoingMedia> {
        val sourceDurationMs = withContext(Dispatchers.IO) { metadata(source).durationMs }
        require(sourceDurationMs > 0L) { "Не удалось прочитать длительность записи" }
        val target = settings.targetProfile()
        val directory = source.parentFile ?: error("Нет папки записи")
        val result = mutableListOf<OutgoingMedia>()
        var startMs = 0L
        var index = 0
        try {
            while (startMs < sourceDurationMs) {
                val endMs = minOf(startMs + MAX_SEGMENT_MS, sourceDurationMs)
                val output = File(directory, "video-note-${index + 1}.mp4")
                try {
                    transform(source, output, startMs, endMs, target.side, target.videoBitrate, target.audioBitrate)
                    val info = withContext(Dispatchers.IO) { metadata(output) }
                    require(info.width == target.side && info.height == target.side) { "Кодировщик изменил размер видеокружка" }
                    require(info.durationMs in 1L..MAX_SEGMENT_MS) { "Длительность сегмента превышает 60 секунд" }
                    result += OutgoingMedia(
                        path = output.absolutePath,
                        fileName = "video-note-${index + 1}.mp4",
                        mimeType = "video/mp4",
                        kind = MediaKind.VideoNote,
                        width = info.width,
                        height = info.height,
                        durationSeconds = ((info.durationMs + 999L) / 1000L).toInt(),
                        size = output.length()
                    )
                } catch (failure: Exception) {
                    output.delete()
                    throw failure
                }
                startMs = endMs
                index++
            }
            source.delete()
            return result
        } catch (failure: Exception) {
            result.forEach { File(it.path).delete() }
            throw failure
        }
    }

    private suspend fun transform(input: File, output: File, startMs: Long, endMs: Long, side: Int, videoBitrate: Int, audioBitrate: Int) {
        withContext(Dispatchers.Main.immediate) {
            output.delete()
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(input))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()
            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(side, side, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))))
                .build()
            suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(videoBitrate).build())
                        .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(audioBitrate).build())
                        .build())
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(
                            composition: androidx.media3.transformer.Composition,
                            exportResult: ExportResult,
                            exportException: androidx.media3.transformer.ExportException
                        ) {
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    })
                    .build()
                continuation.invokeOnCancellation { transformer.cancel() }
                try {
                    transformer.start(edited, output.absolutePath)
                } catch (failure: Exception) {
                    continuation.resumeWithException(failure)
                }
            }
            check(output.isFile && output.length() > 0L) { "Пустой результат обработки" }
        }
    }

    private fun metadata(file: File): VideoMetadata = MediaMetadataRetriever().run {
        try {
            setDataSource(file.absolutePath)
            VideoMetadata(
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            )
        } finally { release() }
    }

    private data class VideoMetadata(val width: Int, val height: Int, val durationMs: Long)
    private data class TargetProfile(val side: Int, val videoBitrate: Int, val audioBitrate: Int)

    private fun VideoMessageSettings.targetProfile(): TargetProfile = when (preset) {
        VideoQualityPreset.Standard -> TargetProfile(384, 1_000_000, 64_000)
        VideoQualityPreset.High -> TargetProfile(480, 1_400_000, 96_000)
        VideoQualityPreset.Maximum -> TargetProfile(640, 2_500_000, 128_000)
        VideoQualityPreset.Custom -> TargetProfile(customSide, customVideoBitrateKbps * 1_000, customAudioBitrateKbps * 1_000)
    }

    companion object { private const val MAX_SEGMENT_MS = 60_000L }
}
