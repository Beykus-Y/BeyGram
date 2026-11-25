package com.beykus.beygram.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileExpandedPhotoTest {
    @Test
    fun maxShiftIsHalfTheExtraScaleOfTheBox() {
        // 260 px box at 1.2x: 0.1 of the box on each side, so 26 px.
        assertEquals(26.0, parallaxMaxPx(260f).toDouble(), 0.01)
        assertEquals(0.0, parallaxMaxPx(0f).toDouble(), 0.0)
    }

    @Test
    fun shiftIsZeroAtTopOfPage() {
        assertEquals(0.0, parallaxOffsetPx(0f, 26f, reduceMotion = false).toDouble(), 0.0)
    }

    @Test
    fun shiftFollowsScrollSlowerThanPage() {
        // 50 px of scroll gives a 10 px shift, so the photo trails the page.
        assertEquals(10.0, parallaxOffsetPx(50f, 26f, reduceMotion = false).toDouble(), 0.01)
    }

    @Test
    fun shiftNeverExceedsTheSlackSoNoEdgeShows() {
        assertEquals(26.0, parallaxOffsetPx(10_000f, 26f, reduceMotion = false).toDouble(), 0.01)
    }

    @Test
    fun reducedMotionKeepsPhotoStill() {
        assertEquals(0.0, parallaxOffsetPx(500f, 26f, reduceMotion = true).toDouble(), 0.0)
    }

    @Test
    fun nonFiniteOrEmptySlackGivesNoShift() {
        assertEquals(0.0, parallaxOffsetPx(Float.NaN, 26f, reduceMotion = false).toDouble(), 0.0)
        assertEquals(0.0, parallaxOffsetPx(50f, 0f, reduceMotion = false).toDouble(), 0.0)
    }
}
