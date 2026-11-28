package com.beykus.beygram.ui.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** Gaps and sizes of the identity block in dp. */
private const val SceneMarginDp = 20f
private const val SceneAvatarDp = 112f
private const val SceneGapAvatarTitleDp = 16f
private const val SceneGapTitleStatusDp = 4f
private const val SceneGapStatusBadgesDp = 0f
private const val SceneImmersiveBottomPadDp = 18f
/** Full-bleed cover spans up to viewport width and up to 54% of viewport height. */
private const val ScenePhotoHeightFraction = 0.54f

/**
 * Heights measured by [ProfileSceneStage] and read by the screen (#1138). Written in the layout pass only,
 * so the screen recomposes when a measurement changes. Used by variant C alone.
 */
internal class ProfileSceneHeights {
    var stageHeightPx by mutableIntStateOf(0)
    /** Window y of the stage top at scroll 0 (the window top plus the current scroll). */
    var stageTopPx by mutableIntStateOf(0)
    var stageLeftPx by mutableIntStateOf(0)
    var stageWidthPx by mutableIntStateOf(0)
    var titleHeightPx by mutableIntStateOf(0)
    var statusHeightPx by mutableIntStateOf(0)
    var badgesHeightPx by mutableIntStateOf(0)
    /** Window y of the top of the root profile container (where the full-bleed cover starts). */
    var windowTopPx by mutableIntStateOf(0)
    /** Window y of the bottom of the overlaid toolbar. */
    var toolbarBottomPx by mutableIntStateOf(0)
    /** Height of the fixed viewport; the scroll content is at least this tall plus the stage travel. */
    var viewportHeightPx by mutableIntStateOf(0)

    /** True once the stage has been laid out at least once; until then the screen must not report or collapse. */
    val measured: Boolean get() = stageHeightPx > 0
}

/**
 * The identity stage for variant C (#1138, #1235). Measures avatar, name, status and badges in one pass,
 * asks the scene model for the rectangles at the current reveal, and places every child at its rectangle.
 */
