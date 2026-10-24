package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageEffect
import com.beykus.beygram.ui.accessibility.systemReducedMotion
import com.beykus.beygram.ui.components.beyGramPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** How long one decorative effect may stay above its message. */
private const val EFFECT_VISIBLE_MILLIS = 3_200L
private const val EFFECT_RAMP_MILLIS = 3_000
private val AnimationEffectSize = 220.dp
private val StaticEffectSize = 112.dp

/**
 * A single effect at a time is enough to stay decorative: a burst of simultaneous arrivals must not stack Lottie
 * animations over the list. The winner is released when its overlay finishes or leaves composition.
 */
internal class MessageEffectPlaybackLimiter(private val maxConcurrent: Int = 1) {
    private val active = mutableSetOf<Long>()

    @Synchronized
    fun tryAcquire(messageId: Long): Boolean {
        if (messageId in active) return true
        if (active.size >= maxConcurrent) return false
        active += messageId
        return true
    }

    @Synchronized
    fun release(messageId: Long) {
        active -= messageId
    }

    @Synchronized
    internal fun activeCount(): Int = active.size
}

/**
 * Wraps one message row with an optional decorative effect. Only a row that [animate] marks as a fresh arrival asks
 * TDLib for effect metadata, so opening a chat with old effects never starts requests or animations. The overlay is
 * measured with `matchParentSize`, which keeps the list height, scrolling and the clickable bubble untouched.
 */
@Composable
internal fun MessageEffectFrame(
    effectId: Long,
    messageId: Long,
    animate: Boolean,
    observeEffect: (Long) -> StateFlow<MessageEffect?>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    limiter: MessageEffectPlaybackLimiter,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!animate || effectId == 0L) {
        content()
        return
    }
    val preferences = LocalChatAnimationPreferences.current
    val enabled = preferences.messageEffects && !systemReducedMotion()
    var effect by remember(effectId) { mutableStateOf<MessageEffect?>(null) }
    var visible by remember(effectId) { mutableStateOf(false) }

    LaunchedEffect(effectId, enabled) {
        effect = null
        if (!enabled) return@LaunchedEffect
        observeEffect(effectId).collect { effect = it }
    }
    LaunchedEffect(effect, enabled, animate) {
        if (!enabled || !animate || effect == null) {
            visible = false
            return@LaunchedEffect
        }
        if (!limiter.tryAcquire(messageId)) return@LaunchedEffect
        try {
            visible = true
            delay(EFFECT_VISIBLE_MILLIS)
        } finally {
            visible = false
            limiter.release(messageId)
        }
    }
    DisposableEffect(messageId) { onDispose { limiter.release(messageId) } }

    Box(modifier.fillMaxWidth()) {
        content()
        val current = effect
        if (visible && current != null) {
            MessageEffectOverlay(current, observeFile, downloadFile, Modifier.matchParentSize())
        }
    }
}

@Composable
private fun MessageEffectOverlay(
    effect: MessageEffect,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    modifier: Modifier
) {
    val progress = remember(effect.effectId) { Animatable(0f) }
    LaunchedEffect(effect.effectId) {
        progress.animateTo(1f, tween(durationMillis = EFFECT_RAMP_MILLIS, easing = LinearEasing))
    }
    val p = progress.value
    val alpha = effectAlpha(p)
    val size = if (effect.animation != null) AnimationEffectSize else StaticEffectSize
    Box(modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Box(
            Modifier.requiredSize(size).clipToBounds().graphicsLayer {
                this.alpha = alpha
                val scale = 0.82f + 0.18f * p
                scaleX = scale
                scaleY = scale
            }
        ) {
            EffectSticker(effect, observeFile, downloadFile, p, Modifier.fillMaxSize())
        }
    }
}

/** Fade in quickly, hold while the sticker plays, then fade out before the row releases the effect slot. */
private fun effectAlpha(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return when {
        p < 0.08f -> p / 0.08f
        p > 0.82f -> ((1f - p) / 0.18f).coerceIn(0f, 1f)
        else -> 1f
    }
}

