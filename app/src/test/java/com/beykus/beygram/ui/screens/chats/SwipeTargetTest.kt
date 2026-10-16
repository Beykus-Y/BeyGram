package com.beykus.beygram.ui.screens.chats

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeTargetTest {
    private val revealPx = 168f
    private val dismissPx = 216f

    @Test fun farDragDismisses() {
        assertEquals(SwipeTarget.Dismissed, swipeTarget(offset = -240f, revealPx = revealPx, dismissPx = dismissPx, velocity = 0f))
    }

    @Test fun fastLeftFlingReveals() {
        assertEquals(SwipeTarget.Revealed, swipeTarget(offset = -20f, revealPx = revealPx, dismissPx = dismissPx, velocity = -1200f))
    }

    @Test fun dragPastHalfRevealsAndShortDragCloses() {
        assertEquals(SwipeTarget.Revealed, swipeTarget(offset = -90f, revealPx = revealPx, dismissPx = dismissPx, velocity = 0f))
        assertEquals(SwipeTarget.Closed, swipeTarget(offset = -40f, revealPx = revealPx, dismissPx = dismissPx, velocity = 0f))
    }

    @Test fun fastRightFlingClosesEvenPastHalf() {
        assertEquals(SwipeTarget.Closed, swipeTarget(offset = -120f, revealPx = revealPx, dismissPx = dismissPx, velocity = 1200f))
    }
}
