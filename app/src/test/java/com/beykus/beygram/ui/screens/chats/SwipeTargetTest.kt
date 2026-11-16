package com.beykus.beygram.ui.screens.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeTargetTest {
    private val revealPx = 168f
    private val dismissPx = 216f

    @Test fun clearingSharedSwipeIdDoesNotRestorePendingArchive() {
        assertFalse(shouldAutoCloseSwipe(null, 42L, -300f, dismissalInProgress = true))
        assertFalse(shouldAutoCloseSwipe(99L, 42L, -300f, dismissalInProgress = true))
    }

    @Test fun successfullyArchivedRowRetainedInServerFolderCanCloseAfterOperation() {
        // The row must remain dismissed while TDLib is busy, but must be free to
        // reappear once the operation completes if the server folder still includes it.
        assertFalse(shouldAutoCloseSwipe(null, 42L, -300f, dismissalInProgress = true))
        assertTrue(shouldAutoCloseSwipe(null, 42L, -300f, dismissalInProgress = false))
    }

    @Test fun otherOpenRowClosesRegularRevealAndFailedArchiveCanRestore() {
        assertTrue(shouldAutoCloseSwipe(null, 42L, -100f, dismissalInProgress = false))
        assertTrue(shouldAutoCloseSwipe(99L, 42L, -300f, dismissalInProgress = false))
        assertFalse(shouldAutoCloseSwipe(42L, 42L, -100f, dismissalInProgress = false))
        assertFalse(shouldAutoCloseSwipe(null, 42L, 0f, dismissalInProgress = false))
    }

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

    @Test fun swipeRightRevealsItsOwnAction() {
        assertEquals(
            SwipeTarget.Revealed,
            bidirectionalSwipeTarget(offset = 100f, revealPx = 84f, dismissPx = 216f, velocity = 0f)
        )
    }

    @Test fun swipeRightCommitsAtThresholdAndBackFlingCloses() {
        assertEquals(
            SwipeTarget.Dismissed,
            bidirectionalSwipeTarget(offset = 250f, revealPx = 84f, dismissPx = 216f, velocity = 0f)
        )
        assertEquals(
            SwipeTarget.Closed,
            bidirectionalSwipeTarget(offset = 90f, revealPx = 84f, dismissPx = 216f, velocity = -1200f)
        )
    }

    @Test fun swipeDirectionCannotTriggerOtherSideWithZeroOffset() {
        assertEquals(
            SwipeTarget.Closed,
            bidirectionalSwipeTarget(offset = 0f, revealPx = 84f, dismissPx = 216f, velocity = 1200f)
        )
    }
}
