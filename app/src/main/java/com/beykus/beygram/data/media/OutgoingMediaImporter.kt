package com.beykus.beygram.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class OutgoingMediaImporter(context: Context) {
    private val app = context.applicationContext
    private val uploads = app.filesDir.resolve("uploads")

    suspend fun prepare(uri: Uri, asDocument: Boolean, squarePhoto: Boolean = false): OutgoingMedia = withContext(Dispatchers.IO) {
        val directory = uploads.resolve(UUID.randomUUID().toString()).apply { if (!mkdirs()) throw UserMessageException(UiText.Res(R.string.err_media_directory_failed)) }
        try {
            val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }.orEmpty().substringAfterLast('/').replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(180).ifBlank { "attachment" }
            val mime = app.contentResolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
            val source = directory.resolve("original").apply { check(mkdirs()) }.resolve(name)
            val input = app.contentResolver.openInputStream(uri) ?: throw UserMessageException(UiText.Res(R.string.err_media_open_failed))
            input.use { from -> source.outputStream().use { to ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = from.read(buffer)
                    if (count < 0) break
                    to.write(buffer, 0, count)
                }
            } }
            if (source.length() <= 0L) throw UserMessageException(UiText.Res(R.string.err_media_file_empty))
            when {
                asDocument -> OutgoingMedia(source.absolutePath, name, mime, MediaKind.Document, size = source.length())
                mime.startsWith("image/") -> preparePhoto(source, directory, squarePhoto)
                mime.startsWith("video/") -> prepareVideo(source, directory, mime)
                else -> OutgoingMedia(source.absolutePath, name, mime, MediaKind.Document, size = source.length())
            }
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    private fun preparePhoto(source: File, directory: File, square: Boolean): OutgoingMedia {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = (maxOf(info.size.width, info.size.height) / 2560f).coerceAtLeast(1f)
            decoder.setTargetSize((info.size.width / scale).roundToInt().coerceAtLeast(1), (info.size.height / scale).roundToInt().coerceAtLeast(1))
        }
        val crop = if (square) minOf(bitmap.width, bitmap.height).let { side -> Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side) } else bitmap
        try {
            // JPEG photo mode is compatible with Telegram; file mode preserves the original bytes.
            // Telegram accepts a maximum aspect ratio of 20; pad extreme panoramas.
            val width = maxOf(crop.width, ceil(crop.height / 20.0).toInt())
            val height = maxOf(crop.height, ceil(crop.width / 20.0).toInt())
            val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                Canvas(flattened).apply { drawColor(Color.WHITE); drawBitmap(crop, (width - crop.width) / 2f, (height - crop.height) / 2f, null) }
                val output = directory.resolve("photo.jpg")
                output.outputStream().use { check(flattened.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
                val limitMb = 10
                if (output.length() > limitMb * 1024 * 1024) throw UserMessageException(UiText.Res(R.string.err_media_photo_too_large, listOf(limitMb)))
                val thumb = writeThumbnail(flattened, directory)
                if (source != output) source.delete()
                return OutgoingMedia(output.absolutePath, output.name, "image/jpeg", MediaKind.Photo, flattened.width, flattened.height,
                    thumbnailPath = thumb.first.absolutePath, size = output.length(), thumbnailWidth = thumb.second, thumbnailHeight = thumb.third)
            } finally { flattened.recycle() }
        } finally { if (crop !== bitmap) crop.recycle(); bitmap.recycle() }
    }

    private fun prepareVideo(source: File, directory: File, mime: String): OutgoingMedia {
        // Other codecs/containers remain available as original files, without a false video format claim.
        val extractor = MediaExtractor()
        val compatible = try {
            extractor.setDataSource(source.absolutePath)
            mime == "video/mp4" && (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
                .filter { it.startsWith("video/") }.let { it.isNotEmpty() && it.all { codec -> codec == "video/avc" } }
        } catch (_: Exception) { false } finally { extractor.release() }
        if (!compatible) return OutgoingMedia(source.absolutePath, source.name, mime, MediaKind.Document, size = source.length())
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(source.absolutePath)
            var width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) { val old = width; width = height; height = old }
            val duration = ceil((metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000.0).toInt()
            val frame = runCatching { metadata.getFrameAtTime(0) }.getOrNull()
            val thumb = frame?.let { try { writeThumbnail(it, directory) } finally { it.recycle() } }
            return OutgoingMedia(source.absolutePath, source.name, mime, MediaKind.Video, width, height, duration,
                thumb?.first?.absolutePath, source.length(), thumb?.second ?: 0, thumb?.third ?: 0)
        } finally { metadata.release() }
    }

    private fun writeThumbnail(bitmap: Bitmap, directory: File): Triple<File, Int, Int> {
        val scale = (maxOf(bitmap.width, bitmap.height) / 320f).coerceAtLeast(1f)
        val thumb = Bitmap.createScaledBitmap(bitmap, (bitmap.width / scale).roundToInt().coerceAtLeast(1), (bitmap.height / scale).roundToInt().coerceAtLeast(1), true)
        try {
            val file = directory.resolve("thumbnail.jpg")
            file.outputStream().use { check(thumb.compress(Bitmap.CompressFormat.JPEG, 80, it)) }
            return Triple(file, thumb.width, thumb.height)
        } finally { if (thumb !== bitmap) thumb.recycle() }
    }

    suspend fun discard(media: OutgoingMedia) = withContext(Dispatchers.IO) {
        var directory = File(media.path).parentFile?.canonicalFile ?: return@withContext
        if (directory.parentFile?.parentFile == uploads.canonicalFile) directory = directory.parentFile!!
        if (directory.parentFile == uploads.canonicalFile) directory.deleteRecursively()
    }
}
