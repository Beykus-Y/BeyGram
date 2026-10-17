package com.beykus.beygram.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
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
    /**
     * Перекодирует RAW в замаскированные сегменты. RAW может лежать в no_backup, поэтому готовые файлы
     * складываются в [outputDirectory] (filesDir/uploads, рядом с другими вложениями на отправку).
     * При сбое сегмента уже готовые сегменты и RAW сохраняются: долгую запись нельзя терять из-за одной ошибки.
     */
    suspend fun process(source: File, settings: VideoMessageSettings, outputDirectory: File): List<OutgoingMedia> {
        val sourceInfo = withContext(Dispatchers.IO) { metadata(source) }
        val sourceDurationMs = sourceInfo.durationMs
        require(sourceDurationMs > 0L) { "Не удалось прочитать длительность записи" }
        val target = settings.targetProfile()
        withContext(Dispatchers.IO) { outputDirectory.mkdirs() }
        // CameraX пишет кадр целиком, включая углы вне круга, а TDLib принимает кружок только в H.264: поэтому файл всегда перекодируется.
        val result = mutableListOf<OutgoingMedia>()
        for ((index, segment) in videoNoteSegments(sourceDurationMs, settings.longRecordingEnabled).withIndex()) {
            val (startMs, endMs) = segment
            val output = File(outputDirectory, "video-note-${index + 1}.mp4")
            try {
                transform(source, output, startMs, endMs, target.side, target.videoBitrate, target.audioBitrate)
                val info = withContext(Dispatchers.IO) { metadata(output) }
                require(info.width == target.side && info.height == target.side) { "Кодировщик изменил размер видеокружка" }
                require(isAcceptedVideoNoteDuration(info.durationMs)) { "Длительность кружка превышает 60 секунд" }
                result += outgoingMedia(output, info, index + 1)
            } catch (failure: Exception) {
                output.delete()
                throw failure
            }
        }
        source.delete()
        return result
    }

    private fun outgoingMedia(file: File, info: VideoMetadata, index: Int) = OutgoingMedia(
        path = file.absolutePath,
        fileName = "video-note-$index.mp4",
        mimeType = "video/mp4",
        kind = MediaKind.VideoNote,
        width = info.width,
        height = info.height,
        durationSeconds = ((info.durationMs + 999L) / 1000L).toInt(),
        size = file.length()
    )

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
                .setEffects(Effects(emptyList(), listOf(
                    Presentation.createForWidthAndHeight(side, side, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
                    // Кадр за пределами круга очищается: TDLib ожидает «no data outside of the visible circle».
                    OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(circularMask(side))))
                )))
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

    /** Непрозрачный кадр с вырезанным кругом: углы становятся чёрными, данные камеры за пределами круга не сохраняются. */
    private fun circularMask(side: Int): Bitmap {
        val mask = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(mask).apply {
            drawColor(Color.BLACK)
            drawCircle(
                side / 2f,
                side / 2f,
                side / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            )
        }
        return mask
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

}

/** Предел длительности кружка в Telegram. */
internal const val VIDEO_NOTE_MAX_MS = 60_000L

/**
 * Длина сегмента при нарезке. Перекодированный клип выходит длиннее заданного на несколько десятков миллисекунд
 * (кодировщик и контейнер), и клип ровно в 60 секунд не проходил проверку длительности. Режем раньше предела.
 */
internal const val VIDEO_NOTE_SEGMENT_MS = 59_500L

/**
 * Границы сегментов [startMs, endMs) в миллисекундах. Обычная запись останавливается таймером на минуте,
 * а CameraX дописывает хвост задержки: он отбрасывается, и получается один кружок. Длинная запись режется подряд.
 */
internal fun videoNoteSegments(sourceMs: Long, longRecording: Boolean): List<Pair<Long, Long>> {
    val totalMs = if (longRecording) sourceMs else minOf(sourceMs, VIDEO_NOTE_SEGMENT_MS)
    return generateSequence(0L) { it + VIDEO_NOTE_SEGMENT_MS }
        .takeWhile { it < totalMs }
        .map { startMs -> startMs to minOf(startMs + VIDEO_NOTE_SEGMENT_MS, totalMs) }
        .toList()
}

/** Готовый сегмент принимается, только если его длительность укладывается в предел Telegram. */
internal fun isAcceptedVideoNoteDuration(durationMs: Long): Boolean = durationMs in 1L..VIDEO_NOTE_MAX_MS
