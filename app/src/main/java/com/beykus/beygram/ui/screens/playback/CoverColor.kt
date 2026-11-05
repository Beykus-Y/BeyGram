package com.beykus.beygram.ui.screens.playback

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.beykus.beygram.ui.accessibility.systemReducedMotion
import com.beykus.beygram.ui.theme.BeyGramTokens
import com.beykus.beygram.ui.theme.LocalBeyGramTokens
import com.beykus.beygram.ui.theme.MinAccentContrast
import com.beykus.beygram.ui.theme.MinTextContrast
import com.beykus.beygram.ui.theme.contrastRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Largest share of the cover colour mixed into the mini player. */
internal const val MINI_COVER_TINT = 0.30f

/** Largest share of the cover colour mixed into the full player sheet. */
internal const val FULL_COVER_TINT = 0.40f

private const val SAMPLE_SIZE = 32
private const val BUCKET_LEVELS = 16
private const val BUCKETS = BUCKET_LEVELS * BUCKET_LEVELS * BUCKET_LEVELS
private const val TINT_STEP = 0.02f
private const val CACHED_COVER_COLORS = 256

/**
 * The colour that stands for a cover: the bucket of colourful pixels with the most weight, where a pixel counts more
 * the more saturated it is, so a vivid subject beats a large grey background. Returns an opaque ARGB value, or null
 * when no pixel carries colour (transparent, near-black, near-white or empty art).
 */
internal fun dominantCoverColor(argb: IntArray): Int? {
    val population = IntArray(BUCKETS)
    val sumR = IntArray(BUCKETS)
    val sumG = IntArray(BUCKETS)
    val sumB = IntArray(BUCKETS)
    var usable = 0
    for (pixel in argb) {
        if ((pixel ushr 24) < 128) continue
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // Near-black and near-white pixels are frames, shadows and highlights; they say little about the colour.
        if (maxOf(r, maxOf(g, b)) < 20 || minOf(r, minOf(g, b)) > 235) continue
        val bucket = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
        population[bucket]++
        sumR[bucket] += r
        sumG[bucket] += g
        sumB[bucket] += b
        usable++
    }
    if (usable == 0) return null
    var best = -1
    var bestScore = -1f
    for (bucket in 0 until BUCKETS) {
        val count = population[bucket]
        if (count == 0) continue
        val r = sumR[bucket] / count
        val g = sumG[bucket] / count
        val b = sumB[bucket] / count
        val high = maxOf(r, maxOf(g, b))
        val low = minOf(r, minOf(g, b))
        val saturation = if (high == 0) 0f else (high - low).toFloat() / high
        val score = count * (0.08f + saturation * saturation)
        if (score > bestScore) {
            best = bucket
            bestScore = score
        }
    }
    val count = population[best]
    return (0xFF shl 24) or
        ((sumR[best] / count) shl 16) or
        ((sumG[best] / count) shl 8) or
        (sumB[best] / count)
}

/** Black or white, whichever reads better on [background]. One of the two always reaches [MinTextContrast]. */
internal fun readableInk(background: Color): Color =
    if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) Color.Black else Color.White

/** Colours of a player surface after a cover tint: the surface itself, its primary text and its muted text. */
internal data class CoverSurface(val surface: Color, val text: Color, val muted: Color)

/**
 * Mixes [source] into [base], as far as the theme still holds its contrast. The accent keeps the contrast it had on
 * [base] (up to 3:1), and the tint shrinks until it does. Theme text stays when it still reads on the mixed colour;
 * otherwise black or white takes over. Nothing is ever made less readable than the theme had it on [base].
 */
internal fun coverSurface(
    base: Color,
    source: Color,
    text: Color,
    muted: Color,
    accent: Color,
    maxAmount: Float
): CoverSurface {
    val accentFloor = minOf(MinAccentContrast, contrastRatio(accent, base))
    var amount = maxAmount.coerceIn(0f, 1f)
    while (amount > 0f && contrastRatio(accent, lerp(base, source, amount)) < accentFloor) {
        amount = (amount - TINT_STEP).coerceAtLeast(0f)
    }
    val surface = lerp(base, source, amount)
    val textOnSurface =
        if (contrastRatio(text, surface) >= minOf(MinTextContrast, contrastRatio(text, base))) text
        else readableInk(surface)
    val mutedOnSurface =
        if (contrastRatio(muted, surface) >= minOf(MinTextContrast, contrastRatio(muted, base))) muted
        else textOnSurface
    return CoverSurface(surface, textOnSurface, mutedOnSurface)
}

