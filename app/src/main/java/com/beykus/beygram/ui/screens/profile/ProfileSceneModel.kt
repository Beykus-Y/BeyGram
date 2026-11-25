package com.beykus.beygram.ui.screens.profile

import kotlin.math.max
import kotlin.math.min

/**
 * Profile 2.0 C, step P1 (#1138): the single geometry model of the adaptive profile scene.
 *
 * Pure Kotlin, no Android types, so every rule is unit-tested without a device. The model decides where the
 * avatar/photo, name, status and badges are, how far the header collapses, and how the photo layer is scaled and
 * shifted. Nothing here is wired into the UI yet (P2 and P3 do that after the visual review of each stage).
 *
 * Rules (see docs/profile-c-scene-design.md):
 * - travel is the scroll distance that brings the identity block's bottom to the toolbar bottom, so it follows the
 *   current stage height. Reveal only changes at scroll 0, where collapse is 0 whatever the travel is;
 * - reveal changes only at scroll 0, the caller checks [ProfileSceneFrame.canToggleReveal];
 * - without a photo, or when the revealed photo would be shorter than the avatar, reveal is disabled and the layer
 *   is the avatar itself;
 * - the photo layer covers its clip at every state (cover scale), with overscale 1 + (overscale - 1) * reveal, so the
 *   collapsed and resting avatars show no extra zoom;
 * - parallax is zero at rest, zero with reduced motion, and scaled by the reveal.
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

/**
 * Everything the model needs. Lengths are in window pixels; the caller converts dp. Text heights are measured
 * from an invisible stage (P2), so long names and large font scales change the result.
 */
internal data class ProfileSceneInputs(
    val scrollPx: Float,
    val revealFraction: Float,
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    /** Window x of the content area edges after system insets (cutouts, folds). */
    val contentLeftPx: Float,
    val contentRightPx: Float,
    /** Horizontal content margin inside the content area (20 dp in px). */
    val marginPx: Float,
    /** Upper bound of the revealed photo width (420 dp in px). */
    val maxPhotoWidthPx: Float,
    /** The revealed photo is at most this share of the viewport height. */
    val photoHeightFraction: Float,
    /** Window y of the identity block at scroll 0. */
    val stageTopPx: Float,
    val toolbarBottomPx: Float,
    /** Collapsed avatar side (112 dp in px). */
    val avatarPx: Float,
    val titleHeightPx: Float,
    val statusHeightPx: Float,
    val badgesHeightPx: Float,
    /**
     * Separate gaps, as in the screen: avatar to name (16 dp), name to status (4 dp), status to badges (0 dp).
     * A gap is applied only when the element below it has height, so a missing status or badges row leaves no
     * empty space (zero height means no element and no gap).
     */
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
    /** Rest rects (reveal and scroll 0 state) of the name, the status and the badges row. A zero-height element has an empty rect. */
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
    /** Side of the square photo layer: the larger of the photo sides and the avatar, so the crop is symmetric and never distorted. */
    val layerSidePx: Float,
    /** Upper bound of the visible photo clip: the toolbar bottom, released to 0 between collapse 0.8 and 1 (see [clipTopFor]). */
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
    val fullPhotoWidth = max(0f, min(contentWidth - 2f * i.marginPx, i.maxPhotoWidthPx))
    val fullPhotoHeight = max(0f, min(fullPhotoWidth, i.photoHeightFraction * i.viewportHeightPx))
    val revealEnabled = i.hasPhoto && fullPhotoWidth > 0f && fullPhotoHeight >= i.avatarPx
    val photoW = if (revealEnabled) fullPhotoWidth else 0f
    val reveal = if (revealEnabled) i.revealFraction.coerceIn(0f, 1f) else 0f
    val stretch = if (reveal == 1f && scroll <= 0f && !i.reduceMotion) {
        i.pullDownPx.coerceIn(0f, i.avatarPx * 0.5f)
    } else 0f
    val photoH = if (revealEnabled) fullPhotoHeight + stretch else 0f

    // The identity block height at the current reveal: avatar block grows toward the photo height.
    val blockNow = lerpScene(i.avatarPx, if (revealEnabled) photoH else i.avatarPx, reveal)
    val stageHeight = stageHeightFor(i, blockNow)
    // Scroll until the stage bottom reaches the toolbar bottom (equal to the stage height when the stage starts
    // at the toolbar bottom).
    val travel = max(0f, stageHeight + i.stageTopPx - i.toolbarBottomPx)
    val collapse = if (travel <= 0f) 0f else (scroll / travel).coerceIn(0f, 1f)

    val centerX = (i.contentLeftPx + i.contentRightPx) / 2f
    val restAvatar = SceneRect(centerX - i.avatarPx / 2f, i.stageTopPx, centerX + i.avatarPx / 2f, i.stageTopPx + i.avatarPx)
    val photoAtRest = SceneRect(centerX - photoW / 2f, i.stageTopPx, centerX + photoW / 2f, i.stageTopPx + photoH)
    val avatarZero = lerpSceneRect(restAvatar, photoAtRest, reveal)
    val titleTop = i.stageTopPx + blockNow + gapBefore(i.titleHeightPx, i.gapAvatarTitlePx)
    val titleZero = SceneRect(i.contentLeftPx + i.marginPx, titleTop, i.contentRightPx - i.marginPx, titleTop + i.titleHeightPx)
    val statusTop = titleZero.bottom + gapBefore(i.statusHeightPx, i.gapTitleStatusPx)
    val statusZero = SceneRect(titleZero.left, statusTop, titleZero.right, statusTop + i.statusHeightPx)
    val badgesTop = statusZero.bottom + gapBefore(i.badgesHeightPx, i.gapStatusBadgesPx)

    val avatarRect = lerpSceneRect(avatarZero, i.compactAvatar, collapse)
    val titleRect = lerpSceneRect(titleZero, i.compactTitle, collapse)
    val statusRect = lerpSceneRect(statusZero, i.compactStatus, collapse)

    // The layer is a square of the larger side (P1.2): the photo is never distorted and its crop is symmetric.
    // Without a photo it is the avatar itself, so cover is 1 at rest.
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
        badgesRect = SceneRect(titleZero.left, badgesTop, titleZero.right, badgesTop + i.badgesHeightPx),
        photoWidthPx = photoW,
        photoHeightPx = photoH,
        revealEnabled = revealEnabled,
        canToggleReveal = scroll <= 0f,
        avatarRect = avatarRect,
        layerScale = layerScale,
        layerSidePx = layerSide,
        clipTopPx = clipTopFor(collapse, i.toolbarBottomPx),
        parallaxPx = parallax,
        displayedBottomPx = max(avatarRect.bottom, max(titleRect.bottom, statusRect.bottom)),
    )
}

