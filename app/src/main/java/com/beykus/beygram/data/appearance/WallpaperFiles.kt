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
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0
            val upright = if (rotation == 0) decoded else Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true
            )
            val name = "wallpaper_${System.currentTimeMillis()}.jpg"
            File(dir(context), name).outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            name
        }.getOrNull()
    }

    /** Removes every stored picture except [keep]. */
    suspend fun deleteAllExcept(context: Context, keep: String?) = withContext(Dispatchers.IO) {
        dir(context).listFiles()?.filter { it.name != keep }?.forEach { it.delete() }
    }

    /** Decodes the stored picture for drawing. Returns null when the file is gone or damaged. */
    suspend fun load(context: Context, fileName: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = file(context, fileName) ?: return@withContext null
        runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
    }

    private fun exifRotation(stream: java.io.InputStream): Int =
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}
