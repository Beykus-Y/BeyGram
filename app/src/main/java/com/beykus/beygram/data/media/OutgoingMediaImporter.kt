package com.beykus.beygram.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.markUploadDirectoryOwner
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

internal class OutgoingMediaImporter(context: Context, private val accountId: String = "legacy") {
    private val app = context.applicationContext
    private val uploads = app.filesDir.resolve("uploads")

    suspend fun prepare(uri: Uri, asDocument: Boolean, squarePhoto: Boolean = false): OutgoingMedia = withContext(Dispatchers.IO) {
        val directory = newUploadDirectory()
        try {
            val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }.orEmpty().substringAfterLast('/').sanitizeFileName()
            val mime = app.contentResolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
            val source = directory.resolve("original").apply { check(mkdirs()) }.resolve(name)
            val input = app.contentResolver.openInputStream(uri) ?: throw UserMessageException(UiText.Res(R.string.err_media_open_failed))
            input.use { from -> source.outputStream().use { to -> copyActive(from, to) } }
            importIntoSameDirectory(source, directory, name, mime, asDocument, squarePhoto)
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    /**
     * Imports a file the app produced itself (camera take, photo editor export). The file is copied
     * into the account's upload staging and then follows exactly the same photo/video pipeline as
     * a picked file. The caller owns [file] and may delete it afterwards.
     */
    suspend fun prepareLocal(file: File, asDocument: Boolean = false, displayName: String? = null): OutgoingMedia = withContext(Dispatchers.IO) {
        val directory = newUploadDirectory()
        try {
            val name = (displayName ?: file.name).sanitizeFileName()
            val mime = mimeTypeFor(name)
            val source = directory.resolve("original").apply { check(mkdirs()) }.resolve(name)
            file.inputStream().use { from -> source.outputStream().use { to -> copyActive(from, to) } }
            importIntoSameDirectory(source, directory, name, mime, asDocument, squarePhoto = false)
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    /**
     * Registers an already encoded editor export without re-compressing it. The editor chose the
     * JPEG/PNG quality and dimensions, so a second lossy pass would only degrade the photo.
     */
    suspend fun importEditedPhoto(
        source: File,
        mimeType: String,
        width: Int,
        height: Int,
        displayName: String? = null
    ): OutgoingMedia = withContext(Dispatchers.IO) {
        val directory = newUploadDirectory()
        try {
            val fallbackName = if (mimeType == "image/png") "photo.png" else "photo.jpg"
            val name = (displayName ?: fallbackName).sanitizeFileName()
            val destination = directory.resolve(name)
            source.inputStream().use { from -> destination.outputStream().use { to -> copyActive(from, to) } }
            if (destination.length() <= 0L) throw UserMessageException(UiText.Res(R.string.err_media_file_empty))
            if (destination.length() > MediaLimits.MAX_PHOTO_BYTES) {
                throw UserMessageException(UiText.Res(R.string.err_media_photo_too_large, listOf(MediaLimits.maxPhotoMegabytes)))
            }
            val thumb = withContext(Dispatchers.IO) { decodeThumbnail(destination) }?.let { decoded ->
                try { writeThumbnail(decoded, directory) } finally { if (!decoded.isRecycled) decoded.recycle() }
            }
            OutgoingMedia(
                destination.absolutePath, name, mimeType, MediaKind.Photo, width.coerceAtLeast(0), height.coerceAtLeast(0),
                thumbnailPath = thumb?.first?.absolutePath, size = destination.length(),
                thumbnailWidth = thumb?.second ?: 0, thumbnailHeight = thumb?.third ?: 0
            )
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    /**
     * Replaces an imported video with the Media3 Transformer output. The compressed file keeps the
     * original upload directory, so removing the attachment still discards both files together.
     * Metadata and the thumbnail are read from the new file; callers keep the original when this
     * fails, so a broken transcode never loses the user's video.
     */
    suspend fun adoptCompressedVideo(original: OutgoingMedia, compressed: File, mime: String = "video/mp4"): OutgoingMedia = withContext(Dispatchers.IO) {
        val directory = File(original.path).parentFile?.canonicalFile
            ?: throw UserMessageException(UiText.Res(R.string.err_media_open_failed))
        val destination = directory.resolve("compressed.mp4")
        if (compressed.canonicalFile != destination) {
            if (!compressed.renameTo(destination)) {
                compressed.inputStream().use { from -> destination.outputStream().use { to -> copyActive(from, to) } }
                compressed.delete()
            }
        }
        if (!destination.isFile || destination.length() <= 0L) throw UserMessageException(UiText.Res(R.string.err_media_file_empty))
        val media = prepareVideo(destination, directory, mime, original.fileName)
        if (media.kind != MediaKind.Video) throw UserMessageException(UiText.Res(R.string.err_video_compress_failed))
        // The caller replaces the pending item first and deletes the old file afterwards, so a
        // crash between adoption and the state swap cannot leave the composer pointing at nothing.
        media
    }

    private fun importIntoSameDirectory(
        source: File,
        directory: File,
        name: String,
        mime: String,
        asDocument: Boolean,
        squarePhoto: Boolean
    ): OutgoingMedia {
        if (source.length() <= 0L) throw UserMessageException(UiText.Res(R.string.err_media_file_empty))
        return when {
            asDocument -> OutgoingMedia(source.absolutePath, name, mime, MediaKind.Document, size = source.length())
            mime.startsWith("image/") -> preparePhoto(source, directory, mime, squarePhoto)
            mime.startsWith("video/") -> prepareVideo(source, directory, mime, name)
            else -> OutgoingMedia(source.absolutePath, name, mime, MediaKind.Document, size = source.length())
        }
    }

    private fun preparePhoto(source: File, directory: File, mime: String, square: Boolean): OutgoingMedia {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = (maxOf(info.size.width, info.size.height) / MediaLimits.MAX_PHOTO_SIDE.toFloat()).coerceAtLeast(1f)
            decoder.setTargetSize((info.size.width / scale).roundToInt().coerceAtLeast(1), (info.size.height / scale).roundToInt().coerceAtLeast(1))
        }
        // A source with transparency becomes a PNG: flattening it onto white would change colors.
        val keepAlpha = bitmap.hasAlpha() && (mime == "image/png" || mime == "image/webp")
        val crop = if (square) minOf(bitmap.width, bitmap.height).let { side -> Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side) } else bitmap
        try {
            // Telegram accepts a maximum aspect ratio of 20; pad extreme panoramas instead of cropping content.
            val width = maxOf(crop.width, ceil(crop.height / MediaLimits.MAX_PHOTO_ASPECT).toInt())
            val height = maxOf(crop.height, ceil(crop.width / MediaLimits.MAX_PHOTO_ASPECT).toInt())
            val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                Canvas(flattened).apply {
                    drawColor(if (keepAlpha) Color.TRANSPARENT else Color.WHITE)
                    drawBitmap(crop, (width - crop.width) / 2f, (height - crop.height) / 2f, null)
                }
                val outputName = if (keepAlpha) "photo.png" else "photo.jpg"
                val output = directory.resolve(outputName)
                output.outputStream().use {
                    check(flattened.compress(if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, MediaLimits.PHOTO_JPEG_QUALITY, it))
                }
                if (output.length() > MediaLimits.MAX_PHOTO_BYTES) {
                    throw UserMessageException(UiText.Res(R.string.err_media_photo_too_large, listOf(MediaLimits.maxPhotoMegabytes)))
                }
                val thumb = writeThumbnail(flattened, directory)
                if (source != output) source.delete()
                return OutgoingMedia(output.absolutePath, output.name, if (keepAlpha) "image/png" else "image/jpeg", MediaKind.Photo,
                    flattened.width, flattened.height, thumbnailPath = thumb.first.absolutePath, size = output.length(),
                    thumbnailWidth = thumb.second, thumbnailHeight = thumb.third)
            } finally { flattened.recycle() }
        } finally { if (crop !== bitmap) crop.recycle(); bitmap.recycle() }
    }

    private fun prepareVideo(source: File, directory: File, mime: String, displayName: String): OutgoingMedia {
        // Other codecs/containers remain available as original files, without a false video format claim.
        val extractor = MediaExtractor()
        val compatible = try {
            extractor.setDataSource(source.absolutePath)
            mime == "video/mp4" && (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
                .filter { it.startsWith("video/") }.let { it.isNotEmpty() && it.all { codec -> codec == "video/avc" } }
        } catch (_: Exception) { false } finally { extractor.release() }
        if (!compatible) return OutgoingMedia(source.absolutePath, displayName, mime, MediaKind.Document, size = source.length())
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
            return OutgoingMedia(source.absolutePath, displayName, mime, MediaKind.Video, width, height, duration,
                thumb?.first?.absolutePath, source.length(), thumb?.second ?: 0, thumb?.third ?: 0)
        } finally { metadata.release() }
    }

    private fun decodeThumbnail(source: File): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = (maxOf(info.size.width, info.size.height) / 640f).coerceAtLeast(1f)
            decoder.setTargetSize((info.size.width / scale).roundToInt().coerceAtLeast(1), (info.size.height / scale).roundToInt().coerceAtLeast(1))
        }
    }.getOrNull()

