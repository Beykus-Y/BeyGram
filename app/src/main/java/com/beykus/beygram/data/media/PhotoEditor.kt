package com.beykus.beygram.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Bitmap pipeline for the photo editor. ImageDecoder applies the source EXIF orientation before a
 * single bitmap is handed to the UI, so rotation and cropping always operate on an upright image.
 * JPEG remains JPEG, and a source with transparency is written as PNG instead of being flattened
 * onto white, which would change its colors.
 */
internal class PhotoEditor(private val context: Context) {
    data class Preview(val bitmap: Bitmap, val width: Int, val height: Int)
    data class Export(val file: File, val width: Int, val height: Int, val mimeType: String)

    suspend fun loadPreview(source: File): Preview? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decode(source, PREVIEW_MAX_SIDE)
            Preview(bitmap, bitmap.width, bitmap.height)
        }.getOrNull()
    }

    suspend fun export(
        source: File,
        rotation: PhotoRotation,
        crop: NormalizedCrop,
        quality: PhotoExportQuality
    ): Export = withContext(Dispatchers.IO) {
        val decoded = decode(source, quality.maxSide)
        val hasAlpha = decoded.hasAlpha()
        val rotated = rotate(decoded, rotation)
        val cropRect = cropPixels(crop, rotated.width, rotated.height)
        val cropped = Bitmap.createBitmap(rotated, cropRect.left, cropRect.top, cropRect.width, cropRect.height)
        val scaled = scaledSize(cropped.width, cropped.height, quality.maxSide)
        val resized = if (scaled.width == cropped.width && scaled.height == cropped.height) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, scaled.width, scaled.height, true)
        }
        val target = telegramPaddedSize(resized.width, resized.height)
        val outputBitmap = if (target.width == resized.width && target.height == resized.height) {
            resized
        } else {
            Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888).also { padded ->
                Canvas(padded).apply {
                    drawColor(if (hasAlpha) Color.TRANSPARENT else Color.WHITE)
                    drawBitmap(resized, (target.width - resized.width) / 2f, (target.height - resized.height) / 2f, null)
                }
            }
        }
        try {
            val directory = File(context.cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
            val file = directory.resolve("edited-${UUID.randomUUID()}.${photoExportExtension(hasAlpha)}")
            try {
                val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                file.outputStream().use { output ->
                    check(outputBitmap.compress(format, quality.jpegQuality, output)) { "Cannot encode edited photo" }
                }
                check(file.length() > 0L) { "Empty edited photo" }
            } catch (failure: Throwable) {
                file.delete()
                throw failure
            }
            Export(file, outputBitmap.width, outputBitmap.height, photoExportMimeType(hasAlpha))
        } finally {
            recycleDistinct(decoded, rotated, cropped, resized, outputBitmap)
        }
    }

    /** Preview rotation is a UI concern: it allocates a temporary bitmap that the composition drops. */
    fun rotatePreview(bitmap: Bitmap, rotation: PhotoRotation): Bitmap = rotate(bitmap, rotation)

    private fun decode(source: File, maxSide: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scaled = scaledSize(info.size.width, info.size.height, maxSide)
            decoder.setTargetSize(scaled.width.coerceAtLeast(1), scaled.height.coerceAtLeast(1))
        }

    private fun rotate(source: Bitmap, rotation: PhotoRotation): Bitmap {
        if (rotation == PhotoRotation.None) return source
        val matrix = Matrix().apply { postRotate(rotation.degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun recycleDistinct(vararg bitmaps: Bitmap) {
        val seen = HashSet<Int>()
        bitmaps.forEach { bitmap ->
            if (seen.add(System.identityHashCode(bitmap)) && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    private companion object {
        const val PREVIEW_MAX_SIDE = 1440
        const val EXPORT_DIRECTORY = "photo-edit"
    }
}
