package com.beykus.beygram.data.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil

/**
 * Media3 Transformer wrapper for regular composer videos. A failure is not fatal: the caller keeps
 * the original file and explains that it will be sent unchanged. Video notes are never routed here.
 */
@OptIn(UnstableApi::class)
internal class VideoCompressor(private val context: Context) {

    suspend fun inspect(file: File, mimeType: String): VideoSourceInfo = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var hasAudio = false
        val videoCodecs = mutableListOf<String>()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) videoCodecs += mime
                if (mime.startsWith("audio/")) hasAudio = true
            }
        } finally {
            extractor.release()
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            var width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) { val old = width; width = height; height = old }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            VideoSourceInfo(
                width = width,
                height = height,
                durationSeconds = ceil(durationMs / 1000.0).toInt(),
                sizeBytes = file.length(),
                mimeType = mimeType,
                videoCodecH264 = videoCodecs.isNotEmpty() && videoCodecs.all { it == MimeTypes.VIDEO_H264 },
                hasAudio = hasAudio
            )
        } finally {
            retriever.release()
        }
    }

    /**
     * Runs one Transformer export on the main thread (Media3 requirement) and reports progress via
     * [onProgress], 0..1. Cancellation cancels the transformer and removes a partial output file.
     */
    suspend fun compress(
        source: File,
        output: File,
        plan: VideoCompressionPlan,
        info: VideoSourceInfo,
        onProgress: (Float) -> Unit
    ): Unit = withContext(Dispatchers.Main.immediate) {
        output.delete()
        var transformer: Transformer? = null
        try {
            coroutineScope {
                val progressJob = launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        val current = transformer
                        if (current != null) {
                            val state = runCatching { current.getProgress(holder) }.getOrNull()
                            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                            }
                        }
                        delay(PROGRESS_INTERVAL_MS)
                    }
                }
                try {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        val mediaItem = MediaItem.fromUri(Uri.fromFile(source))
                        val edited = EditedMediaItem.Builder(mediaItem)
                            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(plan.targetHeight))))
                            .setRemoveAudio(!info.hasAudio)
                            .build()
                        val built = Transformer.Builder(context)
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .setEncoderFactory(
                                DefaultEncoderFactory.Builder(context)
                                    .setRequestedVideoEncoderSettings(
                                        VideoEncoderSettings.Builder().setBitrate(plan.videoBitrate).build()
                                    )
                                    .setRequestedAudioEncoderSettings(
                                        AudioEncoderSettings.Builder().setBitrate(plan.audioBitrate).build()
                                    )
                                    .build()
                            )
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
                        transformer = built
                        continuation.invokeOnCancellation { built.cancel() }
                        try {
                            built.start(edited, output.absolutePath)
                        } catch (failure: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(failure)
                        }
                    }
                } finally {
                    progressJob.cancel()
                }
            }
            check(output.isFile && output.length() > 0L) { "Empty compressed video" }
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 250L
    }
}
