package com.beykus.beygram.ui.screens.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkCarouselTest {

    @Test
    fun transformIsContinuousFromStartToMidToCompleteAndIncomingReachesExactCenter() {
        val travel = 280f
        val startCurrent = carouselCardTransform(slotPosition = 0f, travelDistancePx = travel, reducedMotion = false)
        assertEquals(0f, startCurrent.translationX, 0.001f)
        assertEquals(1f, startCurrent.scale, 0.001f)
        assertEquals(0f, startCurrent.rotationY, 0.001f)
        assertEquals(1f, startCurrent.alpha, 0.001f)

        val midOffset = -0.5f
        val midCurrent = carouselCardTransform(slotPosition = midOffset, travelDistancePx = travel, reducedMotion = false)
        val midIncoming = carouselCardTransform(slotPosition = 1f + midOffset, travelDistancePx = travel, reducedMotion = false)
        assertTrue(midCurrent.translationX < 0f)
        assertTrue(midIncoming.translationX > 0f)
        assertTrue(midCurrent.scale in 0.84f..1f)
        assertTrue(midIncoming.scale in 0.84f..1f)
        assertTrue(midCurrent.rotationY > 0f)
        assertTrue(midIncoming.rotationY < 0f)

        val completeOffset = -1.0f
        val completedIncoming = carouselCardTransform(
            slotPosition = 1f + completeOffset,
            travelDistancePx = travel,
            reducedMotion = false,
        )
        assertEquals(0f, completedIncoming.translationX, 0.001f)
        assertEquals(1f, completedIncoming.scale, 0.001f)
        assertEquals(0f, completedIncoming.rotationY, 0.001f)
        assertEquals(1f, completedIncoming.alpha, 0.001f)
        assertEquals(1f, completedIncoming.zIndex, 0.001f)
    }

    @Test
    fun reducedMotionDisablesSpatialTranslationRotationAndScaling() {
        val travel = 280f
        val center = carouselCardTransform(slotPosition = 0f, travelDistancePx = travel, reducedMotion = true)
        assertEquals(0f, center.translationX, 0.001f)
        assertEquals(1f, center.scale, 0.001f)
        assertEquals(0f, center.rotationY, 0.001f)
        assertEquals(1f, center.alpha, 0.001f)

        val side = carouselCardTransform(slotPosition = 0.8f, travelDistancePx = travel, reducedMotion = true)
        assertEquals(0f, side.translationX, 0.001f)
        assertEquals(1f, side.scale, 0.001f)
        assertEquals(0f, side.rotationY, 0.001f)
        assertEquals(0f, side.alpha, 0.001f)
    }

    @Test
    fun swipeDecisionCommitsByDistanceOrFlingAndCancelsOtherwise() {
        assertEquals(-1, resolveCarouselSwipeDecision(-0.30f, 0f, canNext = true, canPrevious = true))
        assertEquals(1, resolveCarouselSwipeDecision(0.30f, 0f, canNext = true, canPrevious = true))
        assertEquals(-1, resolveCarouselSwipeDecision(-0.10f, -700f, canNext = true, canPrevious = true))
        assertEquals(1, resolveCarouselSwipeDecision(0.10f, 700f, canNext = true, canPrevious = true))
        assertEquals(0, resolveCarouselSwipeDecision(-0.18f, -120f, canNext = true, canPrevious = true))
        assertEquals(0, resolveCarouselSwipeDecision(-0.50f, -1200f, canNext = false, canPrevious = true))
        assertEquals(0, resolveCarouselSwipeDecision(0.50f, 1200f, canNext = true, canPrevious = false))
    }

    @Test
    fun edgeResistanceDampensDragPastQueueBounds() {
        assertEquals(-0.22f, applyCarouselEdgeResistance(-1.0f, canNext = false, canPrevious = true), 0.001f)
        assertEquals(0.22f, applyCarouselEdgeResistance(1.0f, canNext = true, canPrevious = false), 0.001f)
        assertEquals(-0.6f, applyCarouselEdgeResistance(-0.6f, canNext = true, canPrevious = false), 0.001f)
    }
}
