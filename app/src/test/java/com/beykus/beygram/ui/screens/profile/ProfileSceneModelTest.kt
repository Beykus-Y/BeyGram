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
        contentLeft: Float = 0f,
        contentRight: Float = width,
        stageTop: Float = toolbarBottom,
        hasPhoto: Boolean = true,
        titleHeight: Float = 34f,
        statusHeight: Float = 20f,
        badgesHeight: Float = 0f,
        gapStatusBadges: Float = 0f,
        reduceMotion: Boolean = false,
    ) = ProfileSceneInputs(
        scrollPx = scroll,
        revealFraction = reveal,
        viewportWidthPx = width,
        viewportHeightPx = height,
        contentLeftPx = contentLeft,
        contentRightPx = contentRight,
        marginPx = 20f,
        maxPhotoWidthPx = 420f,
        photoHeightFraction = 0.42f,
        stageTopPx = stageTop,
        toolbarBottomPx = toolbarBottom,
        avatarPx = 112f,
        titleHeightPx = titleHeight,
        statusHeightPx = statusHeight,
        badgesHeightPx = badgesHeight,
        gapAvatarTitlePx = 16f,
        gapTitleStatusPx = 4f,
        gapStatusBadgesPx = gapStatusBadges,
        hasPhoto = hasPhoto,
        compactAvatar = SceneRect(20f, 10f, 60f, 50f),
        compactTitle = SceneRect(70f, 12f, 300f, 36f),
        compactStatus = SceneRect(70f, 36f, 300f, 52f),
        reduceMotion = reduceMotion,
    )

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f) =
        assertEquals(expected.toDouble(), actual.toDouble(), tolerance.toDouble())

    @Test
    fun travelFollowsTheCurrentStageHeightAtAnyReveal() {
        for (reveal in listOf(0f, 0.5f, 1f)) {
            val frame = profileSceneFrame(inputs(reveal = reveal))
            assertClose(frame.stageHeightPx, frame.travelPx, 0.0001f)
            // Collapse is complete exactly when the stage bottom reaches the toolbar bottom.
            assertClose(1f, profileSceneFrame(inputs(reveal = reveal, scroll = frame.stageHeightPx)).collapse, 0f)
        }
    }

    @Test
    fun travelBringsTheStageBottomToTheToolbarBottom() {
        val stageTop = 80f
        val frame = profileSceneFrame(inputs(stageTop = stageTop, reveal = 0f))
        val atEnd = profileSceneFrame(inputs(stageTop = stageTop, reveal = 0f, scroll = frame.travelPx))
        assertClose(toolbarBottom, atEnd.stageBottomPx, 0.01f)
        assertClose(1f, atEnd.collapse, 0f)
    }

    @Test
    fun collapseIsClampedToZeroAndOne() {
        assertClose(0f, profileSceneFrame(inputs(scroll = -5f)).collapse, 0f)
        assertClose(1f, profileSceneFrame(inputs(scroll = 1_000_000f)).collapse, 0f)
    }

    @Test
    fun collapseIsMonotonicInScroll() {
        val travel = profileSceneFrame(inputs()).travelPx
        var previous = -1f
        var scroll = 0f
        while (scroll <= travel * 1.5f) {
            val collapse = profileSceneFrame(inputs(scroll = scroll)).collapse
            assertTrue("collapse decreased at scroll=$scroll", collapse >= previous)
            previous = collapse
            scroll += 7f
        }
    }

    @Test
    fun nanScrollIsTreatedAsTheTop() {
        val frame = profileSceneFrame(inputs(scroll = Float.NaN, reveal = 1f))
        assertClose(0f, frame.collapse, 0f)
        assertClose(0f, frame.parallaxPx, 0f)
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
    fun photoWithRevealDisabledKeepsTheLayerAtAvatarSize() {
        // 0.42 * 200 = 84 < 112: reveal is off, so the layer is the avatar and the scale at rest is 1.
        val frame = profileSceneFrame(inputs(width = 360f, height = 200f, reveal = 1f))
        assertFalse(frame.revealEnabled)
        assertClose(112f, frame.avatarRect.width)
        assertClose(1f, frame.layerScale, 0.001f)
    }

    @Test
    fun restingAvatarIsCenteredUnderTheToolbar() {
        val frame = profileSceneFrame(inputs(scroll = 0f, reveal = 0f, width = 360f))
        assertEquals(SceneRect(124f, 56f, 236f, 168f), frame.avatarRect)
    }

    @Test
    fun insetsMoveTheCentreAndTheContentEdges() {
        // Content runs from x=40 to x=400 in a 400 px window (a cutout on the left): centre 220.
        val frame = profileSceneFrame(inputs(width = 400f, contentLeft = 40f, contentRight = 400f, reveal = 0f))
        assertEquals(SceneRect(164f, 56f, 276f, 168f), frame.avatarRect)
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
        // Portrait 360 dp wide: full-bleed width 360, height min(360, 0.42 * 800) = 336.
        val portrait = profileSceneFrame(inputs(width = 360f, height = 800f))
        assertClose(360f, portrait.photoWidthPx)
        assertClose(336f, portrait.photoHeightPx)
        // Landscape 800 wide, 360 high: width min(800, 420) = 420, height min(420, 0.42 * 360) = 151.2.
        val landscape = profileSceneFrame(inputs(width = 800f, height = 360f))
        assertClose(420f, landscape.photoWidthPx)
        assertClose(151.2f, landscape.photoHeightPx)
    }

    @Test
    fun zeroSizedViewportDoesNotBreakTheModel() {
        val frame = profileSceneFrame(inputs(width = 0f, height = 0f, reveal = 1f))
        assertFalse(frame.revealEnabled)
        assertTrue(frame.collapse.isFinite() && frame.layerScale.isFinite() && frame.parallaxPx.isFinite())
    }

    @Test
    fun revealIsDisabledWhenThePhotoWouldBeShorterThanTheAvatar() {
        val tiny = profileSceneFrame(inputs(width = 360f, height = 200f, reveal = 1f))
        assertFalse(tiny.revealEnabled)
        assertClose(112f, tiny.avatarRect.width)
    }

    @Test
    fun halfRevealedAvatarIsBetweenTheCircleAndThePhoto() {
        val half = profileSceneFrame(inputs(reveal = 0.5f, scroll = 0f))
        assertTrue(half.avatarRect.width > 112f && half.avatarRect.width < 320f)
    }

    @Test
    fun photoLayerCoversItsClipInEveryState() {
        for (reveal in listOf(0f, 0.5f, 1f)) {
            for (scroll in listOf(0f, 100f, 400f, 2_000f)) {
                val frame = profileSceneFrame(inputs(scroll = scroll, reveal = reveal))
                val layerW = frame.layerSidePx * frame.layerScale
                val layerH = frame.layerSidePx * frame.layerScale
                assertTrue("width covers at reveal=$reveal scroll=$scroll", layerW + 0.01f >= frame.avatarRect.width)
                assertTrue("height covers at reveal=$reveal scroll=$scroll", layerH + 0.01f >= frame.avatarRect.height)
            }
        }
    }

    @Test
    fun noExtraZoomAtRestWithoutReveal() {
        // Reveal 0, photo present: the layer scale is exactly the cover scale (overscale not applied).
        val frame = profileSceneFrame(inputs(scroll = 0f, reveal = 0f))
        assertClose(112f / 360f, frame.layerScale, 0.001f)
        assertClose(0f, frame.parallaxPx, 0f)
    }

    @Test
    fun parallaxIsZeroAtRestAndWithReducedMotion() {
        assertClose(0f, profileSceneFrame(inputs(scroll = 0f, reveal = 1f)).parallaxPx, 0f)
        assertClose(0f, profileSceneFrame(inputs(scroll = 50f, reveal = 1f, reduceMotion = true)).parallaxPx, 0f)
    }

    @Test
    fun parallaxScalesWithRevealAndStaysWithinSlack() {
        val open = profileSceneFrame(inputs(scroll = 50f, reveal = 1f))
        assertClose(10f * (1f - open.collapse), open.parallaxPx)
        // Half reveal at scroll 20: raw shift 4 px is under the slack (about 10 px), then scaled by 0.5 gives 2 px.
        // (At scroll 50 the slack is about 9.3 px, so the shift is clamped to about 4.65 px.)
        val halfOpen = profileSceneFrame(inputs(scroll = 20f, reveal = 0.5f))
        assertClose(2f * (1f - halfOpen.collapse), halfOpen.parallaxPx)
        // At full collapse the clip is 40 px: layer 320 * 0.125 * 1.2 = 48 px, slack (48 - 40) / 2 = 4 px.
        val far = profileSceneFrame(inputs(scroll = 10_000f, reveal = 1f))
        assertClose(0f, far.parallaxPx)
        assertClose(far.avatarRect.width / far.layerSidePx, far.layerScale)
    }

    @Test
    fun fullyCollapsedIdentityStaysInsideTheToolbar() {
        for (reveal in listOf(0f, 1f)) {
            val frame = profileSceneFrame(inputs(scroll = 1_000_000f, reveal = reveal))
            assertClose(1f, frame.collapse, 0f)
            assertTrue("bottom ${frame.displayedBottomPx} inside toolbar", frame.displayedBottomPx <= toolbarBottom + 0.01f)
        }
    }

    @Test
    fun missingStatusAddsNoGapAndNoSpace() {
        // 112 avatar + 16 gap + 34 name, no status and no badges: 162 px at rest.
        val frame = profileSceneFrame(inputs(statusHeight = 0f, reveal = 0f))
        assertClose(162f, frame.stageHeightPx, 0.0001f)
    }

    @Test
    fun separateGapsAreAppliedBetweenNameAndStatus() {
        val frame = profileSceneFrame(inputs(reveal = 0f))
        // Name top at the stage top plus the avatar block and the 16 dp gap; status 4 dp below the name's bottom.
        assertClose(56f + 112f + 16f, frame.titleRect.top, 0.01f)
        assertClose(frame.titleRect.bottom + 4f, frame.statusRect.top, 0.01f)
    }

    @Test
    fun badgesGapAppliesOnlyWhenBadgesHaveHeight() {
        val without = profileSceneFrame(inputs(reveal = 0f, badgesHeight = 0f, gapStatusBadges = 6f))
        val with = profileSceneFrame(inputs(reveal = 0f, badgesHeight = 20f, gapStatusBadges = 6f))
        assertClose(6f + 20f, with.stageHeightPx - without.stageHeightPx, 0.0001f)
    }

    @Test
    fun zeroHeightNameAddsNoGapAboveIt() {
        // No name: the stage is the avatar block only, and the name rect is empty.
        val frame = profileSceneFrame(inputs(titleHeight = 0f, statusHeight = 0f, reveal = 0f))
        assertClose(112f, frame.stageHeightPx, 0.0001f)
        assertClose(0f, frame.titleRect.height, 0f)
    }

    @Test
    fun badgesTopFollowsTheStatusWithoutStatus() {
        // No status: the badges sit under the name with the status-to-badges gap, and no status gap is used.
        val frame = profileSceneFrame(inputs(statusHeight = 0f, badgesHeight = 20f, gapStatusBadges = 6f, reveal = 0f))
        assertClose(frame.titleRect.bottom + 6f, frame.badgesRect.top, 0.01f)
        assertClose(20f, frame.badgesRect.height, 0.01f)
    }

    @Test
    fun travelEqualsStageHeightWithoutStatus() {
        val frame = profileSceneFrame(inputs(statusHeight = 0f, reveal = 0f))
        assertClose(frame.stageHeightPx, frame.travelPx, 0.0001f)
        assertClose(1f, profileSceneFrame(inputs(statusHeight = 0f, reveal = 0f, scroll = frame.stageHeightPx)).collapse, 0f)
    }

    @Test
    fun pullDownStretchesOnlyTheRevealedPhotoAtRestAndCapsItsHeight() {
        val open = inputs(scroll = 0f, reveal = 1f)
        val base = profileSceneFrame(open)
        val stretched = profileSceneFrame(open.copy(pullDownPx = 10_000f))
        assertClose(base.avatarRect.height + open.avatarPx / 2f, stretched.avatarRect.height)
        assertClose(base.avatarRect.width, stretched.avatarRect.width)
        val reduced = profileSceneFrame(open.copy(pullDownPx = 10_000f, reduceMotion = true))
        assertClose(base.avatarRect.height, reduced.avatarRect.height)
        val scrolled = open.copy(scrollPx = 20f)
        assertEquals(profileSceneFrame(scrolled), profileSceneFrame(scrolled.copy(pullDownPx = 80f)))
    }
}
