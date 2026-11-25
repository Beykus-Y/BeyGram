package com.beykus.beygram.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSceneModelTest {
    private val toolbarBottom = 56f

    private fun inputs(
        scroll: Float = 0f,
        reveal: Float = 0f,
        width: Float = 360f,
        height: Float = 800f,
        hasPhoto: Boolean = true,
        titleHeight: Float = 34f,
        statusHeight: Float = 20f,
        reduceMotion: Boolean = false,
    ) = ProfileSceneInputs(
        scrollPx = scroll,
        revealFraction = reveal,
        viewportWidthPx = width,
        viewportHeightPx = height,
        marginPx = 20f,
        maxPhotoWidthPx = 420f,
        photoHeightFraction = 0.42f,
        stageTopPx = toolbarBottom,
        toolbarBottomPx = toolbarBottom,
        avatarPx = 112f,
        titleHeightPx = titleHeight,
        statusHeightPx = statusHeight,
        badgesHeightPx = 0f,
        gapPx = 8f,
        hasPhoto = hasPhoto,
        compactAvatar = SceneRect(20f, 10f, 60f, 50f),
        compactTitle = SceneRect(70f, 12f, 300f, 36f),
        compactStatus = SceneRect(70f, 36f, 300f, 52f),
        reduceMotion = reduceMotion,
    )

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f) =
        assertEquals(expected.toDouble(), actual.toDouble(), tolerance.toDouble())

    @Test
    fun travelDoesNotDependOnReveal() {
        val closed = profileSceneFrame(inputs(reveal = 0f))
        val open = profileSceneFrame(inputs(reveal = 1f))
        assertClose(closed.travelPx, open.travelPx, 0.0001f)
    }

    @Test
    fun collapseIsClampedToZeroAndOne() {
        assertClose(0f, profileSceneFrame(inputs(scroll = -5f)).collapse, 0f)
        assertClose(1f, profileSceneFrame(inputs(scroll = 1_000_000f)).collapse, 0f)
    }

    @Test
    fun revealCanChangeOnlyAtTheTopOfThePage() {
        assertTrue(profileSceneFrame(inputs(scroll = 0f)).canToggleReveal)
        assertFalse(profileSceneFrame(inputs(scroll = 1f)).canToggleReveal)
    }

    @Test
    fun withoutPhotoRevealIsIgnored() {
        val closed = profileSceneFrame(inputs(hasPhoto = false, reveal = 0f))
        val open = profileSceneFrame(inputs(hasPhoto = false, reveal = 1f))
        assertFalse(open.revealEnabled)
        assertEquals(closed.avatarRect, open.avatarRect)
        assertClose(closed.stageHeightPx, open.stageHeightPx, 0.0001f)
    }

    @Test
    fun restingAvatarIsCenteredUnderTheToolbar() {
        val frame = profileSceneFrame(inputs(scroll = 0f, reveal = 0f, width = 360f))
        // Width 360: centre 180, avatar 112 px wide, top at the toolbar bottom.
        assertEquals(SceneRect(124f, 56f, 236f, 168f), frame.avatarRect)
    }

    @Test
    fun longNameMakesTheIdentityTaller() {
        val short = profileSceneFrame(inputs(titleHeight = 34f))
        val long = profileSceneFrame(inputs(titleHeight = 102f))
        assertTrue(long.travelPx > short.travelPx)
        assertTrue(long.stageHeightPx > short.stageHeightPx)
    }

    @Test
    fun photoSizeIsBoundedByWidthAndHeight() {
        // Portrait 360 dp wide: width min(360 - 40, 420) = 320, height min(320, 0.42 * 800) = 320.
        val portrait = profileSceneFrame(inputs(width = 360f, height = 800f))
        assertClose(320f, portrait.photoWidthPx)
        assertClose(320f, portrait.photoHeightPx)
        // Landscape 800 wide, 360 high: width min(760, 420) = 420, height min(420, 0.42 * 360) = 151.2.
        val landscape = profileSceneFrame(inputs(width = 800f, height = 360f))
        assertClose(420f, landscape.photoWidthPx)
        assertClose(151.2f, landscape.photoHeightPx)
    }

    @Test
    fun revealIsDisabledWhenThePhotoWouldBeShorterThanTheAvatar() {
        val tiny = profileSceneFrame(inputs(width = 360f, height = 200f, reveal = 1f))
        // 0.42 * 200 = 84 < 112: reveal is off, the avatar stays at rest.
        assertFalse(tiny.revealEnabled)
        assertClose(112f, tiny.avatarRect.width)
    }

    @Test
    fun photoLayerCoversItsClipInEveryState() {
        for (reveal in listOf(0f, 0.5f, 1f)) {
            for (scroll in listOf(0f, 100f, 400f, 2_000f)) {
                val frame = profileSceneFrame(inputs(scroll = scroll, reveal = reveal))
                val layerW = frame.photoWidthPx * frame.layerScale
                val layerH = frame.photoHeightPx * frame.layerScale
                assertTrue("width covers at reveal=$reveal scroll=$scroll", layerW + 0.01f >= frame.avatarRect.width)
                assertTrue("height covers at reveal=$reveal scroll=$scroll", layerH + 0.01f >= frame.avatarRect.height)
            }
        }
    }

    @Test
    fun parallaxIsZeroAtRestAndWithReducedMotion() {
        assertClose(0f, profileSceneFrame(inputs(scroll = 0f, reveal = 1f)).parallaxPx, 0f)
        assertClose(0f, profileSceneFrame(inputs(scroll = 50f, reveal = 1f, reduceMotion = true)).parallaxPx, 0f)
    }

    @Test
    fun parallaxScalesWithRevealAndStaysWithinSlack() {
        val open = profileSceneFrame(inputs(scroll = 50f, reveal = 1f))
        // 50 px of scroll gives 10 px of shift; the slack at overscale 1.2 is (320 * 1.2 - 320) / 2 = 32 px.
        assertClose(10f, open.parallaxPx)
        val halfOpen = profileSceneFrame(inputs(scroll = 50f, reveal = 0.5f))
        assertClose(5f, halfOpen.parallaxPx)
        val far = profileSceneFrame(inputs(scroll = 10_000f, reveal = 1f))
        assertClose(32f, far.parallaxPx)
    }

    @Test
    fun fullyCollapsedIdentityStaysInsideTheToolbar() {
        for (reveal in listOf(0f, 1f)) {
            val frame = profileSceneFrame(inputs(scroll = 1_000_000f, reveal = reveal))
            assertClose(1f, frame.collapse, 0f)
            assertTrue("bottom ${frame.displayedBottomPx} inside toolbar", frame.displayedBottomPx <= toolbarBottom + 0.01f)
        }
    }
}
