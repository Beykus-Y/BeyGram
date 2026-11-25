package com.beykus.beygram.ui.screens.profile

/**
 * Profile 2.0 C, step P2 (#1138): builds the scene model's inputs from what the screen measures.
 *
 * Only the experiment (C) uses this. Variant A never calls it. Reveal is not offered in P2, so the revealed
 * photo is always off and the avatar stays a circle; the photo morph comes in P3.
 *
 * Dimensions are given in dp and converted with [density], so the model works in px like the rest of the screen.
 */
internal fun profileSceneInputsFor(
    density: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    contentLeftPx: Float,
    contentRightPx: Float,
    scrollPx: Float,
    stageTopPx: Float,
    toolbarBottomPx: Float,
    titleHeightPx: Float,
    statusHeightPx: Float,
    badgesHeightPx: Float,
    hasPhoto: Boolean,
    reduceMotion: Boolean,
    compactAvatar: SceneRect,
    compactTitle: SceneRect,
    compactStatus: SceneRect,
): ProfileSceneInputs = ProfileSceneInputs(
    scrollPx = scrollPx,
    revealFraction = 0f,
    viewportWidthPx = viewportWidthPx,
    viewportHeightPx = viewportHeightPx,
    contentLeftPx = contentLeftPx,
    contentRightPx = contentRightPx,
    marginPx = 20f * density,
    maxPhotoWidthPx = 420f * density,
    photoHeightFraction = 0.42f,
    stageTopPx = stageTopPx,
    toolbarBottomPx = toolbarBottomPx,
    avatarPx = 112f * density,
    titleHeightPx = titleHeightPx,
    statusHeightPx = statusHeightPx,
    badgesHeightPx = badgesHeightPx,
    gapPx = 8f * density,
    hasPhoto = hasPhoto,
    compactAvatar = compactAvatar,
    compactTitle = compactTitle,
    compactStatus = compactStatus,
    reduceMotion = reduceMotion,
)
