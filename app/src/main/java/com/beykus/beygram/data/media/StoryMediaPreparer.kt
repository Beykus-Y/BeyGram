package com.beykus.beygram.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.storyCropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil

internal data class PreparedStoryMedia(
    val media: OutgoingMedia,
    val videoTrimmed: Boolean = false
)

/**
 * TDLib requires a photo story to be exactly 1080x1920 and a video story to be a 720x1280
 * MPEG-4/H.265 file of at most 60 seconds. Gallery files usually match none of those rules,
 * so the publisher converts them before PostStory; uploads that cannot be converted are
 * reported instead of being sent in an invalid format.
 */
@OptIn(UnstableApi::class)
internal class StoryMediaPreparer(private val context: Context) {
    suspend fun prepare(media: OutgoingMedia): PreparedStoryMedia = when (media.kind) {
        MediaKind.Photo -> PreparedStoryMedia(preparePhoto(media))
        MediaKind.Video -> prepareVideo(media)
        else -> throw UserMessageException(UiText.Res(R.string.stories_media_invalid))
    }

    private suspend fun preparePhoto(media: OutgoingMedia): OutgoingMedia = withContext(Dispatchers.IO) {
        val source = File(media.path)
        if (!source.isFile) throw UserMessageException(UiText.Res(R.string.stories_media_prepare_failed))
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_PHOTO_SOURCE_SIDE) {
                val scale = MAX_PHOTO_SOURCE_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1)
                )
            }
        }
        try {
            val crop = storyCropRect(bitmap.width, bitmap.height)
            if (crop.width <= 0 || crop.height <= 0) {
                throw UserMessageException(UiText.Res(R.string.stories_media_prepare_failed))
            }
            val cropped = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width, crop.height)
            try {
                val scaled = Bitmap.createScaledBitmap(cropped, STORY_PHOTO_WIDTH, STORY_PHOTO_HEIGHT, true)
                try {
                    val output = File(uploadRoot(source), STORY_PHOTO_NAME)
                    writePhoto(scaled, output)
                    OutgoingMedia(
                        path = output.absolutePath,
                        fileName = output.name,
                        mimeType = "image/jpeg",
                        kind = MediaKind.Photo,
                        width = STORY_PHOTO_WIDTH,
                        height = STORY_PHOTO_HEIGHT,
                        size = output.length()
                    )
                } finally {
                    if (scaled !== cropped) scaled.recycle()
                }
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writePhoto(bitmap: Bitmap, output: File) {
        var quality = 90
        while (quality >= 60) {
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)) }
            if (output.length() <= MAX_PHOTO_BYTES) return
            quality -= 15
        }
        output.delete()
        throw UserMessageException(UiText.Res(R.string.stories_media_prepare_failed))
    }

    private suspend fun prepareVideo(media: OutgoingMedia): PreparedStoryMedia {
        val source = File(media.path)
        if (!source.isFile) throw UserMessageException(UiText.Res(R.string.stories_media_prepare_failed))
        val sourceDurationMs = withContext(Dispatchers.IO) {
            if (media.durationSeconds > 0) media.durationSeconds * 1_000L else videoMetadata(source)?.durationMs ?: 0L
        }
        val trim = sourceDurationMs > MAX_STORY_VIDEO_MS
        val output = File(withContext(Dispatchers.IO) { uploadRoot(source) }, STORY_VIDEO_NAME)
        try {
            transform(source, output, if (trim) MAX_STORY_VIDEO_MS else null)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            output.delete()
            throw cancelled
        } catch (failure: Exception) {
            output.delete()
            throw UserMessageException(UiText.Res(R.string.stories_video_prepare_failed), failure)
        }
        val info = withContext(Dispatchers.IO) { videoMetadata(output) }
        if (info == null || info.width != STORY_VIDEO_WIDTH || info.height != STORY_VIDEO_HEIGHT || info.durationMs <= 0L) {
            output.delete()
            throw UserMessageException(UiText.Res(R.string.stories_video_prepare_failed))
        }
        if (info.codec != MimeTypes.VIDEO_H265) {
            output.delete()
            throw UserMessageException(UiText.Res(R.string.stories_video_prepare_failed))
        }
        if (info.durationMs > MAX_STORY_VIDEO_MS + 1_000L) {
            output.delete()
            throw UserMessageException(UiText.Res(R.string.stories_video_prepare_failed))
        }
        val durationSeconds = ceil(info.durationMs / 1_000.0).toInt().coerceIn(1, 60)
        return PreparedStoryMedia(
            media = OutgoingMedia(
                path = output.absolutePath,
                fileName = output.name,
                mimeType = "video/mp4",
                kind = MediaKind.Video,
                width = STORY_VIDEO_WIDTH,
                height = STORY_VIDEO_HEIGHT,
                durationSeconds = durationSeconds,
                thumbnailPath = media.thumbnailPath,
                size = output.length(),
                thumbnailWidth = media.thumbnailWidth,
                thumbnailHeight = media.thumbnailHeight
            ),
            videoTrimmed = trim || info.durationMs > MAX_STORY_VIDEO_MS
        )
    }

    private suspend fun transform(source: File, output: File, trimAtMs: Long?): ExportResult =
        withContext(Dispatchers.Main.immediate) {
            output.delete()
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(source))
                .apply {
                    if (trimAtMs != null) {
                        setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setEndPositionMs(trimAtMs)
                                .build()
                        )
                    }
                }
                .build()
            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(emptyList(), listOf(
                    Presentation.createForWidthAndHeight(
                        STORY_VIDEO_WIDTH,
                        STORY_VIDEO_HEIGHT,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                    )
                )))
                .build()
            suspendCancellableCoroutine<ExportResult> { continuation ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H265)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder().setBitrate(STORY_VIDEO_BITRATE).build()
                            )
                            .setRequestedAudioEncoderSettings(
                                AudioEncoderSettings.Builder().setBitrate(STORY_AUDIO_BITRATE).build()
                            )
                            .build()
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(
                            composition: androidx.media3.transformer.Composition,
                            exportResult: ExportResult
                        ) {
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
            }.also {
                check(output.isFile && output.length() > 0L) { "empty story video" }
            }
        }

    private fun uploadRoot(source: File): File {
        val parent = source.parentFile ?: throw UserMessageException(UiText.Res(R.string.stories_media_prepare_failed))
        return if (parent.name == "original" && parent.parentFile != null) parent.parentFile!! else parent
    }

    private fun videoMetadata(file: File): VideoMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            VideoMetadata(
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                codec = videoCodec(file)
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun videoCodec(file: File): String {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount)
                .mapNotNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
                .firstOrNull { it.startsWith("video/") }
                .orEmpty()
        } catch (_: Exception) {
            ""
        } finally {
            extractor.release()
        }
    }

    private data class VideoMetadata(val width: Int, val height: Int, val durationMs: Long, val codec: String)

    private companion object {
        const val STORY_PHOTO_WIDTH = 1080
        const val STORY_PHOTO_HEIGHT = 1920
        const val STORY_VIDEO_WIDTH = 720
        const val STORY_VIDEO_HEIGHT = 1280
        const val MAX_PHOTO_SOURCE_SIDE = 2560
        const val MAX_PHOTO_BYTES = 10L * 1024 * 1024
        const val MAX_STORY_VIDEO_MS = 60_000L
        const val STORY_VIDEO_BITRATE = 2_500_000
        const val STORY_AUDIO_BITRATE = 128_000
        const val STORY_PHOTO_NAME = "story.jpg"
        const val STORY_VIDEO_NAME = "story.mp4"
    }
}