@Composable
internal fun ProfileSceneStage(
    heights: ProfileSceneHeights,
    scrollPx: () -> Float,
    hasPhoto: Boolean,
    reveal: () -> Float,
    reduceMotion: Boolean,
    pullDownPx: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    Layout(
        content = content,
        modifier = modifier.onGloballyPositioned { coordinates ->
            val origin = coordinates.positionInWindow()
            heights.stageTopPx = (origin.y + scrollPx()).roundToInt()
            heights.stageLeftPx = origin.x.roundToInt()
            heights.stageWidthPx = coordinates.size.width
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val avatarPx = SceneAvatarDp * density
        val marginPx = SceneMarginDp * density
        val revealNow = if (hasPhoto) reveal().coerceIn(0f, 1f) else 0f
        val textMaxWidth = (width - (2f * marginPx).roundToInt()).coerceAtLeast(1)
        val title = measurables[1].measure(Constraints(maxWidth = textMaxWidth))
        val status = measurables[2].measure(Constraints(maxWidth = textMaxWidth))
        val badges = measurables[3].measure(Constraints(maxWidth = textMaxWidth))

        val relativeWindowTop = (heights.windowTopPx - heights.stageTopPx).toFloat().coerceAtMost(0f)
        val frame = profileSceneFrame(
            ProfileSceneInputs(
                scrollPx = 0f,
                revealFraction = revealNow,
                viewportWidthPx = width.toFloat(),
                viewportHeightPx = heights.viewportHeightPx.toFloat(),
                contentLeftPx = 0f,
                contentRightPx = width.toFloat(),
                marginPx = marginPx,
                maxPhotoWidthPx = width.toFloat(),
                photoHeightFraction = ScenePhotoHeightFraction,
                stageTopPx = 0f,
                toolbarBottomPx = 0f,
                windowTopPx = relativeWindowTop,
                immersiveOverlayBottomPaddingPx = SceneImmersiveBottomPadDp * density,
                avatarPx = avatarPx,
                titleHeightPx = title.height.toFloat(),
                statusHeightPx = status.height.toFloat(),
                badgesHeightPx = badges.height.toFloat(),
                gapAvatarTitlePx = SceneGapAvatarTitleDp * density,
                gapTitleStatusPx = SceneGapTitleStatusDp * density,
                gapStatusBadgesPx = SceneGapStatusBadgesDp * density,
                hasPhoto = hasPhoto,
                compactAvatar = SceneRect(0f, 0f, 0f, 0f),
                compactTitle = SceneRect(0f, 0f, 0f, 0f),
                compactStatus = SceneRect(0f, 0f, 0f, 0f),
                reduceMotion = reduceMotion,
                pullDownPx = pullDownPx(),
            )
        )
        val avatarRect = frame.avatarRect
        val avatar = measurables[0].measure(
            Constraints.fixed(avatarRect.width.roundToInt().coerceAtLeast(0), avatarRect.height.roundToInt().coerceAtLeast(0))
        )
        val stageHeight = frame.stageHeightPx.roundToInt()
        heights.titleHeightPx = title.height
        heights.statusHeightPx = status.height
        heights.badgesHeightPx = badges.height
        heights.stageHeightPx = stageHeight

        layout(width, stageHeight) {
            avatar.placeRelative(avatarRect.left.roundToInt(), avatarRect.top.roundToInt())
            title.placeRelative(((width - title.width) / 2f).roundToInt(), frame.titleRect.top.roundToInt())
            status.placeRelative(((width - status.width) / 2f).roundToInt(), frame.statusRect.top.roundToInt())
            badges.placeRelative(((width - badges.width) / 2f).roundToInt(), frame.badgesRect.top.roundToInt())
        }
    }
}

/**
 * Inputs for the screen's collapse in C, from the measured stage and the compact toolbar geometry.
 */
internal fun profileSceneScreenInputs(
    density: Float,
    windowWidthPx: Float,
    windowHeightPx: Float,
    scrollPx: Float,
    stageTopPx: Float,
    toolbarBottomPx: Float,
    windowTopPx: Float = toolbarBottomPx,
    titleHeightPx: Float,
    statusHeightPx: Float,
    badgesHeightPx: Float,
    hasPhoto: Boolean,
    reduceMotion: Boolean,
    compactAvatar: SceneRect,
    compactTitle: SceneRect,
    compactStatus: SceneRect,
    revealFraction: Float = 0f,
): ProfileSceneInputs {
    val marginPx = SceneMarginDp * density
    return ProfileSceneInputs(
        scrollPx = scrollPx,
        revealFraction = revealFraction,
        viewportWidthPx = windowWidthPx,
        viewportHeightPx = windowHeightPx,
        contentLeftPx = 0f,
        contentRightPx = windowWidthPx,
        marginPx = marginPx,
        maxPhotoWidthPx = windowWidthPx,
        photoHeightFraction = ScenePhotoHeightFraction,
        stageTopPx = stageTopPx,
        toolbarBottomPx = toolbarBottomPx,
        windowTopPx = windowTopPx,
        immersiveOverlayBottomPaddingPx = SceneImmersiveBottomPadDp * density,
        avatarPx = SceneAvatarDp * density,
        titleHeightPx = titleHeightPx,
        statusHeightPx = statusHeightPx,
        badgesHeightPx = badgesHeightPx,
        gapAvatarTitlePx = SceneGapAvatarTitleDp * density,
        gapTitleStatusPx = SceneGapTitleStatusDp * density,
        gapStatusBadgesPx = SceneGapStatusBadgesDp * density,
        hasPhoto = hasPhoto,
        compactAvatar = compactAvatar,
        compactTitle = compactTitle,
        compactStatus = compactStatus,
        reduceMotion = reduceMotion,
    )
}

/** Converts a window rectangle from the compact toolbar geometry into the model's rectangle. */
internal fun Rect.toSceneRect(): SceneRect = SceneRect(left, top, right, bottom)
