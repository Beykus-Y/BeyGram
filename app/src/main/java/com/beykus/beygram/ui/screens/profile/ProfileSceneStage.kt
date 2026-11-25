package com.beykus.beygram.ui.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** Gaps and sizes of the identity block in dp, as in the screen: avatar to name 16, name to status 4, status to badges 0. */
private const val SceneMarginDp = 20f
private const val SceneAvatarDp = 112f
private const val SceneGapAvatarTitleDp = 16f
private const val SceneGapTitleStatusDp = 4f
private const val SceneGapStatusBadgesDp = 0f

/**
 * Heights measured by [ProfileSceneStage] and read by the screen (P2b, #1138). Written in the layout pass only,
 * so the screen recomposes when a measurement changes. Used by variant C alone.
 */
internal class ProfileSceneHeights {
    var stageHeightPx by mutableIntStateOf(0)
    /** Window y of the stage top at scroll 0 (the window top plus the current scroll). */
    var stageTopPx by mutableIntStateOf(0)
    var titleHeightPx by mutableIntStateOf(0)
    var statusHeightPx by mutableIntStateOf(0)
    var badgesHeightPx by mutableIntStateOf(0)

    /** True once the stage has been laid out at least once; until then the screen must not report or collapse. */
    val measured: Boolean get() = stageHeightPx > 0
}

/**
 * The identity stage for variant C (P2b, #1138). It measures the avatar, name, status and badges in one pass, asks
 * the scene model for the rest rectangles, and places every child at its rectangle. The stage height is the model's
 * height, so the block has no empty gap. The children must be given in this order: avatar, name, status, badges.
 */
@Composable
internal fun ProfileSceneStage(
    heights: ProfileSceneHeights,
    scrollPx: () -> Float,
    hasPhoto: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    Layout(
        content = content,
        modifier = modifier.onGloballyPositioned { coordinates ->
            heights.stageTopPx = (coordinates.boundsInWindow().top + scrollPx()).roundToInt()
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val marginPx = SceneMarginDp * density
        val avatarPx = SceneAvatarDp * density
        val textWidth = (width - 2f * marginPx).coerceAtLeast(0f).roundToInt()
        val avatar = measurables[0].measure(Constraints.fixed(avatarPx.roundToInt(), avatarPx.roundToInt()))
        val title = measurables[1].measure(Constraints(minWidth = textWidth, maxWidth = textWidth))
        val status = measurables[2].measure(Constraints(minWidth = textWidth, maxWidth = textWidth))
        val badges = measurables[3].measure(Constraints(maxWidth = textWidth))

        // Rest geometry relative to the stage top (the stage starts at 0, at scroll 0, reveal off).
        val rest = profileSceneFrame(
            ProfileSceneInputs(
                scrollPx = 0f,
                revealFraction = 0f,
                viewportWidthPx = width.toFloat(),
                viewportHeightPx = 0f,
                contentLeftPx = 0f,
                contentRightPx = width.toFloat(),
                marginPx = marginPx,
                maxPhotoWidthPx = 0f,
                photoHeightFraction = 0f,
                stageTopPx = 0f,
                toolbarBottomPx = 0f,
                avatarPx = avatarPx,
                titleHeightPx = title.height.toFloat(),
                statusHeightPx = status.height.toFloat(),
                badgesHeightPx = badges.height.toFloat(),
                gapAvatarTitlePx = SceneGapAvatarTitleDp * density,
                gapTitleStatusPx = SceneGapTitleStatusDp * density,
                gapStatusBadgesPx = SceneGapStatusBadgesDp * density,
                hasPhoto = false,
                compactAvatar = SceneRect(0f, 0f, 0f, 0f),
                compactTitle = SceneRect(0f, 0f, 0f, 0f),
                compactStatus = SceneRect(0f, 0f, 0f, 0f),
                reduceMotion = true,
            )
        )
        val stageHeight = rest.stageHeightPx.roundToInt()
        heights.titleHeightPx = title.height
        heights.statusHeightPx = status.height
        heights.badgesHeightPx = badges.height
        heights.stageHeightPx = stageHeight

        layout(width, stageHeight) {
            avatar.placeRelative(rest.avatarRect.left.roundToInt(), rest.avatarRect.top.roundToInt())
            title.placeRelative(rest.titleRect.left.roundToInt(), rest.titleRect.top.roundToInt())
            status.placeRelative(((width - status.width) / 2f).roundToInt(), rest.statusRect.top.roundToInt())
            badges.placeRelative(((width - badges.width) / 2f).roundToInt(), rest.badgesRect.top.roundToInt())
        }
    }
}

/**
 * Inputs for the screen's collapse in C, from the measured stage and the compact toolbar geometry. The stage top is
 * also taken as the toolbar bottom: the scroll content starts right below the toolbar.
 */
internal fun profileSceneScreenInputs(
    density: Float,
    windowWidthPx: Float,
    windowHeightPx: Float,
    scrollPx: Float,
    heights: ProfileSceneHeights,
    hasPhoto: Boolean,
    reduceMotion: Boolean,
    compactAvatar: SceneRect,
    compactTitle: SceneRect,
    compactStatus: SceneRect,
): ProfileSceneInputs = ProfileSceneInputs(
    scrollPx = scrollPx,
    revealFraction = 0f,
    viewportWidthPx = windowWidthPx,
    viewportHeightPx = windowHeightPx,
    contentLeftPx = 0f,
    contentRightPx = windowWidthPx,
    marginPx = SceneMarginDp * density,
    maxPhotoWidthPx = 420f * density,
    photoHeightFraction = 0.42f,
    stageTopPx = heights.stageTopPx.toFloat(),
    toolbarBottomPx = heights.stageTopPx.toFloat(),
    avatarPx = SceneAvatarDp * density,
    titleHeightPx = heights.titleHeightPx.toFloat(),
    statusHeightPx = heights.statusHeightPx.toFloat(),
    badgesHeightPx = heights.badgesHeightPx.toFloat(),
    gapAvatarTitlePx = SceneGapAvatarTitleDp * density,
    gapTitleStatusPx = SceneGapTitleStatusDp * density,
    gapStatusBadgesPx = SceneGapStatusBadgesDp * density,
    hasPhoto = hasPhoto,
    compactAvatar = compactAvatar,
    compactTitle = compactTitle,
    compactStatus = compactStatus,
    reduceMotion = reduceMotion,
)

/** Converts a window rectangle from the compact toolbar geometry into the model's rectangle. */
internal fun Rect.toSceneRect(): SceneRect = SceneRect(left, top, right, bottom)
