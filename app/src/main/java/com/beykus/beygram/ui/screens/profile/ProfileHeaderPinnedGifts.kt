package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ReceivedGiftItem
import com.beykus.beygram.domain.model.selectPinnedHeaderGifts
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PinnedGiftSlot(
    val xPx: Float,
    val yPx: Float,
    val immersiveXOffsetPx: Float,
    val immersiveYOffsetPx: Float
)

/**
 * Pure deterministic layout for pinned collectible gifts around the Profile C avatar (#1135).
 * Strictly enforces exclusion zones around the avatar, overlaid toolbar, and name/title block.
 */
internal fun computePinnedGiftSlots(
    containerWidthPx: Float,
    containerHeightPx: Float,
    avatarCenterXPx: Float,
    avatarCenterYPx: Float,
    avatarRadiusPx: Float,
    toolbarBottomPx: Float,
    titleTopPx: Float,
    itemSizePx: Float,
    densityScale: Float,
    preset: ProfileSceneDensity,
    count: Int
): List<PinnedGiftSlot> {
    if (count <= 0 || itemSizePx <= 0f || densityScale <= 0f) return emptyList()
    if (containerWidthPx < 280f * densityScale || containerHeightPx < 180f * densityScale) return emptyList()

    val maxSlots = when {
        containerWidthPx < 340f * densityScale -> 2
        preset == ProfileSceneDensity.Calm -> 2
        preset == ProfileSceneDensity.Balanced -> 4
        else -> 6
    }
    val actualCount = min(count, maxSlots)
    val edgeMargin = 12f * densityScale
    val exclusionRadius = avatarRadiusPx + 14f * densityScale
    val minY = max(edgeMargin, toolbarBottomPx + 8f * densityScale)
    val maxY = max(minY, titleTopPx - itemSizePx - 8f * densityScale)
    if (maxY <= minY + 4f * densityScale && titleTopPx > 0f) {
        // Still allow symmetric horizontal placement flanking the avatar above titleTopPx
    }

    // Normalized symmetric offsets on left/right of avatar: (sideSign: -1 or +1, verticalStep: 0..2, horizExtra: 0..1)
    val templateOffsets = listOf(
        Triple(-1f, -0.45f, 0.25f),
        Triple(1f, -0.45f, 0.25f),
        Triple(-1f, 0.25f, 0.60f),
        Triple(1f, 0.25f, 0.60f),
        Triple(-1f, -0.85f, 0.72f),
        Triple(1f, -0.85f, 0.72f)
    )

    val availableSide = max(0f, (containerWidthPx / 2f) - exclusionRadius - edgeMargin - itemSizePx)
    if (availableSide < 4f * densityScale) return emptyList()

    return (0 until actualCount).map { idx ->
        val (side, yFactor, xFactor) = templateOffsets[idx]
        val radialX = exclusionRadius + availableSide * xFactor
        val rawCenterX = avatarCenterXPx + side * (radialX + itemSizePx * 0.5f)
        val x = (rawCenterX - itemSizePx * 0.5f).coerceIn(edgeMargin, containerWidthPx - edgeMargin - itemSizePx)
        val rawY = (avatarCenterYPx - itemSizePx * 0.5f) + yFactor * avatarRadiusPx
        val y = rawY.coerceIn(minY, max(minY, maxY))
        // During immersive reveal, shift gently outward & toward the upper cover corners
        val immersiveDx = side * 10f * densityScale
        val immersiveDy = (-12f - (idx / 2) * 6f) * densityScale
        PinnedGiftSlot(x, y, immersiveDx, immersiveDy)
    }
}

/**
 * Compact header/cover showcase of real pinned collectible gifts for Profile C (#1135).
 * Reuses GiftArtworkFrame and shared GiftAnimationBudget(maxActive = 2); clicking opens GiftDetailSheet.
 */
