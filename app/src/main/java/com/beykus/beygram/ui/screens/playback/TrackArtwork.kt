package com.beykus.beygram.ui.screens.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.RandomAccessFile
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_COVER_SIZE = 512
private const val MAX_EMBEDDED_BYTES = 8 * 1024 * 1024

/** Small LRU avoids repeatedly decoding artwork for rows and the shared player. */
private object ArtworkCache {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    @Synchronized fun get(key: String): Bitmap? = cache.get(key)
    private val misses = object : LinkedHashMap<String, Boolean>(160, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 160
    }
    @Synchronized fun contains(key: String): Boolean = cache.get(key) != null || misses.containsKey(key)
    @Synchronized fun put(key: String, image: Bitmap?) {
        if (image != null) { cache.put(key, image); misses.remove(key) }
        else misses[key] = true
    }
}

/** Both sources belong to the key: a miss for the cover alone must not hide an audio file that arrives later. */
internal fun artworkCacheKey(artFile: File?, audioFile: File?): String? {
    if (artFile == null && audioFile == null) return null
    return listOf(artFile, audioFile).joinToString("|") { file ->
        file?.let { "${it.absolutePath}:${it.length()}:${it.lastModified()}" } ?: "-"
    }
}

private fun BitmapFactory.Options.finishSampling(): BitmapFactory.Options {
    var sample = 1
    while (outWidth / sample > MAX_COVER_SIZE * 2 || outHeight / sample > MAX_COVER_SIZE * 2) {
        sample *= 2
    }
    return BitmapFactory.Options().apply { inSampleSize = sample }
}

private fun decodeCoverFile(path: String): Bitmap? {
    val file = File(path)
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return BitmapFactory.decodeFile(path, bounds.finishSampling())
}

/** Read a bounded APIC frame from ID3v2 MP3 without asking MediaMetadataRetriever
 * to allocate arbitrarily large embeddedPicture bytes. Other formats rely on TDLib art. */
private fun embeddedCover(path: String): Bitmap? {
    val file = File(path)
    if (!file.isFile) return null
    return RandomAccessFile(file, "r").use { input ->
        if (input.length() < 20) return null
        val header = ByteArray(10)
        input.readFully(header)
        if (header[0].toInt() != 73 || header[1].toInt() != 68 || header[2].toInt() != 51) return null
        val version = header[3].toInt() and 255
        if (version !in 3..4 || header[5].toInt() and 0x80 != 0) return null
        fun syncsafe(data: ByteArray, offset: Int): Long {
            var value = 0L
            for (i in 0 until 4) {
                val part = data[offset + i].toInt() and 255
                if (part >= 128) return -1L
                value = (value shl 7) or part.toLong()
            }
            return value
        }
        fun bigEndian(data: ByteArray, offset: Int): Long {
            var v = 0L
            for (i in 0 until 4) v = (v shl 8) or (data[offset + i].toInt() and 255).toLong()
            return v
        }
        val total = syncsafe(header, 6)
        if (total <= 0L || total > 32L * 1024 * 1024) return null
        val end = minOf(input.length(), total + 10L)
        // Skip extended headers instead of misparsing a position as a frame header.
        if ((header[5].toInt() and 0x40) != 0) return null
        val frameHeader = ByteArray(10)
        while (input.filePointer + 10L <= end) {
            input.readFully(frameHeader)
            val name = String(frameHeader, 0, 4, Charsets.US_ASCII)
            if (!name.all { it.isLetterOrDigit() }) return null
            val size = if (version == 4) syncsafe(frameHeader, 4) else bigEndian(frameHeader, 4)
            if (size <= 0L || input.filePointer + size > end) return null
            if (name == "APIC") {
                if (size > MAX_EMBEDDED_BYTES || size < 8) return null
                val bytes = ByteArray(size.toInt())
                input.readFully(bytes)
                val encoding = bytes[0].toInt() and 255
                if (encoding !in 0..3) return null
                var pos = 1
                // MIME string, followed by picture type, description and the image data.
                while (pos < bytes.size && bytes[pos].toInt() != 0) pos++
                if (pos + 2 >= bytes.size) return null
                pos += 2
                val unit = if (encoding == 1 || encoding == 2) 2 else 1
                while (pos + unit <= bytes.size) {
                    val endOfDescription = if (unit == 1) bytes[pos].toInt() == 0
                        else bytes[pos].toInt() == 0 && bytes[pos + 1].toInt() == 0
                    pos += unit
                    if (endOfDescription) break
                }
                if (pos >= bytes.size) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, pos, bytes.size - pos, bounds)
                if (bounds.outWidth !in 1..20000 || bounds.outHeight !in 1..20000) return null
                return BitmapFactory.decodeByteArray(bytes, pos, bytes.size - pos, bounds.finishSampling())
            }
            input.seek(input.filePointer + size)
        }
        null
    }
}

/** TDLib cover first, then embedded file artwork; missing/corrupt art uses the theme-friendly fallback. */
@Composable
internal fun TrackArtwork(
    artworkPath: String?,
    audioPath: String?,
    modifier: Modifier,
    background: Color,
    tint: Color,
    symbolSize: Dp = 22.dp
) {
    val cover by produceState<Bitmap?>(initialValue = null, key1 = artworkPath, key2 = audioPath) {
        value = withContext(Dispatchers.IO) {
            // mtime/size ensures a just-downloaded TDLib thumbnail invalidates a miss.
            val artFile = artworkPath?.let(::File)?.takeIf { it.isFile }
            val audioFile = audioPath?.let(::File)?.takeIf { it.isFile }
            val key = artworkCacheKey(artFile, audioFile)
            if (key != null && ArtworkCache.contains(key)) ArtworkCache.get(key)
            else {
                val image = runCatching {
                    artFile?.absolutePath?.let(::decodeCoverFile)
                        ?: audioFile?.absolutePath?.let(::embeddedCover)
                }.getOrNull()
                if (key != null) ArtworkCache.put(key, image)
                image
            }
        }
    }
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(background), contentAlignment = Alignment.Center) {
        val bitmap = cover
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = tint, modifier = Modifier.size(symbolSize))
        }
    }
}
