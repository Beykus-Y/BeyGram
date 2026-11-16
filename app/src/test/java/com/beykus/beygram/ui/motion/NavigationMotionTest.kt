package com.beykus.beygram.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.LayoutDirection
import com.beykus.beygram.RouteTransitionFrame
import com.beykus.beygram.navigation.AppRoute
import com.beykus.beygram.routeContentKey
import com.beykus.beygram.routeHostContentKey
import com.beykus.beygram.routeHostTransitionMove
import com.beykus.beygram.routeTransitionMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMotionTest {
    private val pushSpec = MotionSpec(durationMillis = 260, intensity = 1f, easing = FastOutSlowInEasing)
    private val popSpec = MotionSpec(durationMillis = 240, intensity = 1f, easing = FastOutSlowInEasing)
    private val tabSpec = MotionSpec(durationMillis = 200, intensity = 1f, easing = FastOutSlowInEasing)
    private val instantSpec = MotionSpec(durationMillis = 0, intensity = 0f, easing = FastOutSlowInEasing)

    @Test
    fun pushPlacesIncomingScreenAboveOutgoingAndUsesFullWidthEnterWithParallaxExit() {
        val transform = routeContentTransform(NavigationMove.Push, pushSpec, popSpec, tabSpec)
        assertTrue(transform.targetContentZIndex > 0f)
        assertNotEquals(EnterTransition.None, transform.targetContentEnter)
        assertNotEquals(ExitTransition.None, transform.initialContentExit)

        val ltr = routeSlideOffsets(NavigationMove.Push, fullWidth = 1000, intensity = 1f, layoutDirection = LayoutDirection.Ltr)
        assertEquals(1000, ltr.enterOffsetX)
        assertEquals(-220, ltr.exitOffsetX)

        val rtl = routeSlideOffsets(NavigationMove.Push, fullWidth = 1000, intensity = 1f, layoutDirection = LayoutDirection.Rtl)
        assertEquals(-1000, rtl.enterOffsetX)
        assertEquals(220, rtl.exitOffsetX)
    }

    @Test
    fun popKeepsOutgoingScreenAboveIncomingAndSlidesOffWhileIncomingReturnsFromParallax() {
        val transform = routeContentTransform(NavigationMove.Pop, pushSpec, popSpec, tabSpec)
        assertTrue(transform.targetContentZIndex < 0f)
        assertNotEquals(EnterTransition.None, transform.targetContentEnter)
        assertNotEquals(ExitTransition.None, transform.initialContentExit)

        val ltr = routeSlideOffsets(NavigationMove.Pop, fullWidth = 1000, intensity = 1f, layoutDirection = LayoutDirection.Ltr)
        assertEquals(-220, ltr.enterOffsetX)
        assertEquals(1000, ltr.exitOffsetX)

        val rtl = routeSlideOffsets(NavigationMove.Pop, fullWidth = 1000, intensity = 1f, layoutDirection = LayoutDirection.Rtl)
        assertEquals(220, rtl.enterOffsetX)
        assertEquals(-1000, rtl.exitOffsetX)
    }

    @Test
    fun tabKeepsBothScreensAtZeroZIndexAndSlidesEdgeToEdgeWithCorrectSigns() {
        val forward = routeContentTransform(NavigationMove.Tab, pushSpec, popSpec, tabSpec, tabDirection = 1)
        val backward = routeContentTransform(NavigationMove.Tab, pushSpec, popSpec, tabSpec, tabDirection = -1)
        assertEquals(0f, forward.targetContentZIndex, 0f)
        assertEquals(0f, backward.targetContentZIndex, 0f)
        assertNotEquals(EnterTransition.None, forward.targetContentEnter)
        assertNotEquals(ExitTransition.None, forward.initialContentExit)

        val ltrForward = routeSlideOffsets(NavigationMove.Tab, fullWidth = 1000, intensity = 1f, tabDirection = 1, layoutDirection = LayoutDirection.Ltr)
        val ltrBackward = routeSlideOffsets(NavigationMove.Tab, fullWidth = 1000, intensity = 1f, tabDirection = -1, layoutDirection = LayoutDirection.Ltr)
        assertEquals(1000, ltrForward.enterOffsetX)
        assertEquals(-1000, ltrForward.exitOffsetX)
        assertEquals(-1000, ltrBackward.enterOffsetX)
        assertEquals(1000, ltrBackward.exitOffsetX)

        val rtlForward = routeSlideOffsets(NavigationMove.Tab, fullWidth = 1000, intensity = 1f, tabDirection = 1, layoutDirection = LayoutDirection.Rtl)
        val rtlBackward = routeSlideOffsets(NavigationMove.Tab, fullWidth = 1000, intensity = 1f, tabDirection = -1, layoutDirection = LayoutDirection.Rtl)
        assertEquals(-1000, rtlForward.enterOffsetX)
        assertEquals(1000, rtlForward.exitOffsetX)
        assertEquals(1000, rtlBackward.enterOffsetX)
        assertEquals(-1000, rtlBackward.exitOffsetX)
    }

    @Test
    fun noneMoveProducesImmediateCut() {
        val transform = routeContentTransform(NavigationMove.None, pushSpec, popSpec, tabSpec)
        assertEquals(EnterTransition.None, transform.targetContentEnter)
        assertEquals(ExitTransition.None, transform.initialContentExit)
        assertEquals(RouteSlideOffsets(0, 0), routeSlideOffsets(NavigationMove.None, 1000, 1f))
    }

    @Test
    fun instantSpecsProduceImmediateCutForAllMoves() {
        for (move in NavigationMove.entries) {
            val transform = routeContentTransform(move, instantSpec, instantSpec, instantSpec)
            assertEquals(EnterTransition.None, transform.targetContentEnter)
            assertEquals(ExitTransition.None, transform.initialContentExit)
        }
    }

    @Test
    fun zeroIntensityProducesImmediateCutWithoutBlankOrMidScreenJump() {
        val zeroIntensity = MotionSpec(durationMillis = 200, intensity = 0f, easing = FastOutSlowInEasing)
        for (move in listOf(NavigationMove.Push, NavigationMove.Pop, NavigationMove.Tab)) {
            val transform = routeContentTransform(move, zeroIntensity, zeroIntensity, zeroIntensity)
            assertEquals(EnterTransition.None, transform.targetContentEnter)
            assertEquals(ExitTransition.None, transform.initialContentExit)
            assertEquals(RouteSlideOffsets(0, 0), routeSlideOffsets(move, 1000, 0f))
        }
    }

    @Test
    fun parallaxFractionScalesWithIntensityAndStaysBounded() {
        assertEquals(0f, routeParallaxFraction(-1f), 0.0001f)
        assertEquals(0f, routeParallaxFraction(0f), 0.0001f)
        assertTrue(routeParallaxFraction(0.5f) < routeParallaxFraction(1f))
        assertTrue(routeParallaxFraction(1f) < routeParallaxFraction(1.5f))
        assertTrue(routeParallaxFraction(3f) <= 0.33f)
    }

    @Test
    fun twoPaneContentKeyAndResizeSuppressesStaleRouteMove() {
        assertEquals(AppRoute.Chats, routeContentKey(AppRoute.Chats, twoPane = true))
        assertEquals(AppRoute.Chats, routeContentKey(AppRoute.Chat, twoPane = true))
        assertEquals(AppRoute.Chats, routeContentKey(AppRoute.ChatInfo, twoPane = true))
        assertEquals(AppRoute.Chats, routeContentKey(AppRoute.Archive, twoPane = true))
        assertEquals(AppRoute.Chats, routeContentKey(AppRoute.GlobalSearch, twoPane = true))
        assertEquals(AppRoute.Chats, routeContentKey(AppRoute.NewChat, twoPane = true))
        assertEquals(AppRoute.Chat, routeContentKey(AppRoute.Chat, twoPane = false))
        assertEquals(AppRoute.Chat, routeContentKey(AppRoute.MessageThread, twoPane = false))

        // Fold/resize on the same twoPaneSlotRoute must cut cleanly instead of replaying a stale Push/Pop.
        assertEquals(
            NavigationMove.None,
            routeTransitionMove(
                initial = RouteTransitionFrame(AppRoute.Chat, twoPane = false),
                target = RouteTransitionFrame(AppRoute.Chat, twoPane = true),
                routeMove = NavigationMove.Push
            )
        )
        assertEquals(
            NavigationMove.None,
            routeTransitionMove(
                initial = RouteTransitionFrame(AppRoute.ChatInfo, twoPane = true),
                target = RouteTransitionFrame(AppRoute.ChatInfo, twoPane = false),
                routeMove = NavigationMove.Pop
            )
        )
        assertEquals(
            NavigationMove.Tab,
            routeTransitionMove(
                initial = RouteTransitionFrame(AppRoute.Chat, twoPane = true),
                target = RouteTransitionFrame(AppRoute.Settings, twoPane = true),
                routeMove = NavigationMove.Tab
            )
        )
    }
    @Test
    fun phoneRootTabsShareOnlyOuterSlotWhileNestedAndWideRoutesKeepTheirKeys() {
        val roots = MainTab.entries.map { RouteTransitionFrame(it.route, false) }
        assertEquals(1, roots.map(::routeHostContentKey).distinct().size)
        val chats = roots.first()
        val settings = roots.last()
        val nested = RouteTransitionFrame(AppRoute.Chat, false)
        val wideChats = RouteTransitionFrame(AppRoute.Chats, true)
        val wideChat = RouteTransitionFrame(AppRoute.Chat, true)
        assertTrue(routeHostContentKey(chats) != routeHostContentKey(nested))
        assertTrue(routeHostContentKey(chats) != routeHostContentKey(wideChats))
        assertEquals(routeHostContentKey(wideChats), routeHostContentKey(wideChat))
        assertEquals(NavigationMove.None, routeHostTransitionMove(chats, settings, NavigationMove.Tab))
        assertEquals(NavigationMove.Push, routeHostTransitionMove(chats, nested, NavigationMove.Push))
        assertEquals(NavigationMove.Pop, routeHostTransitionMove(nested, chats, NavigationMove.Pop))
    }
}
