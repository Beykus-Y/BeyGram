package com.beykus.beygram.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
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
    fun pushPlacesIncomingScreenAboveOutgoingAndAnimatesBoth() {
        val transform = routeContentTransform(NavigationMove.Push, pushSpec, popSpec, tabSpec)
        assertTrue(transform.targetContentZIndex > 0f)
        assertNotEquals(EnterTransition.None, transform.targetContentEnter)
        assertNotEquals(ExitTransition.None, transform.initialContentExit)
    }

    @Test
    fun popKeepsOutgoingScreenAboveIncomingAndAnimatesBoth() {
        val transform = routeContentTransform(NavigationMove.Pop, pushSpec, popSpec, tabSpec)
        assertTrue(transform.targetContentZIndex < 0f)
        assertNotEquals(EnterTransition.None, transform.targetContentEnter)
        assertNotEquals(ExitTransition.None, transform.initialContentExit)
    }

    @Test
    fun tabKeepsBothScreensAtZeroZIndexAndAnimatesEdgeToEdge() {
        val forward = routeContentTransform(NavigationMove.Tab, pushSpec, popSpec, tabSpec, tabDirection = 1)
        val backward = routeContentTransform(NavigationMove.Tab, pushSpec, popSpec, tabSpec, tabDirection = -1)
        assertEquals(0f, forward.targetContentZIndex, 0f)
        assertEquals(0f, backward.targetContentZIndex, 0f)
        assertNotEquals(EnterTransition.None, forward.targetContentEnter)
        assertNotEquals(ExitTransition.None, forward.initialContentExit)
        assertNotEquals(forward.targetContentEnter, backward.targetContentEnter)
        assertNotEquals(forward.initialContentExit, backward.initialContentExit)
    }

    @Test
    fun noneMoveProducesImmediateCut() {
        val transform = routeContentTransform(NavigationMove.None, pushSpec, popSpec, tabSpec)
        assertEquals(EnterTransition.None, transform.targetContentEnter)
        assertEquals(ExitTransition.None, transform.initialContentExit)
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
}