/** The gap above an element, or none when the element has no height. */
private fun gapBefore(elementHeightPx: Float, gapPx: Float): Float = if (elementHeightPx > 0f) gapPx else 0f

/** Collapse at which the photo clip starts to release toward the window top. */
private const val CLIP_RELEASE_START = 0.8f

/**
 * Upper bound of the photo clip. It is the toolbar bottom while the photo is wide (so it does not cover the toolbar's
 * back button), and it is released continuously to 0 between collapse [CLIP_RELEASE_START] and 1. The continuous
 * release avoids a jump of the clip edge, and at collapse 1 the compact avatar inside the toolbar is never cut.
 */
internal fun clipTopFor(collapse: Float, toolbarBottomPx: Float): Float {
    val release = ((collapse - CLIP_RELEASE_START) / (1f - CLIP_RELEASE_START)).coerceIn(0f, 1f)
    return max(0f, toolbarBottomPx) * (1f - release)
}

/** Avatar block, then each element with its own gap (none for a zero-height element). */
private fun stageHeightFor(i: ProfileSceneInputs, avatarBlock: Float): Float =
    avatarBlock +
        gapBefore(i.titleHeightPx, i.gapAvatarTitlePx) + i.titleHeightPx +
        gapBefore(i.statusHeightPx, i.gapTitleStatusPx) + i.statusHeightPx +
        gapBefore(i.badgesHeightPx, i.gapStatusBadgesPx) + i.badgesHeightPx

/**
 * Minimum height of the scroll content. The content must exceed the viewport by at least the stage travel, or a short
 * profile can never scroll far enough to reach full collapse (the invariant that broke before P2b-2). Scrollable
 * range is `result - viewportHeightPx`, which is therefore always at least [travelPx].
 */
internal fun profileSceneMinContentHeightPx(viewportHeightPx: Float, travelPx: Float): Float =
    max(0f, viewportHeightPx) + max(0f, travelPx)

/** Parallax shift per px of scroll: the photo moves slower than the page. */
internal const val PARALLAX_FACTOR = 0.2f
