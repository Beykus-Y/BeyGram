package com.beykus.beygram.ui.screens.playback

import androidx.compose.ui.geometry.Rect
import com.beykus.beygram.ui.motion.MotionSpec
import com.beykus.beygram.ui.motion.MotionToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerExpansionTest {
    private val eps = 0.001f

    @Before
    fun resetController() {
        // The controller is a singleton: every test starts closed, with instant motion and no host.
        PlayerExpansion.dismissImmediately()
        PlayerExpansion.motion = MotionSpec(durationMillis = 0, intensity = 0f, easing = MotionToken.PlayerExpand.easing)
        PlayerExpansion.scope = null
        PlayerExpansion.density = 1f
        PlayerExpansion.statusTopPx = 0f
        PlayerExpansion.rootCoords = null
    }

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, eps)
        assertEquals(expected.top, actual.top, eps)
        assertEquals(expected.right, actual.right, eps)
        assertEquals(expected.bottom, actual.bottom, eps)
    }

    @Test
    fun rampClampsAndInterpolatesBetweenItsEnds() {
        assertEquals(0f, ramp(-1f, 0f, 1f), 0f)
        assertEquals(0.5f, ramp(0.5f, 0f, 1f), eps)
        assertEquals(1f, ramp(2f, 0f, 1f), 0f)
        assertEquals(0.125f, ramp(0.25f, 0.2f, 0.6f), eps)
    }

    @Test
    fun smoothIsClampedAndCentred() {
        assertEquals(0f, smooth(-1f), 0f)
        assertEquals(0.5f, smooth(0.5f), eps)
        assertEquals(1f, smooth(3f), 0f)
    }

    @Test
    fun lerpHelpersInterpolateEveryEdge() {
        assertEquals(12.5f, lerpFloat(10f, 20f, 0.25f), eps)
        assertRect(Rect(5f, 10f, 20f, 35f), lerpRect(Rect(0f, 0f, 10f, 10f), Rect(10f, 20f, 30f, 60f), 0.5f))
    }

    @Test
    fun fullPlayerRectIsCappedAndClearsTheStatusBarAndHandle() {
        val narrow = fullPlayerRect(width = 400f, height = 800f, statusTop = 24f, density = 1f)
        assertEquals(0f, narrow.left, eps)
        assertEquals(400f, narrow.right, eps)
        assertEquals(24f + 0.06f * (800f - 24f - 48f), narrow.top, eps)
        assertEquals(800f, narrow.bottom, eps)

        val wide = fullPlayerRect(width = 1000f, height = 800f, statusTop = 0f, density = 1f)
        assertEquals(180f, wide.left, eps)
        assertEquals(820f, wide.right, eps)
    }

    @Test
    fun containerGrowsFromTheBarOrRisesFromBelowWithoutOne() {
        val full = Rect(0f, 100f, 400f, 900f)
        val bar = Rect(0f, 800f, 400f, 856f)
        assertRect(bar, containerRectAt(0f, bar, full))
        assertRect(full, containerRectAt(1f, bar, full))
        // Without a bar the player rises: halfway it starts half its height lower.
        assertRect(Rect(0f, 500f, 400f, 1300f), containerRectAt(0.5f, null, full))
    }

    @Test
    fun dragTravelIsTheWayFromTheBarToFullSizeWithAMinimum() {
        val full = Rect(0f, 60f, 400f, 800f)
        val lowBar = Rect(0f, 700f, 400f, 756f)
        val nearTopBar = Rect(0f, 70f, 400f, 126f)
        val topBar = Rect(0f, 4f, 400f, 60f)
        assertEquals(640f, dragTravelPx(PlayerDragOrigin.Mini, MiniAnchorEdge.Bottom, lowBar, full, 1f), eps)
        // A bar already near the top of the full rect still needs the minimum travel.
        assertEquals(96f, dragTravelPx(PlayerDragOrigin.Mini, MiniAnchorEdge.Bottom, nearTopBar, full, 1f), eps)
        assertEquals(740f, dragTravelPx(PlayerDragOrigin.Mini, MiniAnchorEdge.Top, topBar, full, 1f), eps)
    }

    @Test
    fun dragDirectionFollowsTheBarEdgeAndFullDragCollapsesDownwards() {
        // A bottom bar grows upwards, so the finger moves up to expand it; a top bar grows downwards.
        assertEquals(0.5f, progressDelta(PlayerDragOrigin.Mini, MiniAnchorEdge.Bottom, -100f, 200f), eps)
        assertEquals(-0.5f, progressDelta(PlayerDragOrigin.Mini, MiniAnchorEdge.Bottom, 100f, 200f), eps)
        assertEquals(0.5f, progressDelta(PlayerDragOrigin.Mini, MiniAnchorEdge.Top, 100f, 200f), eps)
        assertEquals(-0.5f, progressDelta(PlayerDragOrigin.Mini, MiniAnchorEdge.Top, -100f, 200f), eps)
        // The full player collapses when the finger moves down.
        assertEquals(-0.5f, progressDelta(PlayerDragOrigin.Full, MiniAnchorEdge.Bottom, 100f, 200f), eps)
        // No travel means no movement at all.
        assertEquals(0f, progressDelta(PlayerDragOrigin.Full, MiniAnchorEdge.Bottom, 100f, 0f), 0f)
    }

    @Test
    fun releaseSettlesByFlingDirectionThenByPlace() {
        // A fast fling decides by its direction.
        assertEquals(1f, endDragTarget(PlayerDragOrigin.Mini, MiniAnchorEdge.Bottom, 0f, -2000f, 1f), 0f)
        assertEquals(0f, endDragTarget(PlayerDragOrigin.Mini, MiniAnchorEdge.Bottom, 1f, 2000f, 1f), 0f)
        assertEquals(1f, endDragTarget(PlayerDragOrigin.Mini, MiniAnchorEdge.Top, 0f, 2000f, 1f), 0f)
        assertEquals(0f, endDragTarget(PlayerDragOrigin.Mini, MiniAnchorEdge.Top, 1f, -2000f, 1f), 0f)
        assertEquals(0f, endDragTarget(PlayerDragOrigin.Full, MiniAnchorEdge.Bottom, 0.9f, 2000f, 1f), 0f)
        assertEquals(1f, endDragTarget(PlayerDragOrigin.Full, MiniAnchorEdge.Bottom, 0.1f, -2000f, 1f), 0f)
        // A slow release decides by its place: the bar opens past 30 %, the full player closes below 70 %.
        assertEquals(1f, endDragTarget(PlayerDragOrigin.Mini, MiniAnchorEdge.Bottom, 0.4f, 0f, 1f), 0f)
        assertEquals(0f, endDragTarget(PlayerDragOrigin.Mini, MiniAnchorEdge.Bottom, 0.2f, 0f, 1f), 0f)
        assertEquals(0f, endDragTarget(PlayerDragOrigin.Full, MiniAnchorEdge.Bottom, 0.6f, 0f, 1f), 0f)
        assertEquals(1f, endDragTarget(PlayerDragOrigin.Full, MiniAnchorEdge.Bottom, 0.8f, 0f, 1f), 0f)
    }

    @Test
    fun miniChromeFadesOnlyForTheBarThatStartedTheMove() {
        assertEquals(0.5f, miniChromeAlphaOf(1L, 1L, PlayerExpansionPhase.Collapsing, 0.15f), eps)
        assertEquals(1f, miniChromeAlphaOf(2L, 1L, PlayerExpansionPhase.Collapsing, 0.15f), 0f)
        assertEquals(1f, miniChromeAlphaOf(1L, 1L, PlayerExpansionPhase.Collapsed, 0f), 0f)
    }

    @Test
    fun miniSharedElementsHideOnlyWhileTheirOwnFlightMoves() {
        val roles = setOf(FlightRole.Title)
        assertTrue(miniSharedHiddenOf(1L, FlightRole.Title, 1L, true, PlayerExpansionPhase.Expanding, 0.2f, roles))
        assertFalse(miniSharedHiddenOf(2L, FlightRole.Title, 1L, true, PlayerExpansionPhase.Expanding, 0.2f, roles))
        // Before the first frame of movement nothing is hidden yet.
        assertFalse(miniSharedHiddenOf(1L, FlightRole.Title, 1L, true, PlayerExpansionPhase.Expanding, 0f, roles))
        assertFalse(miniSharedHiddenOf(1L, FlightRole.Artwork, 1L, true, PlayerExpansionPhase.Expanding, 0.2f, roles))
        assertFalse(miniSharedHiddenOf(1L, FlightRole.Title, 1L, false, PlayerExpansionPhase.Expanding, 0.2f, roles))
    }

    @Test
    fun fullSharedElementsHideFromTheStartOfTheFlightUntilItEnds() {
        val roles = setOf(FlightRole.Artwork)
        assertTrue(fullSharedHiddenOf(FlightRole.Artwork, true, PlayerExpansionPhase.Expanding, roles))
        assertTrue(fullSharedHiddenOf(FlightRole.Artwork, true, PlayerExpansionPhase.Collapsing, roles))
        assertFalse(fullSharedHiddenOf(FlightRole.Artwork, true, PlayerExpansionPhase.Expanded, roles))
        assertFalse(fullSharedHiddenOf(FlightRole.Artwork, false, PlayerExpansionPhase.Expanding, roles))
        assertFalse(fullSharedHiddenOf(FlightRole.Title, true, PlayerExpansionPhase.Expanding, roles))
    }

    @Test
    fun openWithoutAHostScopeLandsExpandedAtOnce() {
        PlayerExpansion.open(null)
        assertEquals(PlayerExpansionPhase.Expanded, PlayerExpansion.phase)
        assertEquals(1f, PlayerExpansion.progress, 0f)
        assertNull(PlayerExpansion.activeAnchorId)
        assertFalse(PlayerExpansion.flights)
    }

    @Test
    fun collapseWithoutAnHostScopeClosesAtOnce() {
        PlayerExpansion.open(null)
        PlayerExpansion.collapse()
        assertEquals(PlayerExpansionPhase.Collapsed, PlayerExpansion.phase)
        assertEquals(0f, PlayerExpansion.progress, 0f)
    }

    @Test
    fun dismissImmediatelyResetsEveryTransitionValue() {
        PlayerExpansion.open(null)
        assertTrue(PlayerExpansion.beginDrag(PlayerDragOrigin.Full, null))
        PlayerExpansion.dragBy(100f)
        PlayerExpansion.dismissImmediately()
        assertEquals(PlayerExpansionPhase.Collapsed, PlayerExpansion.phase)
        assertEquals(0f, PlayerExpansion.progress, 0f)
        assertNull(PlayerExpansion.activeAnchorId)
        assertFalse(PlayerExpansion.flights)
        assertTrue(PlayerExpansion.flyingRoles.isEmpty())
        assertFalse(PlayerExpansion.anchorLost)
        assertNull(PlayerExpansion.lastSource)
    }

    @Test
    fun beginDragFromTheMiniBarNeedsABarOnScreen() {
        assertFalse(PlayerExpansion.beginDrag(PlayerDragOrigin.Mini, null))
        assertEquals(PlayerExpansionPhase.Collapsed, PlayerExpansion.phase)
    }

    @Test
    fun draggingTheFullPlayerMovesProgressClampsAndCollapses() {
        PlayerExpansion.open(null)
        assertTrue(PlayerExpansion.beginDrag(PlayerDragOrigin.Full, null))
        assertEquals(PlayerExpansionPhase.Dragging, PlayerExpansion.phase)
        // Without a bar the travel is the full height of the player, so half of it moves the progress half way.
        val travel = fullPlayerRect(400f, 1000f, 0f, 1f).height
        PlayerExpansion.dragBy(travel / 2f)
        assertEquals(0.5f, PlayerExpansion.progress, eps)
        // A drag past the bar stops at the bar, it does not go below it.
        PlayerExpansion.dragBy(10_000f)
        assertEquals(0f, PlayerExpansion.progress, 0f)
        PlayerExpansion.endDrag(0f)
        assertEquals(PlayerExpansionPhase.Collapsed, PlayerExpansion.phase)
    }

    @Test
    fun aFastFlingUpSettlesAHalfDraggedPlayerOpen() {
        PlayerExpansion.open(null)
        assertTrue(PlayerExpansion.beginDrag(PlayerDragOrigin.Full, null))
        val travel = fullPlayerRect(400f, 1000f, 0f, 1f).height
        PlayerExpansion.dragBy(travel / 2f)
        // The player is still half way; a fling up (negative y) wins over the place.
        PlayerExpansion.endDrag(-2000f)
        assertEquals(PlayerExpansionPhase.Expanded, PlayerExpansion.phase)
        assertEquals(1f, PlayerExpansion.progress, 0f)
    }

    @Test
    fun predictiveBackShrinksTheFullPlayerByAtMostThePeek() {
        PlayerExpansion.open(null)
        assertTrue(PlayerExpansion.beginDrag(PlayerDragOrigin.Back, null))
        PlayerExpansion.backProgress(0.5f)
        assertEquals(1f - BACK_PEEK * 0.5f, PlayerExpansion.progress, eps)
        PlayerExpansion.backProgress(2f)
        assertEquals(1f - BACK_PEEK, PlayerExpansion.progress, eps)
    }
}
