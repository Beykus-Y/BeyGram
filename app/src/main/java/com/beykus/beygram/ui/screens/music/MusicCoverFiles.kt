package com.beykus.beygram.ui.screens.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.beykus.beygram.data.music.MUSIC_COVER_DIRECTORY
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val COVER_JPEG_QUALITY = 90

/** App-private directory of playlist covers. Only files directly inside it are ever stored as covers. */
internal fun musicCoverRoot(context: Context): File = File(context.applicationContext.filesDir, MUSIC_COVER_DIRECTORY)

/**
 * Copies the picked picture into the cover directory as a reduced JPEG with a new name. The gallery file is never
 * referenced, so the cover keeps working after the picture is moved or deleted. Returns null when the picture cannot be
 * read.
 */
internal suspend fun importMusicCover(context: Context, source: Uri): File? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.applicationContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val sample = coverSampleSize(maxOf(bounds.outWidth, bounds.outHeight), MUSIC_COVER_MAX_SIDE_PX)
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val orientation = resolver.openInputStream(source)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        // Sampling only halves in steps, so the picture may still be larger than the limit: scale it down exactly.
        val scale = minOf(1f, MUSIC_COVER_MAX_SIDE_PX.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = orientationMatrix(orientation).apply { postScale(scale, scale) }
        val upright = if (matrix.isIdentity) decoded else Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, matrix, true
        )
        val root = musicCoverRoot(context)
        root.mkdirs()
        val target = File(root, "${UUID.randomUUID()}.jpg")
        target.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, it) }
        target
    }.onFailure { if (it is CancellationException) throw it }.getOrNull()
}

/**
 * Deletes a cover file the library does not keep, such as a picture imported for a change the repository then refused.
 * The library removes the covers of replaced and deleted playlists itself. Paths outside the cover directory are never
 * touched.
 */
internal suspend fun removeMusicCoverFile(context: Context, path: String?) {
    if (path.isNullOrBlank()) return
    withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path).canonicalFile
            if (file.parentFile == musicCoverRoot(context).canonicalFile) file.delete()
        }.onFailure { if (it is CancellationException) throw it }
    }
}

private fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            postRotate(90f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            postRotate(-90f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
    }
}
