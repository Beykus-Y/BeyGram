package com.beykus.beygram.ui.screens.profile

import kotlin.math.max
import kotlin.math.min

/**
 * Profile 2.0 C (#1138, #1235): the single geometry model of the adaptive immersive profile scene.
 *
 * Pure Kotlin, no Android types, so every rule is unit-tested without a device.
 * - At reveal = 0, the avatar is a 112 dp circle below the toolbar with centered name and status underneath.
 * - As reveal -> 1, the avatar morphs continuously into an edge-to-edge full-bleed cover starting at windowTopPx
 *   (behind the overlaid transparent toolbar), while title and status interpolate smoothly into the bottom
 *   overlay zone inside the cover.
 * - stageHeight interpolates so the bottom of the stage always aligns with photoAtRest.bottom at reveal = 1
 *   (zero empty gap below the cover).
 */
internal data class SceneRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** Linear interpolation of a rectangle, used for the avatar, name and status between their states. */
internal fun lerpSceneRect(from: SceneRect, to: SceneRect, fraction: Float): SceneRect = SceneRect(
    left = lerpScene(from.left, to.left, fraction),
    top = lerpScene(from.top, to.top, fraction),
    right = lerpScene(from.right, to.right, fraction),
    bottom = lerpScene(from.bottom, to.bottom, fraction),
)

