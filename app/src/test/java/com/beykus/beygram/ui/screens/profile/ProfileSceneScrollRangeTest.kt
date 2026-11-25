package com.beykus.beygram.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scroll range of the profile scene must always cover the stage travel, so every profile can fully collapse. */
class ProfileSceneScrollRangeTest {
    private val toolbarBottom = 56f
    private val viewport = 800f

    private fun frameFor(stageHeightTitle: Float, statusHeight: Float): ProfileSceneFrame = profileSceneFrame(
        ProfileSceneInputs(
            scrollPx = 0f,
            revealFraction = 0f,
            viewportWidthPx = 360f,
            viewportHeightPx = viewport,
            contentLeftPx = 0f,
            contentRightPx = 360f,
            marginPx = 0f,
            maxPhotoWidthPx = 420f,
            photoHeightFraction = 0f,
            stageTopPx = toolbarBottom,
            toolbarBottomPx = toolbarBottom,
            avatarPx = 112f,
            titleHeightPx = stageHeightTitle,
            statusHeightPx = statusHeight,
            badgesHeightPx = 0f,
            gapAvatarTitlePx = 16f,
            gapTitleStatusPx = 4f,
            gapStatusBadgesPx = 0f,
            hasPhoto = false,
            compactAvatar = SceneRect(0f, 0f, 40f, 40f),
            compactTitle = SceneRect(48f, 0f, 200f, 24f),
            compactStatus = SceneRect(48f, 24f, 200f, 40f),
            reduceMotion = false,
        ),
    )

    @Test
    fun scrollRangeCoversTheTravelForShortProfiles() {
        // A short profile has little content, so the minimum must come from the viewport plus travel.
        val frame = frameFor(stageHeightTitle = 34f, statusHeight = 20f)
        val minContent = profileSceneMinContentHeightPx(viewport, frame.travelPx)
        assertTrue(minContent - viewport >= frame.travelPx)
    }

    @Test
    fun fullScrollReachesFullCollapse() {
        val frame = frameFor(stageHeightTitle = 34f, statusHeight = 20f)
        val maxScroll = profileSceneMinContentHeightPx(viewport, frame.travelPx) - viewport
        val atBottom = profileSceneFrame(
            ProfileSceneInputs(
                scrollPx = maxScroll,
                revealFraction = 0f,
                viewportWidthPx = 360f,
                viewportHeightPx = viewport,
                contentLeftPx = 0f,
                contentRightPx = 360f,
                marginPx = 0f,
                maxPhotoWidthPx = 420f,
                photoHeightFraction = 0f,
                stageTopPx = toolbarBottom,
                toolbarBottomPx = toolbarBottom,
                avatarPx = 112f,
                titleHeightPx = 34f,
                statusHeightPx = 20f,
                badgesHeightPx = 0f,
                gapAvatarTitlePx = 16f,
                gapTitleStatusPx = 4f,
                gapStatusBadgesPx = 0f,
                hasPhoto = false,
                compactAvatar = SceneRect(0f, 0f, 40f, 40f),
                compactTitle = SceneRect(48f, 0f, 200f, 24f),
                compactStatus = SceneRect(48f, 24f, 200f, 40f),
                reduceMotion = false,
            ),
        )
        assertEquals(1f, atBottom.collapse, 0.0001f)
    }

    @Test
    fun zeroTravelNeedsNoExtraContent() {
        assertEquals(viewport, profileSceneMinContentHeightPx(viewport, 0f), 0.0001f)
    }

    @Test
    fun negativeInputsAreClamped() {
        assertEquals(viewport, profileSceneMinContentHeightPx(viewport, -50f), 0.0001f)
        assertEquals(0f, profileSceneMinContentHeightPx(-10f, 0f), 0.0001f)
    }
}
