package com.beykus.beygram.ui.screens.chat

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import kotlin.math.cos
import kotlin.math.sin

/** Strict upper bound for the synthetic spoiler texture: it never contains real media pixels. */
internal const val SAFE_SPOILER_TEXTURE_DIM = 64

/**
 * This is deliberately NOT a downscaled/blurred photo. Even 64x64 real content can
 * betray text, faces or silhouettes. A deterministic opaque 64x64 abstract texture
 * gives a soft, blurred-media appearance with zero source-image geometry.
 *
 * No network access, media decode, disk cache or original-thumbnail input is involved.
 */
internal fun safeSpoilerTexturePixels(seed: Int): IntArray {
    val dim = SAFE_SPOILER_TEXTURE_DIM
    val pixels = IntArray(dim * dim)
    val shiftX = (seed and 0xFF) / 255f
    val shiftY = ((seed ushr 8) and 0xFF) / 255f
    for (y in 0 until dim) {
        for (x in 0 until dim) {
            val u = x.toFloat() / dim
            val v = y.toFloat() / dim
            // Soft, low-frequency synthetic plumes; no edge from any photograph.
            val plume = (sin((u + shiftX) * 6.283185f) +
                cos((v + shiftY) * 6.283185f) +
                sin((u + v) * 4.4f)) / 3f
            val grain = (((x * 73856093) xor (y * 19349663) xor seed) and 15) - 7
            val r = (47f + 13f * plume + grain * 0.42f).toInt().coerceIn(0, 255)
            val g = (37f + 10f * plume + grain * 0.38f).toInt().coerceIn(0, 255)
            val b = (68f + 18f * plume + grain * 0.50f).toInt().coerceIn(0, 255)
            pixels[y * dim + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return pixels
}

/** Decorative-only component. The parent owns reveal click/accessibility and privacy policy. */
@Composable
internal fun SafeSpoilerOverlay(seed: Int, modifier: Modifier = Modifier) {
    val texture = remember(seed) {
        Bitmap.createBitmap(SAFE_SPOILER_TEXTURE_DIM, SAFE_SPOILER_TEXTURE_DIM, Bitmap.Config.ARGB_8888).apply {
            setPixels(safeSpoilerTexturePixels(seed), 0, SAFE_SPOILER_TEXTURE_DIM, 0, 0,
                SAFE_SPOILER_TEXTURE_DIM, SAFE_SPOILER_TEXTURE_DIM)
        }.asImageBitmap()
    }
    Box(modifier.background(Color(0xFF211B32)), contentAlignment = Alignment.Center) {
        Image(
            bitmap = texture, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.27f)))
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White)
            Text(
                stringResource(R.string.media_spoiler_title),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.media_spoiler_reveal),
                color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