/** Tokens for a player whose surface follows its cover, and the colour that surface is painted with. */
internal data class CoverTokens(val tokens: BeyGramTokens, val surface: Color)

/**
 * Tokens of a player tinted by the cover of [artworkPath] and [audioPath]. A track without a cover takes the theme
 * accent instead. While the cover is still decoding, the surface keeps the theme colour and then fades to the tint.
 * With [enabled] false the tokens stay the theme's.
 */
@Composable
internal fun rememberCoverTokens(
    artworkPath: String?,
    audioPath: String?,
    enabled: Boolean,
    maxAmount: Float,
    base: Color? = null
): CoverTokens {
    val tokens = LocalBeyGramTokens.current
    val surfaceBase = base ?: tokens.surface
    val key = "${artworkPath.orEmpty()}|${audioPath.orEmpty()}|$enabled"
    val lookup by produceState(initialValue = CoverLookup("", null), key1 = key) {
        value = CoverLookup(key, if (enabled) trackCoverColor(artworkPath, audioPath) else null)
    }
    val ready = enabled && lookup.key == key
    val target = remember(ready, lookup, surfaceBase, tokens, maxAmount) {
        if (ready) {
            coverSurface(surfaceBase, lookup.color ?: tokens.accent, tokens.text, tokens.muted, tokens.accent, maxAmount)
        } else {
            CoverSurface(surfaceBase, tokens.text, tokens.muted)
        }
    }
    val reduced = systemReducedMotion()
    val surface by animateColorAsState(
        targetValue = target.surface,
        animationSpec = tween(durationMillis = if (reduced) 0 else ARTWORK_FADE_MS),
        label = "coverSurface"
    )
    return remember(tokens, surface, target.text, target.muted) {
        CoverTokens(
            tokens = tokens.copy(bg = surface, surface = surface, text = target.text, muted = target.muted),
            surface = surface
        )
    }
}

private data class CoverLookup(val key: String, val color: Color?)

/**
 * The colour of a track's cover, or null when it has none. The file is decoded on IO and sampled on the default
 * dispatcher, so the main thread only reads the result. Colours are cached per cover file.
 */
internal suspend fun trackCoverColor(artworkPath: String?, audioPath: String?): Color? = withContext(Dispatchers.IO) {
    val artFile = artworkPath?.let(::File)?.takeIf { it.isFile }
    val audioFile = audioPath?.let(::File)?.takeIf { it.isFile }
    val key = artworkCacheKey(artFile, audioFile) ?: return@withContext null
    CoverColorCache.get(key)?.let { return@withContext it.color }
    val bitmap = loadTrackArtwork(artworkPath, audioPath)
    val color = bitmap?.let { withContext(Dispatchers.Default) { sampleCoverColor(it) } }
    CoverColorCache.put(key, CachedCoverColor(color))
    color
}

private fun sampleCoverColor(bitmap: Bitmap): Color? {
    val small = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
    return try {
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        small.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        dominantCoverColor(pixels)?.let { Color(it) }
    } finally {
        // The scaled copy is ours to free; the decoded cover is shared with the artwork cache and must stay alive.
        if (small !== bitmap) small.recycle()
    }
}

private class CachedCoverColor(val color: Color?)

/** Colour per cover file, so a track played again does not decode or sample its art again. */
private object CoverColorCache {
    private val cache = LruCache<String, CachedCoverColor>(CACHED_COVER_COLORS)
    @Synchronized fun get(key: String): CachedCoverColor? = cache.get(key)
    @Synchronized fun put(key: String, value: CachedCoverColor) {
        cache.put(key, value)
    }
}
