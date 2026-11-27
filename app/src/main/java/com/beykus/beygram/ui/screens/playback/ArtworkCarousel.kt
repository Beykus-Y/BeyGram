package com.beykus.beygram.ui.screens.playback

import androidx.compose.runtime.Immutable
import com.beykus.beygram.playback.PlaybackItem
import kotlin.math.abs

/**
 * Spatial transform for a single card in the continuous artwork carousel.
 *
 * [slotPosition] is relative to the viewport center:
 * - `0f`: centered active artwork
 * - `+1f`: resting position of the next track's artwork (right)
 * - `-1f`: resting position of the previous track's artwork (left)
 */
@Immutable
internal data class CarouselCardTransform(
    val translationX: Float,
    val scale: Float,
    val rotationY: Float,
    val alpha: Float,
    val zIndex: Float,
)

/**
 * Pending track switch whose visual animation has already carried the incoming artwork to center
 * (or is finishing its settle), awaiting [PlaybackEngine] to publish the same occurrence.
 */
@Immutable
internal data class PendingSwipeCommit(
    /** Queue index of the shown occurrence: two rows may carry the same track key. */
    val targetIndex: Int,
    val direction: Int,
    val incomingItem: PlaybackItem,
    val incomingProviderCover: String?,
)

/**
 * Computes deterministic 3D-like perspective transform for a carousel card at [slotPosition].
 * At `slotPosition == 0f`, the card sits at exact center (`translationX = 0f`, `scale = 1f`,
 * `rotationY = 0f`, `alpha = 1f`, `zIndex = 1f`), ensuring seamless handoff without a second pop-in.
 */
internal fun carouselCardTransform(
    slotPosition: Float,
    travelDistancePx: Float,
    reducedMotion: Boolean,
): CarouselCardTransform {
    val absS = abs(slotPosition).coerceIn(0f, 1.25f)
    if (reducedMotion) {
        return CarouselCardTransform(
            translationX = 0f,
            scale = 1f,
            rotationY = 0f,
            alpha = if (absS < 0.5f) 1f else 0f,
            zIndex = 1f - absS,
        )
    }
    val clamped = absS.coerceAtMost(1f)
    return CarouselCardTransform(
        translationX = slotPosition * travelDistancePx,
        scale = 1f - 0.16f * clamped,
        rotationY = (-slotPosition * 14f).coerceIn(-18f, 18f),
        alpha = 1f - 0.65f * (absS * 0.9f).coerceIn(0f, 1f),
        zIndex = 1f - absS,
    )
}

/**
 * Applies soft rubber-band resistance when dragging past the start or end of the queue.
 */
internal fun applyCarouselEdgeResistance(
    rawFraction: Float,
    canNext: Boolean,
    canPrevious: Boolean,
): Float {
    val clamped = rawFraction.coerceIn(-1f, 1f)
    return when {
        clamped < 0f && !canNext -> (clamped * 0.22f).coerceAtLeast(-0.22f)
        clamped > 0f && !canPrevious -> (clamped * 0.22f).coerceAtMost(0.22f)
        else -> clamped
    }
}

/**
 * Decides whether a released horizontal drag commits to next (`-1`), previous (`+1`),
 * or cancels back to center (`0`), combining normalized offset and fling velocity.
 */
internal fun resolveCarouselSwipeDecision(
    offsetFraction: Float,
    velocityPxPerSec: Float,
    canNext: Boolean,
    canPrevious: Boolean,
): Int = when {
    canNext && (offsetFraction <= -0.28f || (offsetFraction < -0.05f && velocityPxPerSec <= -650f)) -> -1
    canPrevious && (offsetFraction >= 0.28f || (offsetFraction > 0.05f && velocityPxPerSec >= 650f)) -> 1
    else -> 0
}
