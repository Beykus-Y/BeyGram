package com.beykus.beygram.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSceneStageInputsTest {
    private val compact = SceneRect(20f, 10f, 60f, 50f)

    @Test
    fun identityColumnSidePaddingIsTheContentEdgeAndTheAvatarIsCentred() {
        // The identity column already has 20 dp side padding, so the stage edges are 20 px and 380 px in a 400 px window.
        val inputs = profileSceneScreenInputs(
            density = 1f, windowWidthPx = 400f, windowHeightPx = 800f, scrollPx = 0f,
            stageTopPx = 80f, toolbarBottomPx = 56f,
            titleHeightPx = 34f, statusHeightPx = 20f, badgesHeightPx = 0f,
            hasPhoto = false, reduceMotion = false,
            compactAvatar = compact, compactTitle = compact, compactStatus = compact,
        )
        assertEquals(0f, inputs.contentLeftPx, 0f)
        assertEquals(400f, inputs.contentRightPx, 0f)
        assertEquals(20f, inputs.marginPx, 0f)

        val frame = profileSceneFrame(inputs)
        // Centre 200, avatar 112 px: left 144, top at the stage top.
        assertEquals(144f, frame.avatarRect.left, 0.01f)
        assertEquals(80f, frame.avatarRect.top, 0.01f)
        // Travel brings the stage bottom to the toolbar bottom.
        assertEquals(frame.stageHeightPx + 80f - 56f, frame.travelPx, 0.01f)
    }
}
