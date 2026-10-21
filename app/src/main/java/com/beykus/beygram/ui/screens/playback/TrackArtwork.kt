package com.beykus.beygram.ui.screens.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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
    @Synchronized fun put(key: String, image: Bitmap) { cache.put(key, image) }
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

private fun embeddedCover(path: String): Bitmap? {
    if (!File(path).isFile) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        val bytes = retriever.embeddedPicture ?: return null
        if (bytes.size > MAX_EMBEDDED_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null
        else BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds.finishSampling())
    } finally {
        retriever.release()
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
            val key = if (artworkPath != null) "cover:" + artworkPath else audioPath?.let { "audio:" + it }
            key?.let { ArtworkCache.get(it) } ?: runCatching {
                artworkPath?.let(::decodeCoverFile) ?: audioPath?.let(::embeddedCover)
            }.getOrNull()?.also { image -> if (key != null) ArtworkCache.put(key, image) }
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
