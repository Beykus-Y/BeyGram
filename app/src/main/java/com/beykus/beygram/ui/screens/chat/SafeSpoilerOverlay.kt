package com.beykus.beygram.ui.screens.chat

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
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

/** Fraction of the texture that is visible as grain on top of the plumes. */
private const val SPOILER_GRAIN_ALPHA = 0.35f

/** Soft low-frequency plumes. Colours stay in the dark violet palette of the mask. */
private val SPOILER_PLUME_COLORS = listOf(
    Color(0xFF3C3058),
    Color(0xFF2E2647),
    Color(0xFF45375F),
    Color(0xFF3C3058)
)

/** Deterministic value in 0..1 derived from the seed only (never from media contents). */
private fun spoilerUnit(seed: Int, salt: Int): Float {
    var h = seed * -0x61c88647 + salt * 0x27d4eb2f
    h = h xor (h ushr 16)
    h *= 0x7feb352d
    h = h xor (h ushr 15)
    return (h and 0xFFFF) / 65535f
}

/**
 * Plumes are radial gradients positioned and sized relative to the current box, so they stay smooth
 * at any size. Nothing here is a bitmap, so there is no stretched texture.
 */
private fun DrawScope.drawSpoilerPlumes(seed: Int) {
    val longest = max(size.width, size.height)
    for (i in SPOILER_PLUME_COLORS.indices) {
        val color = SPOILER_PLUME_COLORS[i]
        val cx = size.width * (0.12f + 0.76f * spoilerUnit(seed, i * 3))
        val cy = size.height * (0.12f + 0.76f * spoilerUnit(seed, i * 3 + 1))
        val radius = longest * (0.35f + 0.30f * spoilerUnit(seed, i * 3 + 2))
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0f)),
                center = Offset(cx, cy),
                radius = radius
            )
        )
    }
}

/**
 * Fine grain: the synthetic 64x64 texture tiled at a constant scale of one texture pixel per dp.
 * Built once per seed and density; the paint is reused for every draw.
 */
private fun spoilerGrainPaint(seed: Int, density: Float): Paint {
    val dim = SAFE_SPOILER_TEXTURE_DIM
    val bitmap = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888).apply {
        setPixels(safeSpoilerTexturePixels(seed), 0, dim, 0, 0, dim, dim)
    }
    val grainShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(Matrix().apply { setScale(density, density) })
    }
    return Paint().apply {
        shader = grainShader
        alpha = (255 * SPOILER_GRAIN_ALPHA).roundToInt()
    }
}

/**
 * Decorative-only component. The parent owns reveal click/accessibility and privacy policy.
 * The mask is fully opaque and never draws any media pixels.
 */
@Composable
internal fun SafeSpoilerOverlay(seed: Int, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val grain = remember(seed, density) { spoilerGrainPaint(seed, density) }
    BoxWithConstraints(
        modifier
            .background(Color(0xFF211B32))
            .drawBehind {
                drawSpoilerPlumes(seed)
                drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, grain)
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.27f)))
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
        if (side >= 140.dp) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White)
                Text(
                    stringResource(R.string.media_spoiler_title),
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.media_spoiler_reveal),
                    color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        } else if (side >= 56.dp) {
            Box(
                Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * Size of a media rectangle fitted inside a container, keeping the media aspect ratio.
 * Uses only the media metadata width and height; unknown sizes fall back to 3:4.
 */
internal fun spoilerFitSize(containerW: Float, containerH: Float, mediaW: Int, mediaH: Int): Pair<Float, Float> {
    val ratio = if (mediaW > 0 && mediaH > 0) (mediaW.toFloat() / mediaH).coerceIn(0.2f, 5f) else 3f / 4f
    return if (containerW / containerH > ratio) {
        containerH * ratio to containerH
    } else {
        containerW to containerW / ratio
    }
}
