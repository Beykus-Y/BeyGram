package com.beykus.beygram.data.music.bgmusic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/** Outcome of [BgMusicExporter.export]. */
sealed interface BgMusicExportResult {
    /** The archive is complete. It stays in the export folder until the next export replaces it. */
    data class Ready(val file: File) : BgMusicExportResult

    /** The archive could not be written. No partial file is left behind. */
    data object Failed : BgMusicExportResult
}

/**
 * Writes `.bgmusic` archives into `cacheDir/bgmusic_export/`. The folder keeps only the latest archive, because every export
 * removes the earlier ones. The cover is re-encoded as JPEG and kept only while it fits in 1 MiB. A cover that cannot be used
 * is left out instead of failing the export.
 */
object BgMusicExporter {
    /** Name of the cache folder. It must match the `cache-path` entry in res/xml/file_paths.xml. */
    const val EXPORT_DIRECTORY = "bgmusic_export"

    private const val PARTIAL_SUFFIX = ".part"
    private const val COVER_MAX_SIDE_PX = 1024
    private val COVER_QUALITY_STEPS = intArrayOf(90, 75, 60)

    fun exportDirectory(context: Context): File = File(context.cacheDir, EXPORT_DIRECTORY)

    /**
     * Writes [manifest] and the cover at [coverPath]. A cover outside [coverRoot] is ignored, so an export can only read the
     * app's own cover files. Blocking I/O: call it from Dispatchers.IO. Exports run one at a time.
     */
    fun export(
        context: Context,
        manifest: BgMusicManifest,
        coverPath: String?,
        coverRoot: File,
    ): BgMusicExportResult = synchronized(this) {
        val directory = exportDirectory(context)
        val target = File(directory, BgMusicLibraryMapping.fileNameFor(manifest.title))
        val partial = File(directory, target.name + PARTIAL_SUFFIX)
        try {
            directory.listFiles()?.forEach { it.delete() }
            directory.mkdirs()
            val cover = coverJpeg(coverPath, coverRoot)
            FileOutputStream(partial).use { out -> BgMusicWriter.write(manifest, cover, out) }
            if (!partial.renameTo(target)) throw IOException("the export file could not be renamed")
            BgMusicExportResult.Ready(target)
        } catch (invalid: IllegalArgumentException) {
            partial.delete()
            BgMusicExportResult.Failed
        } catch (io: IOException) {
            partial.delete()
            BgMusicExportResult.Failed
        } catch (exhausted: OutOfMemoryError) {
            partial.delete()
            BgMusicExportResult.Failed
        }
    }

    /** The cover as a JPEG of at most 1 MiB, or null when there is no cover or it cannot be used. */
    private fun coverJpeg(coverPath: String?, coverRoot: File): ByteArray? {
        if (coverPath == null) return null
        val source = File(coverPath)
        if (!source.isFile || !isInside(source, coverRoot)) return null
        val decoded = decodeLimited(source) ?: return null
        val flattened = flattenOnWhite(decoded)
        decoded.recycle()
        try {
            for (quality in COVER_QUALITY_STEPS) {
                val bytes = compressJpeg(flattened, quality)
                if (bytes.size.toLong() <= BgMusicFormat.MAX_COVER_BYTES) return bytes
            }
            return null
        } finally {
            flattened.recycle()
        }
    }

    /** Decodes [file] at a reduced sample size when it is large, so a big cover cannot exhaust memory. */
    private fun decodeLimited(file: File): Bitmap? {
        val path = file.absolutePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= COVER_MAX_SIDE_PX) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, options)
    }

    /** Scales the image down to fit [COVER_MAX_SIDE_PX] and paints it over white, because JPEG has no transparency. */
    private fun flattenOnWhite(source: Bitmap): Bitmap {
        val scale = minOf(1f, COVER_MAX_SIDE_PX.toFloat() / maxOf(source.width, source.height))
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, null, Rect(0, 0, width, height), null)
        return output
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun isInside(file: File, root: File): Boolean = try {
        file.canonicalPath.startsWith(root.canonicalPath + File.separator)
    } catch (failure: IOException) {
        false
    }
}
