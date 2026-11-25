package com.beykus.beygram.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.MediaExtractor
import android.media.MediaFormat
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
import com.beykus.beygram.R
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
     * Сбой сегмента не удаляет ни готовые сегменты, ни RAW: долгую запись нельзя терять из-за одной ошибки.
     * Готовый RAW остаётся на диске до приёма кружка TDLib или явного удаления — владелец решает это в
     * [VideoNoteExportCenter].
     */
    suspend fun process(source: File, settings: VideoMessageSettings, outputDirectory: File, diagnosticId: String? = null): List<OutgoingMedia> {
        if (diagnosticId != null) VideoNoteDiagnostics.inspect(diagnosticId, VideoNoteDiagnostics.text(R.string.vn_stage_raw), source)
        val sourceInfo = withContext(Dispatchers.IO) { metadata(source) }
        val sourceDurationMs = sourceInfo.durationMs
        require(sourceDurationMs > 0L) { "Не удалось прочитать длительность записи" }
        val target = settings.targetProfile().let { profile ->
            profile.copy(side = videoNoteOutputSide(profile.side, sourceInfo.width, sourceInfo.height))
        }
        withContext(Dispatchers.IO) { outputDirectory.mkdirs() }
        // CameraX пишет кадр целиком, включая углы вне круга, а TDLib принимает кружок только в H.264: поэтому файл всегда перекодируется.
        val result = mutableListOf<OutgoingMedia>()
        for ((index, segment) in videoNoteSegments(sourceDurationMs, settings.longRecordingEnabled).withIndex()) {
            val (startMs, endMs) = segment
            val output = File(outputDirectory, "video-note-${index + 1}.mp4")
            try {
                if (diagnosticId != null) VideoNoteDiagnostics.append(diagnosticId, VideoNoteDiagnostics.text(R.string.vn_entry_export_segment, index + 1, target.side, target.side, target.videoBitrate, target.audioBitrate))
                val exportStartMs = android.os.SystemClock.elapsedRealtime()
                val exported = transform(source, output, startMs, endMs, target.side, target.videoBitrate, target.audioBitrate)
                if (diagnosticId != null) {
                    VideoNoteDiagnostics.append(diagnosticId, VideoNoteDiagnostics.text(R.string.vn_entry_export_result,
                        exported.videoFrameCount, exported.videoEncoderName ?: "?", exported.audioEncoderName ?: "?",
                        exported.fileSizeBytes, exported.averageVideoBitrate.coerceAtLeast(0) / 1000, exported.width, exported.height))
                    VideoNoteDiagnostics.append(diagnosticId, VideoNoteDiagnostics.text(R.string.vn_entry_export_finished, android.os.SystemClock.elapsedRealtime() - exportStartMs))
                    VideoNoteDiagnostics.inspect(
                        diagnosticId,
                        VideoNoteDiagnostics.text(R.string.vn_stage_final, index + 1),
                        output,
                        output = true,
                        exportFrameCount = exported.videoFrameCount
                    )
                }
                val info = withContext(Dispatchers.IO) { metadata(output) }
                require(info.width == target.side && info.height == target.side) { "Кодировщик изменил размер видеокружка" }
                require(isAcceptedVideoNoteDuration(info.durationMs)) { "Длительность кружка превышает 60 секунд" }
                // A file that TDLib would upload but nobody could watch is not a successful export. The checks run on the
                // real output, so a broken encoder, a missing video track or a black/empty clip fails here with a reason
                // instead of surfacing as a black preview and a dead Send button (#1228).
                val facts = withContext(Dispatchers.IO) { outputFacts(output, target.side, info) }
                val issue = videoNoteOutputIssue(facts)
                if (diagnosticId != null) {
                    VideoNoteDiagnostics.append(diagnosticId, VideoNoteDiagnostics.text(R.string.vn_entry_output_check, facts.decodedFrames, facts.videoMime ?: "?"))
                }
                if (issue != null) {
                    VideoNoteDiagnostics.append(diagnosticId ?: "", VideoNoteDiagnostics.text(R.string.vn_entry_output_rejected, issue.name))
                    output.delete()
                    throw VideoNoteExportException(issue)
                }
                result += outgoingMedia(output, info, index + 1, extractThumbnail(output, File(outputDirectory, "video-note-${index + 1}-thumb.jpg")))
            } catch (failure: Exception) {
                output.delete()
                throw failure
            }
        }
        // The raw recording is kept until the note is accepted or explicitly deleted: a failed delivery must never cost
        // the user the recording, and a broken export can be redone from it (#1228). The export center deletes it.
        return result
    }

    private fun outgoingMedia(file: File, info: VideoMetadata, index: Int, thumbnail: File?) = OutgoingMedia(
        path = file.absolutePath,
        fileName = "video-note-$index.mp4",
        mimeType = "video/mp4",
        kind = MediaKind.VideoNote,
        width = info.width,
        height = info.height,
        durationSeconds = ((info.durationMs + 999L) / 1000L).toInt(),
        thumbnailPath = thumbnail?.absolutePath,
        size = file.length(),
        thumbnailWidth = THUMBNAIL_SIDE,
        thumbnailHeight = THUMBNAIL_SIDE
    )

    /** Кадр для превью кружка в ленте. Неудача не мешает отправке: кружок уйдёт без миниатюры. */
    private suspend fun extractThumbnail(video: File, output: File): File? = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(video.absolutePath)
                val frame = retriever.getFrameAtTime(THUMBNAIL_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@runCatching null
                val scaled = Bitmap.createScaledBitmap(frame, THUMBNAIL_SIDE, THUMBNAIL_SIDE, true)
                output.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, it) }
                if (scaled !== frame) frame.recycle()
                scaled.recycle()
                output
            } finally {
                retriever.release()
            }
        }.getOrNull().also { if (it == null) output.delete() }
    }

    private suspend fun transform(input: File, output: File, startMs: Long, endMs: Long, side: Int, videoBitrate: Int, audioBitrate: Int): ExportResult {
        return withContext(Dispatchers.Main.immediate) {
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
            val exported = suspendCancellableCoroutine<ExportResult> { continuation ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(videoBitrate).build())
                        .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(audioBitrate).build())
                        .build())
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(exportResult)
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
            exported
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

    /**
     * Reads what the checks need from the real output file: the container's video mime type and a few frames that the
     * platform decoder must be able to produce.
     */
    private fun outputFacts(file: File, expectedSide: Int, info: VideoMetadata): VideoNoteOutputFacts {
        val mime = runCatching {
            MediaExtractor().let { extractor ->
                try {
                    extractor.setDataSource(file.absolutePath)
                    (0 until extractor.trackCount)
                        .map { extractor.getTrackFormat(it) }
                        .firstOrNull { (it.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/") }
                        ?.getString(MediaFormat.KEY_MIME)
                } finally {
                    extractor.release()
                }
            }
        }.getOrNull()
        // Three positions, not one: the first frames of a broken export can still decode, while a clip that only has a
        // correct first frame is exactly the "preview is black, send does nothing" case.
        val frames = runCatching {
            MediaMetadataRetriever().let { retriever ->
                try {
                    retriever.setDataSource(file.absolutePath)
                    val duration = info.durationMs.coerceAtLeast(1L)
                    listOf(0L, duration / 2, (duration * 9 / 10).coerceAtLeast(1L))
                        .count { positionMs ->
                            retriever.getFrameAtTime(positionMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) != null
                        }
                } finally {
                    retriever.release()
                }
            }
        }.getOrDefault(0)
        return VideoNoteOutputFacts(
            sizeBytes = file.length(),
            width = info.width,
            height = info.height,
            durationMs = info.durationMs,
            videoMime = mime,
            decodedFrames = frames,
            expectedSide = expectedSide
        )
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

/**
 * What makes an exported clip unusable as a Telegram video note (#1228). [NoDecodableFrame] is the reported symptom: a
 * file that looks fine to the exporter but has nothing the platform can decode — a black preview and a recipient that
 * sees nothing.
 */
internal enum class VideoNoteOutputIssue { EmptyFile, NoDecodableFrame, NotH264, NotSquare, BadDuration }

/** Everything the export check reads from the output file; a plain data holder so the rules are tested without a codec. */
internal data class VideoNoteOutputFacts(
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    /** Container video mime type, or null when the extractor could not answer (then the codec check is skipped). */
    val videoMime: String?,
    val decodedFrames: Int,
    val expectedSide: Int
)

/**
 * The export is accepted only when the file really is a square H.264 video note with decodable frames inside the
 * duration limit. The checks are ordered from "nothing was written" to "the container is wrong", so the reported reason
 * names the first real problem.
 */
internal fun videoNoteOutputIssue(facts: VideoNoteOutputFacts): VideoNoteOutputIssue? = when {
    facts.sizeBytes <= 0L -> VideoNoteOutputIssue.EmptyFile
    facts.decodedFrames <= 0 -> VideoNoteOutputIssue.NoDecodableFrame
    facts.videoMime != null && facts.videoMime != "video/avc" -> VideoNoteOutputIssue.NotH264
    facts.width != facts.expectedSide || facts.height != facts.expectedSide -> VideoNoteOutputIssue.NotSquare
    facts.durationMs !in 1L..VIDEO_NOTE_MAX_MS -> VideoNoteOutputIssue.BadDuration
    else -> null
}

/** An export that failed the checks above; the raw recording is kept, so the note can be exported again. */
internal class VideoNoteExportException(val issue: VideoNoteOutputIssue) :
    IllegalStateException("Видеокружок не прошёл проверку: " + issue.name)

/** Never upscale CameraX SD source after its square crop: the short side limits detail. */
internal fun videoNoteOutputSide(requested: Int, width: Int, height: Int): Int {
    // Metadata may omit the size (some firmware, a just-finalized file). Keep the requested side instead of failing the note.
    if (width <= 0 || height <= 0) return minOf(requested, 640)
    return minOf(requested, width, height, 640)
}

/** Предел длительности кружка в Telegram. */
internal const val VIDEO_NOTE_MAX_MS = 60_000L

/** Сторона миниатюры кружка: TDLib принимает JPEG до 320 px, кадр с запасом меньше 200 КБ. */
private const val THUMBNAIL_SIDE = 320
private const val THUMBNAIL_QUALITY = 85
/** Кадр берётся не с нулевой секунды: первые миллисекунды после старта кодировщика часто чёрные. */
private const val THUMBNAIL_TIME_US = 200_000L

/**
 * Длина сегмента при нарезке. Перекодированный клип выходит длиннее заданного на несколько десятков миллисекунд
 * (кодировщик и контейнер), и клип ровно в 60 секунд не проходил проверку длительности. Режем раньше предела.
 */
internal const val VIDEO_NOTE_SEGMENT_MS = 59_500L

/** Хвост длиннее этого становится отдельным кружком; более короткий отбрасывается. */
internal const val MIN_SEGMENT_TAIL_MS = 500L

/**
 * Границы сегментов [startMs, endMs) в миллисекундах. Обычная запись останавливается таймером на минуте,
 * а CameraX дописывает хвост задержки: он отбрасывается, и получается один кружок. Длинная запись режется подряд.
 */
internal fun videoNoteSegments(sourceMs: Long, longRecording: Boolean): List<Pair<Long, Long>> {
    val totalMs = if (longRecording) sourceMs else minOf(sourceMs, VIDEO_NOTE_SEGMENT_MS)
    val segments = generateSequence(0L) { it + VIDEO_NOTE_SEGMENT_MS }
        .takeWhile { it < totalMs }
        .map { startMs -> startMs to minOf(startMs + VIDEO_NOTE_SEGMENT_MS, totalMs) }
        .toList()
    // A tail this short is too little for a clip of its own, and its failure would drop the finished segments of the recording.
    val (tailStart, tailEnd) = segments.lastOrNull() ?: return segments
    return if (segments.size > 1 && tailEnd - tailStart < MIN_SEGMENT_TAIL_MS) segments.dropLast(1) else segments
}

/** Готовый сегмент принимается, только если его длительность укладывается в предел Telegram. */
internal fun isAcceptedVideoNoteDuration(durationMs: Long): Boolean = durationMs in 1L..VIDEO_NOTE_MAX_MS
