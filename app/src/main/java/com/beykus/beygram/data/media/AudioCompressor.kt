package com.beykus.beygram.data.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
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
 * Media3 Transformer wrapper for plain audio files imported into the composer. It never touches
 * voice notes or video notes: those carry a waveform/special TG-to-TDLib contract and are filtered
 * out before this class is called. On any failure the caller keeps the original document.
 */
@OptIn(UnstableApi::class)
internal class AudioCompressor(private val context: Context) {

    suspend fun inspect(file: File, mimeType: String): AudioSourceInfo = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        val audioCodecs = mutableListOf<String>()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) audioCodecs += mime
            }
        } finally {
            extractor.release()
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val hasTags = listOf(
                MediaMetadataRetriever.METADATA_KEY_TITLE,
                MediaMetadataRetriever.METADATA_KEY_ARTIST,
                MediaMetadataRetriever.METADATA_KEY_ALBUM
            ).any { key -> !retriever.extractMetadata(key).isNullOrBlank() }
            val hasArtwork = runCatching { retriever.embeddedPicture != null }.getOrDefault(false)
            AudioSourceInfo(
                durationSeconds = ceil(durationMs / 1000.0).toInt(),
                sizeBytes = file.length(),
                mimeType = mimeType,
                codecAac = audioCodecs.isNotEmpty() && audioCodecs.all { it == MimeTypes.AUDIO_AAC },
                hasMetadata = hasTags || hasArtwork
            )
        } finally {
            retriever.release()
        }
    }

    /**
     * Exports audio-only AAC. Progress and cancellation behave like the video path: a partial file
     * is removed and the caller keeps the original document.
     */
    suspend fun compress(
        source: File,
        output: File,
        plan: AudioCompressionPlan,
        onProgress: (Float) -> Unit
    ): Unit = withContext(Dispatchers.Main.immediate) {
        require(source.canonicalFile != output.canonicalFile) { "Compression output must differ from the source" }
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
                        val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
                            .setRemoveVideo(true)
                            .build()
                        val built = Transformer.Builder(context)
                            .setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .setEncoderFactory(
                                DefaultEncoderFactory.Builder(context)
                                    .setRequestedAudioEncoderSettings(
                                        AudioEncoderSettings.Builder().setBitrate(plan.bitrate).build()
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
            check(output.isFile && output.length() > 0L) { "Empty compressed audio" }
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 250L
    }
}