@Composable
internal fun ProfileHeaderPinnedGifts(
    gifts: List<ReceivedGiftItem>,
    emojiStatusUpgradedGiftId: Long?,
    frame: () -> ProfileSceneFrame?,
    reveal: () -> Float,
    preset: ProfileSceneDensity,
    reduceMotion: Boolean,
    stageTopLocalPx: Float,
    toolbarBottomLocalPx: Float,
    modifier: Modifier = Modifier
) {
    val pinned = remember(gifts, emojiStatusUpgradedGiftId) {
        selectPinnedHeaderGifts(gifts, emojiStatusUpgradedGiftId, maxCount = 6)
    }
    if (pinned.isEmpty()) return

    var detailGift by remember { mutableStateOf<ReceivedGiftItem?>(null) }
    val density = LocalDensity.current.density
    val itemSizeDp = 40.dp
    val itemSizePx = with(LocalDensity.current) { itemSizeDp.toPx() }
    val currentFrame = frame() ?: return

    val width = max(currentFrame.photoWidthPx, 320f * density)
    val avatarRadius = 56f * density
    val avatarCenterX = width / 2f
    val avatarCenterY = stageTopLocalPx + avatarRadius
    val restTitleTop = stageTopLocalPx + avatarRadius * 2f + 16f * density
    val slots = remember(
        width,
        currentFrame.stageHeightPx,
        avatarCenterX,
        avatarCenterY,
        toolbarBottomLocalPx,
        restTitleTop,
        preset,
        pinned.size
    ) {
        computePinnedGiftSlots(
            containerWidthPx = width,
            containerHeightPx = max(currentFrame.stageHeightPx, 200f * density),
            avatarCenterXPx = avatarCenterX,
            avatarCenterYPx = avatarCenterY,
            avatarRadiusPx = avatarRadius,
            toolbarBottomPx = toolbarBottomLocalPx,
            titleTopPx = restTitleTop,
            itemSizePx = itemSizePx,
            densityScale = density,
            preset = preset,
            count = pinned.size
        )
    }
    if (slots.isEmpty()) return

    ProvideGiftAnimationBudget(maxActive = 2) {
        Box(modifier.fillMaxSize()) {
            slots.forEachIndexed { index, slot ->
                val gift = pinned.getOrNull(index) ?: return@forEachIndexed
                val label = gift.collectible?.let {
                    val baseName = gift.name.ifBlank { it.title }.ifBlank { stringResource(R.string.gifts_gift_generic) }
                    "$baseName #${it.number}"
                } ?: gift.name.ifBlank { stringResource(R.string.gifts_gift_generic) }
                Box(
                    Modifier
                        .offset { IntOffset(slot.xPx.roundToInt(), slot.yPx.roundToInt()) }
                        .size(itemSizeDp)
                        .graphicsLayer {
                            val sc = frame()
                            val c = sc?.collapse ?: 0f
                            val r = reveal().coerceIn(0f, 1f)
                            val fade = (1f - c * 2.4f).coerceIn(0f, 1f)
                            alpha = fade * (0.95f - r * 0.08f)
                            val converge = c * 0.35f
                            translationX = (avatarCenterX - itemSizePx / 2f - slot.xPx) * converge +
                                slot.immersiveXOffsetPx * r * (1f - c)
                            translationY = (avatarCenterY - itemSizePx / 2f - slot.yPx) * converge +
                                slot.immersiveYOffsetPx * r * (1f - c)
                            val scale = (1f - c * 0.35f).coerceAtLeast(0.5f)
                            scaleX = scale
                            scaleY = scale
                        }
                        .clickable(role = Role.Button) { detailGift = gift }
                        .semantics { contentDescription = label }
                ) {
                    GiftArtworkFrame(
                        visual = gift.visual,
                        emoji = gift.emoji,
                        collectible = gift.collectible,
                        frameSize = itemSizeDp,
                        stickerSize = 28.dp,
                        symbolSize = 12.dp,
                        cornerRadius = 14.dp,
                        staticOnly = reduceMotion
                    )
                }
            }
        }
    }

    detailGift?.let { gift ->
        GiftDetailSheet(target = GiftDetailTarget.Received(gift), onDismiss = { detailGift = null })
    }
}
