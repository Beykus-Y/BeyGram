package com.beykus.beygram.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSceneWiringTest {
    private val compact = SceneRect(20f, 10f, 60f, 50f)

    @Test
    fun dpValuesAreScaledByDensity() {
        val inputs = profileSceneInputsFor(
            density = 2f, viewportWidthPx = 720f, viewportHeightPx = 1600f, contentLeftPx = 0f, contentRightPx = 720f,
            scrollPx = 0f, stageTopPx = 112f, toolbarBottomPx = 112f, titleHeightPx = 68f, statusHeightPx = 40f,
            badgesHeightPx = 0f, hasPhoto = true, reduceMotion = false,
            compactAvatar = compact, compactTitle = compact, compactStatus = compact,
        )
        assertEquals(224f, inputs.avatarPx, 0.001f)
        assertEquals(16f, inputs.gapPx, 0.001f)
        assertEquals(40f, inputs.marginPx, 0.001f)
        assertEquals(0f, inputs.revealFraction, 0f)
    }

    @Test
    fun wiredInputsNeverRevealInP2() {
        val inputs = profileSceneInputsFor(
            density = 3f, viewportWidthPx = 1080f, viewportHeightPx = 2400f, contentLeftPx = 0f, contentRightPx = 1080f,
            scrollPx = 0f, stageTopPx = 168f, toolbarBottomPx = 168f, titleHeightPx = 100f, statusHeightPx = 60f,
            badgesHeightPx = 0f, hasPhoto = true, reduceMotion = true,
            compactAvatar = compact, compactTitle = compact, compactStatus = compact,
        )
        val frame = profileSceneFrame(inputs)
        assertEquals(0f, frame.parallaxPx, 0f)
        assertEquals(0f, frame.collapse, 0f)
        assertEquals(0f, inputs.revealFraction, 0f)
    }
}
