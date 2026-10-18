package com.beykus.beygram.data.appearance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WALLPAPER_DIR = "wallpapers"
private const val MAX_SIDE_PX = 1600
private const val JPEG_QUALITY = 85

/** Keeps wallpaper pictures as reduced local copies, so the chat never depends on the gallery file. */
object WallpaperFiles {
    private fun dir(context: Context) = File(context.applicationContext.filesDir, WALLPAPER_DIR).apply { mkdirs() }

    fun file(context: Context, fileName: String): File? =
        if (isSafeWallpaperFileName(fileName)) File(dir(context), fileName).takeIf { it.isFile } else null

    /** Decodes a reduced copy of [uri] and stores it under a new name. Returns null when the picture cannot be read. */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.applicationContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val longSide = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longSide / (sample * 2) >= MAX_SIDE_PX) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            // Sampling only halves in steps, so the picture can still be up to twice the limit: scale it down exactly.
            val scale = minOf(1f, MAX_SIDE_PX.toFloat() / maxOf(decoded.width, decoded.height))
            val matrix = orientationMatrix(orientation).apply { postScale(scale, scale) }
            val upright = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val name = "wallpaper_${System.currentTimeMillis()}.jpg"
            File(dir(context), name).outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            name
        }.getOrNull()
    }

    /** Removes every stored picture except those named in [keep]. */
    suspend fun deleteAllExcept(context: Context, keep: Set<String>) = withContext(Dispatchers.IO) {
        dir(context).listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    /** Decodes the stored picture for drawing. Returns null when the file is gone or damaged. */
    suspend fun load(context: Context, fileName: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = file(context, fileName) ?: return@withContext null
        runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
    }

    private fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
        }
    }
}
