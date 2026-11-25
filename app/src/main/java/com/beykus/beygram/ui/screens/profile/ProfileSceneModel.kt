package com.beykus.beygram.ui.screens.profile

import kotlin.math.max
import kotlin.math.min

/**
 * Profile 2.0 C, step P1 (#1138): the single geometry model of the adaptive profile scene.
 *
 * Pure Kotlin, no Android types, so every rule is unit-tested without a device. The model decides where the
 * avatar/photo, name and status are, how far the header collapses, and how the photo layer is scaled and shifted.
 * Nothing here is wired into the UI yet (P2 and P3 do that after the visual review of each stage).
 *
 * Rules (see docs/profile-c-scene-design.md):
 * - travel (the scroll distance that collapses the header) does not depend on the reveal state;
 * - reveal changes only at scroll 0, the caller checks [ProfileSceneFrame.canToggleReveal];
 * - without a photo, or when the revealed photo would be shorter than the avatar, reveal is disabled;
 * - the photo layer covers its clip at every state (cover scale), with a small overscale for the parallax room;
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
    /** Horizontal content margin (20 dp in px). */
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
    /** One vertical gap between identity elements. */
    val gapPx: Float,
    val hasPhoto: Boolean,
    val compactAvatar: SceneRect,
    val compactTitle: SceneRect,
    val compactStatus: SceneRect,
    /** Extra scale of the photo layer so the parallax has room (1 means none). */
    val overscale: Float = 1.2f,
    val reduceMotion: Boolean,
)

internal data class ProfileSceneFrame(
    /** 0 at the top, 1 once the identity has collapsed into the toolbar. */
    val collapse: Float,
    /** Scroll distance that collapses the header. Does not depend on the reveal state. */
    val travelPx: Float,
    /** Height of the identity block in the content flow at the current reveal. */
    val stageHeightPx: Float,
    val photoWidthPx: Float,
    val photoHeightPx: Float,
    val revealEnabled: Boolean,
    /** Reveal may change only when the page is at the top. */
    val canToggleReveal: Boolean,
    /** Window rect of the avatar, which is also the clip of the photo layer. */
    val avatarRect: SceneRect,
    /** Scale of the photo layer, applied uniformly (cover, no distortion). */
    val layerScale: Float,
    /** Parallax shift in px, already limited to the slack and scaled by the reveal. */
    val parallaxPx: Float,
    /** Lowest bottom edge among the avatar, name and status. */
    val displayedBottomPx: Float,
)

internal fun profileSceneFrame(inputs: ProfileSceneInputs): ProfileSceneFrame {
    val i = inputs
    val fullPhotoWidth = max(0f, min(i.viewportWidthPx - 2f * i.marginPx, i.maxPhotoWidthPx))
    val fullPhotoHeight = max(0f, min(fullPhotoWidth, i.photoHeightFraction * i.viewportHeightPx))
    val revealEnabled = i.hasPhoto && fullPhotoWidth > 0f && fullPhotoHeight >= i.avatarPx
    val photoW = if (revealEnabled) fullPhotoWidth else 0f
    val photoH = if (revealEnabled) fullPhotoHeight else 0f
    val reveal = if (revealEnabled) i.revealFraction.coerceIn(0f, 1f) else 0f

    // The identity block height as a function of the avatar block height only. Travel uses the expanded block,
    // so it is the same for every reveal state.
    val expandedBlock = if (revealEnabled) photoH else i.avatarPx
    val travel = stageHeightFor(i, expandedBlock)
    val collapse = if (travel <= 0f) 0f else (i.scrollPx / travel).coerceIn(0f, 1f)

    val centerX = i.viewportWidthPx / 2f
    val blockNow = lerpScene(i.avatarPx, expandedBlock, reveal)
    val restAvatar = SceneRect(centerX - i.avatarPx / 2f, i.stageTopPx, centerX + i.avatarPx / 2f, i.stageTopPx + i.avatarPx)
    val photoAtRest = SceneRect(centerX - photoW / 2f, i.stageTopPx, centerX + photoW / 2f, i.stageTopPx + photoH)
    val avatarZero = lerpSceneRect(restAvatar, photoAtRest, reveal)
    val titleTop = i.stageTopPx + blockNow + i.gapPx
    val titleZero = SceneRect(i.marginPx, titleTop, i.viewportWidthPx - i.marginPx, titleTop + i.titleHeightPx)
    val statusTop = titleZero.bottom + i.gapPx
    val statusZero = SceneRect(i.marginPx, statusTop, i.viewportWidthPx - i.marginPx, statusTop + i.statusHeightPx)

    val avatarRect = lerpSceneRect(avatarZero, i.compactAvatar, collapse)
    val titleRect = lerpSceneRect(titleZero, i.compactTitle, collapse)
    val statusRect = lerpSceneRect(statusZero, i.compactStatus, collapse)

    // Cover: the layer must be at least as large as the clip in both directions, then the overscale adds parallax room.
    val cover = if (photoW <= 0f || photoH <= 0f) 1f else max(avatarRect.width / photoW, avatarRect.height / photoH)
    val layerScale = cover * i.overscale
    val slack = if (photoW <= 0f || photoH <= 0f) 0f else max(0f, (photoH * layerScale - avatarRect.height) / 2f)
    val parallax = if (i.reduceMotion) 0f else (i.scrollPx * PARALLAX_FACTOR).coerceIn(0f, slack) * reveal

    return ProfileSceneFrame(
        collapse = collapse,
        travelPx = travel,
        stageHeightPx = stageHeightFor(i, blockNow),
        photoWidthPx = photoW,
        photoHeightPx = photoH,
        revealEnabled = revealEnabled,
        canToggleReveal = i.scrollPx <= 0f,
        avatarRect = avatarRect,
        layerScale = layerScale,
        parallaxPx = parallax,
        displayedBottomPx = max(avatarRect.bottom, max(titleRect.bottom, statusRect.bottom)),
    )
}

private fun stageHeightFor(i: ProfileSceneInputs, avatarBlock: Float): Float =
    avatarBlock + 3f * i.gapPx + i.titleHeightPx + i.statusHeightPx + i.badgesHeightPx

/** Parallax shift per px of scroll: the photo moves slower than the page. */
internal const val PARALLAX_FACTOR = 0.2f
