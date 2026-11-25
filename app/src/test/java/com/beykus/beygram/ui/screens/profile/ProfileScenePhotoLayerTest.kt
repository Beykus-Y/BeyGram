package com.beykus.beygram.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P1.2: the photo layer is a square, and the clip releases continuously toward the window top. */
class ProfileScenePhotoLayerTest {
    private val toolbarBottom = 56f
    private val compact = SceneRect(8f, 8f, 48f, 48f)

    private fun frame(reveal: Float, hasPhoto: Boolean, collapse: Float = 0f): ProfileSceneFrame {
        val scroll = collapse
        return profileSceneFrame(
            ProfileSceneInputs(
                scrollPx = scroll * 1000f,
                revealFraction = reveal,
                viewportWidthPx = 360f,
                viewportHeightPx = 800f,
                contentLeftPx = 0f,
                contentRightPx = 360f,
                marginPx = 0f,
                maxPhotoWidthPx = 420f,
                photoHeightFraction = 0.42f,
                stageTopPx = toolbarBottom,
                toolbarBottomPx = toolbarBottom,
                avatarPx = 112f,
                titleHeightPx = 34f,
                statusHeightPx = 20f,
                badgesHeightPx = 0f,
                gapAvatarTitlePx = 16f,
                gapTitleStatusPx = 4f,
                gapStatusBadgesPx = 0f,
                hasPhoto = hasPhoto,
                compactAvatar = compact,
                compactTitle = SceneRect(56f, 8f, 300f, 28f),
                compactStatus = SceneRect(56f, 28f, 300f, 48f),
                reduceMotion = false,
            ),
        )
    }

    @Test
    fun fullRevealLayerIsASquareOfTheLargerSide() {
        val f = frame(reveal = 1f, hasPhoto = true)
        // Content is 360 wide and the photo is 360 x 336, so the square side is 360.
        assertEquals(360f, f.layerSidePx, 0.0001f)
        assertEquals(f.layerSidePx, maxOf(f.photoWidthPx, f.photoHeightPx, 112f), 0.0001f)
    }

    @Test
    fun restLayerWithoutPhotoIsTheAvatar() {
        val f = frame(reveal = 0f, hasPhoto = false)
        assertEquals(112f, f.layerSidePx, 0.0001f)
    }

    @Test
    fun clipStaysAtTheToolbarBottomWhileTheHeaderIsExpanded() {
        assertEquals(toolbarBottom, clipTopFor(0f, toolbarBottom), 0.0001f)
        assertEquals(toolbarBottom, clipTopFor(0.8f, toolbarBottom), 0.0001f)
    }

    @Test
    fun clipIsReleasedAtFullCollapseSoTheCompactAvatarIsNeverCut() {
        assertEquals(0f, clipTopFor(1f, toolbarBottom), 0.0001f)
        val f = frame(reveal = 1f, hasPhoto = true, collapse = 1f)
        assertEquals(0f, f.clipTopPx, 0.0001f)
        assertTrue(f.clipTopPx <= compact.top)
    }

    @Test
    fun clipReleaseIsContinuousAndMonotone() {
        var previous = clipTopFor(0f, toolbarBottom)
        var collapse = 0f
        while (collapse <= 1f) {
            val current = clipTopFor(collapse, toolbarBottom)
            assertTrue(current <= previous + 0.0001f)
            previous = current
            collapse += 0.01f
        }
        // No jump at the start of the release: a 0.01 step moves the edge by at most the linear slope (toolbar / 0.2 per unit).
        val jump = clipTopFor(0.8f, toolbarBottom) - clipTopFor(0.81f, toolbarBottom)
        assertTrue(jump <= toolbarBottom * 0.01f / 0.2f + 0.001f)
    }
}