private fun lerpScene(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

internal data class ProfileSceneInputs(
    val scrollPx: Float,
    val revealFraction: Float,
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    /** Window x of the content area edges after system insets (cutouts, folds). */
    val contentLeftPx: Float,
    val contentRightPx: Float,
    /** Horizontal content margin for text inside the content area (20 dp in px). */
    val marginPx: Float,
    /** Upper bound of the revealed photo width; defaults to full viewport width in immersive mode. */
    val maxPhotoWidthPx: Float,
    /** The revealed photo is at most this share of the viewport height. */
    val photoHeightFraction: Float,
    /** Window y of the identity block at scroll 0. */
    val stageTopPx: Float,
    val toolbarBottomPx: Float,
    /** Window y of the top of the root profile container (where the full-bleed cover starts). */
    val windowTopPx: Float = toolbarBottomPx,
    /** Bottom padding inside the full-bleed cover below the overlaid name/status. */
    val immersiveOverlayBottomPaddingPx: Float = 0f,
    /** Collapsed avatar side (112 dp in px). */
    val avatarPx: Float,
    val titleHeightPx: Float,
    val statusHeightPx: Float,
    val badgesHeightPx: Float,
    val gapAvatarTitlePx: Float,
    val gapTitleStatusPx: Float,
    val gapStatusBadgesPx: Float,
    val hasPhoto: Boolean,
    val compactAvatar: SceneRect,
    val compactTitle: SceneRect,
    val compactStatus: SceneRect,
    /** Extra scale of the photo layer at full reveal, so the parallax has room (1 means none). */
    val overscale: Float = 1.2f,
    val reduceMotion: Boolean,
    val pullDownPx: Float = 0f,
)

internal data class ProfileSceneFrame(
    /** 0 at the top, 1 once the identity block has reached the toolbar. */
    val collapse: Float,
    /** Scroll distance that collapses the header: the stage bottom reaches the toolbar bottom. */
    val travelPx: Float,
    /** Height of the identity block in the content flow at the current reveal. */
    val stageHeightPx: Float,
    /** Window y of the identity block bottom at the current scroll. */
    val stageBottomPx: Float,
    /** Rest rects (reveal and scroll 0 state) of the name, the status and the badges row. */
    val titleRect: SceneRect,
    val statusRect: SceneRect,
    val badgesRect: SceneRect,
    val photoWidthPx: Float,
    val photoHeightPx: Float,
    val revealEnabled: Boolean,
    /** Reveal may change only when the page is at the top. */
    val canToggleReveal: Boolean,
    /** Window rect of the avatar, which is also the clip of the photo layer. */
    val avatarRect: SceneRect,
    /** Scale of the photo layer, applied uniformly (cover, no distortion). */
    val layerScale: Float,
    /** Side of the square photo layer: the larger of the photo sides and the avatar, so the crop is symmetric. */
    val layerSidePx: Float,
    /** Upper bound of the visible photo clip. */
    val clipTopPx: Float,
    /** Parallax shift in px, already limited to the slack and scaled by the reveal. */
    val parallaxPx: Float,
    /** Lowest bottom edge among the avatar, name and status. */
    val displayedBottomPx: Float,
)

internal fun profileSceneFrame(inputs: ProfileSceneInputs): ProfileSceneFrame {
    val i = inputs
    val scroll = if (i.scrollPx.isNaN()) 0f else i.scrollPx
    val contentWidth = max(0f, i.contentRightPx - i.contentLeftPx)
    val fullPhotoWidth = max(0f, min(contentWidth, i.maxPhotoWidthPx))
    val fullPhotoHeight = max(0f, min(fullPhotoWidth, i.photoHeightFraction * i.viewportHeightPx))
    val revealEnabled = i.hasPhoto && fullPhotoWidth > 0f && fullPhotoHeight >= i.avatarPx
    val photoW = if (revealEnabled) fullPhotoWidth else 0f
    val reveal = if (revealEnabled) i.revealFraction.coerceIn(0f, 1f) else 0f
    val stretch = if (reveal == 1f && scroll <= 0f && !i.reduceMotion) {
        i.pullDownPx.coerceIn(0f, i.avatarPx * 0.5f)
    } else 0f
    val photoH = if (revealEnabled) fullPhotoHeight + stretch else 0f

    val centerX = (i.contentLeftPx + i.contentRightPx) / 2f
    val restAvatar = SceneRect(centerX - i.avatarPx / 2f, i.stageTopPx, centerX + i.avatarPx / 2f, i.stageTopPx + i.avatarPx)
    val photoAtRest = SceneRect(centerX - photoW / 2f, i.windowTopPx, centerX + photoW / 2f, i.windowTopPx + photoH)
    val avatarZero = lerpSceneRect(restAvatar, photoAtRest, reveal)

    // Rest text rects (below circular avatar)
    val textLeft = i.contentLeftPx + i.marginPx
    val textRight = max(textLeft, i.contentRightPx - i.marginPx)
    val restTitleTop = i.stageTopPx + i.avatarPx + gapBefore(i.titleHeightPx, i.gapAvatarTitlePx)
    val restTitleRect = SceneRect(textLeft, restTitleTop, textRight, restTitleTop + i.titleHeightPx)
    val restStatusTop = restTitleRect.bottom + gapBefore(i.statusHeightPx, i.gapTitleStatusPx)
    val restStatusRect = SceneRect(textLeft, restStatusTop, textRight, restStatusTop + i.statusHeightPx)

    // Immersive text rects (integrated into bottom of full-bleed cover)
    val immersiveTextHeight = i.titleHeightPx + gapBefore(i.statusHeightPx, i.gapTitleStatusPx) + i.statusHeightPx
    val immersiveTitleTop = max(i.windowTopPx, photoAtRest.bottom - i.immersiveOverlayBottomPaddingPx - immersiveTextHeight)
    val immersiveTitleRect = SceneRect(textLeft, immersiveTitleTop, textRight, immersiveTitleTop + i.titleHeightPx)
    val immersiveStatusTop = immersiveTitleRect.bottom + gapBefore(i.statusHeightPx, i.gapTitleStatusPx)
    val immersiveStatusRect = SceneRect(textLeft, immersiveStatusTop, textRight, immersiveStatusTop + i.statusHeightPx)

    val titleZero = lerpSceneRect(restTitleRect, immersiveTitleRect, reveal)
    val statusZero = lerpSceneRect(restStatusRect, immersiveStatusRect, reveal)

    val restStageHeight = stageHeightFor(i, i.avatarPx)
    val immersiveStageHeight = if (revealEnabled) {
        max(0f, photoAtRest.bottom - i.stageTopPx)
    } else restStageHeight
    val stageHeight = lerpScene(restStageHeight, immersiveStageHeight, reveal)

    val badgesTop = if (reveal > 0f && revealEnabled) {
        stageHeight + i.stageTopPx - i.badgesHeightPx
    } else {
        restStatusRect.bottom + gapBefore(i.badgesHeightPx, i.gapStatusBadgesPx)
    }

    // Scroll until the stage bottom reaches the toolbar bottom.
    val travel = max(0f, stageHeight + i.stageTopPx - i.toolbarBottomPx)
    val collapse = if (travel <= 0f) 0f else (scroll / travel).coerceIn(0f, 1f)

    val avatarRect = lerpSceneRect(avatarZero, i.compactAvatar, collapse)
    val titleRect = lerpSceneRect(titleZero, i.compactTitle, collapse)
    val statusRect = lerpSceneRect(statusZero, i.compactStatus, collapse)

    // The layer is a square of the larger side (P1.2): the photo is never distorted and its crop is symmetric.
    val layerSide = if (revealEnabled) max(i.avatarPx, max(photoW, photoH)) else i.avatarPx
    val layerW = layerSide
    val layerH = layerSide
    val imageMotion = if (i.reduceMotion) 0f else reveal * (1f - collapse)
    val overscaleNow = 1f + (i.overscale - 1f) * imageMotion
    val cover = if (layerW <= 0f || layerH <= 0f) 1f
    else max(avatarRect.width / layerW, avatarRect.height / layerH)
    val layerScale = cover * overscaleNow
    val slack = if (layerW <= 0f || layerH <= 0f) 0f
    else max(0f, (layerH * layerScale - avatarRect.height) / 2f)
    val parallax = (scroll * PARALLAX_FACTOR).coerceIn(0f, slack) * imageMotion

    return ProfileSceneFrame(
        collapse = collapse,
        travelPx = travel,
        stageHeightPx = stageHeight,
        stageBottomPx = i.stageTopPx + stageHeight - scroll,
        titleRect = titleZero,
        statusRect = statusZero,
        badgesRect = SceneRect(textLeft, badgesTop, textRight, badgesTop + i.badgesHeightPx),
        photoWidthPx = photoW,
        photoHeightPx = photoH,
        revealEnabled = revealEnabled,
        canToggleReveal = scroll <= 0f,
        avatarRect = avatarRect,
        layerScale = layerScale,
        layerSidePx = layerSide,
        clipTopPx = clipTopFor(collapse, i.toolbarBottomPx, reveal, i.windowTopPx),
        parallaxPx = parallax,
        displayedBottomPx = max(avatarRect.bottom, max(titleRect.bottom, statusRect.bottom)),
    )
}

/** The gap above an element, or none when the element has no height. */
private fun gapBefore(elementHeightPx: Float, gapPx: Float): Float = if (elementHeightPx > 0f) gapPx else 0f

/** Collapse at which the photo clip starts to release toward the window top. */
private const val CLIP_RELEASE_START = 0.8f

/**
 * Upper bound of the photo clip. At rest (reveal = 0) it is the toolbar bottom while expanded, releasing to 0
 * between collapse [CLIP_RELEASE_START] and 1. As reveal -> 1, it interpolates smoothly to [windowTopPx]
 * so the full-bleed cover extends behind the overlaid transparent toolbar.
 */
internal fun clipTopFor(
    collapse: Float,
    toolbarBottomPx: Float,
    reveal: Float = 0f,
    windowTopPx: Float = 0f
): Float {
    val release = ((collapse - CLIP_RELEASE_START) / (1f - CLIP_RELEASE_START)).coerceIn(0f, 1f)
    val expandedClip = lerpScene(max(0f, toolbarBottomPx), max(0f, windowTopPx), reveal.coerceIn(0f, 1f))
    return expandedClip * (1f - release)
}

/** Avatar block, then each element with its own gap (none for a zero-height element). */
private fun stageHeightFor(i: ProfileSceneInputs, avatarBlock: Float): Float =
    avatarBlock +
        gapBefore(i.titleHeightPx, i.gapAvatarTitlePx) + i.titleHeightPx +
        gapBefore(i.statusHeightPx, i.gapTitleStatusPx) + i.statusHeightPx +
        gapBefore(i.badgesHeightPx, i.gapStatusBadgesPx) + i.badgesHeightPx

internal fun profileSceneMinContentHeightPx(viewportHeightPx: Float, travelPx: Float): Float =
    max(0f, viewportHeightPx) + max(0f, travelPx)

/** Parallax shift per px of scroll: the photo moves slower than the page. */
internal const val PARALLAX_FACTOR = 0.2f