@Composable
private fun EffectSticker(
    effect: MessageEffect,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    progress: Float,
    modifier: Modifier
) {
    val preferred = effect.primarySticker
    // A premium effect can keep its ordinary sticker as a fallback; prefer any supported file over WebM.
    val sticker = preferred?.takeIf { it.mimeType != WEBM_MIME }
        ?: effect.fallbackSticker?.takeIf { it.mimeType != WEBM_MIME }
        ?: preferred
    if (sticker == null) {
        EffectFallback(effect, progress, modifier)
        return
    }
    val file by remember(sticker.fileId) { observeFile(sticker.fileId) }.collectAsStateWithLifecycle()
    LaunchedEffect(sticker.fileId, sticker.mimeType) {
        if (sticker.mimeType != WEBM_MIME) downloadFile(sticker.fileId)
    }
    val path = file.localPath
    when {
        path != null && sticker.mimeType == TGS_MIME -> Box(modifier) {
            EffectGlow(progress, Modifier.fillMaxSize())
            // The first frame of a TGS effect is empty often enough that a plain fallback would flash behind it.
            TgsSticker(path, Modifier.fillMaxSize(), playing = true, iterations = 1, fallback = {
                EffectFallback(effect, progress, Modifier.fillMaxSize())
            })
        }
        path != null && sticker.mimeType == WEBP_MIME -> Box(modifier, contentAlignment = Alignment.Center) {
            EffectGlow(progress, Modifier.fillMaxSize())
            LocalMediaImage(path, modifier, fit = true)
        }
        // WebM alpha stickers are unsupported on this renderer, and a missing/undownloaded file has no picture yet.
        else -> EffectFallback(effect, progress, modifier)
    }
}

@Composable
private fun EffectGlow(progress: Float, modifier: Modifier) {
    val palette = beyGramPalette()
    val p = progress.coerceIn(0f, 1f)
    Canvas(modifier) {
        drawCircle(
            color = palette.accent.copy(alpha = 0.16f * (1f - p)),
            radius = size.minDimension * (0.18f + 0.34f * p),
            center = center
        )
    }
}

/**
 * No network file is needed for a visible effect: deterministic particles and the sender's emoji rise and fade from
 * the message. This is also the WebM/missing-file fallback, so an effect never leaves an empty or blocked bubble.
 */
@Composable
private fun EffectFallback(effect: MessageEffect, progress: Float, modifier: Modifier) {
    val palette = beyGramPalette()
    val p = progress.coerceIn(0f, 1f)
    val particles = remember(effect.effectId, effect.emoji) { effectParticles(effect) }
    val colors = listOf(palette.accent, palette.accentInk, palette.messageText)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = palette.accent.copy(alpha = 0.14f * (1f - p)),
                radius = size.minDimension * (0.16f + 0.4f * p),
                center = center
            )
            particles.forEachIndexed { index, particle ->
                val distance = size.minDimension * (0.08f + 0.44f * p * particle.distance)
                val point = Offset(
                    x = center.x + cos(particle.angle) * distance,
                    y = center.y + sin(particle.angle) * distance - size.minDimension * 0.06f * p
                )
                drawCircle(
                    color = colors[index % colors.size].copy(alpha = (1f - p) * 0.9f),
                    radius = particle.radius.dp.toPx(),
                    center = point
                )
            }
        }
        val glyphScale = 0.7f + 0.3f * p
        Text(
            effect.emoji.ifBlank { "✨" },
            color = palette.messageText,
            fontSize = 42.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.graphicsLayer {
                alpha = (1f - p * 0.35f).coerceIn(0f, 1f)
                scaleX = glyphScale
                scaleY = glyphScale
            }
        )
    }
}

private data class EffectParticle(val angle: Float, val distance: Float, val radius: Float)

private fun effectParticles(effect: MessageEffect): List<EffectParticle> {
    val seed = effect.effectId xor effect.emoji.hashCode().toLong()
    return List(18) { index ->
        val angleBits = (seed ushr ((index % 15) * 3)) and 0xFF
        val distanceBits = (seed ushr (((index + 5) % 15) * 3)) and 0x7F
        val sizeBits = (seed ushr (((index + 9) % 15) * 3)) and 0x1F
        EffectParticle(
            angle = angleBits / 255f * (2f * PI.toFloat()),
            distance = 0.35f + distanceBits / 127f * 0.65f,
            radius = 1.2f + sizeBits / 31f * 2.8f
        )
    }
}