    private fun writeThumbnail(bitmap: Bitmap, directory: File): Triple<File, Int, Int> {
        val scale = (maxOf(bitmap.width, bitmap.height) / 320f).coerceAtLeast(1f)
        val thumb = Bitmap.createScaledBitmap(bitmap, (bitmap.width / scale).roundToInt().coerceAtLeast(1), (bitmap.height / scale).roundToInt().coerceAtLeast(1), true)
        try {
            val file = directory.resolve("thumbnail.jpg")
            file.outputStream().use { check(thumb.compress(Bitmap.CompressFormat.JPEG, 80, it)) }
            return Triple(file, thumb.width, thumb.height)
        } finally { if (thumb !== bitmap) thumb.recycle() }
    }

    private fun newUploadDirectory(): File = uploads.resolve(UUID.randomUUID().toString()).apply {
        if (!mkdirs()) throw UserMessageException(UiText.Res(R.string.err_media_directory_failed))
        try { markUploadDirectoryOwner(this, accountId) }
        catch (error: Exception) { deleteRecursively(); throw error }
    }

    private fun mimeTypeFor(name: String): String = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        ?: "application/octet-stream"

    private fun String.sanitizeFileName(): String =
        substringAfterLast('/').replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(180).ifBlank { "attachment" }

    private suspend fun copyActive(from: java.io.InputStream, to: java.io.OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            coroutineContext.ensureActive()
            val count = from.read(buffer)
            if (count < 0) break
            to.write(buffer, 0, count)
        }
    }

    suspend fun discard(media: OutgoingMedia) = withContext(Dispatchers.IO) {
        var directory = File(media.path).parentFile?.canonicalFile ?: return@withContext
        if (directory.parentFile?.parentFile == uploads.canonicalFile) directory = directory.parentFile!!
        if (directory.parentFile == uploads.canonicalFile) directory.deleteRecursively()
    }
}
