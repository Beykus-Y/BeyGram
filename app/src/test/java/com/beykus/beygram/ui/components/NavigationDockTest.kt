package com.beykus.beygram.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The main screen keeps its create button and its last row clear of the navigation. These numbers used to be fixed
 * literals; they now follow the measured dock, so a capsule and a pinned bar cannot share one wrong gap.
 */
class NavigationDockTest {
    @Test fun pinnedBarKeepsTheClassicButtonPosition() {
        val dock = NavigationDockGeometry(floating = false)
        val placement = createButtonPlacement(dock)

        assertEquals(20.dp, placement.endPadding)
        assertEquals(16.dp, placement.bottomPadding)
        // Same clearance the list had before the dock geometry existed, so Default parity is untouched.
        assertEquals(104.dp, placement.listClearance)
        assertEquals(0.dp, dock.reservedHeight)
    }

    @Test fun floatingCapsuleAlignsTheButtonWithItsSideMargin() {
        val dock = NavigationDockGeometry(
            floating = true,
            height = 72.dp,
            horizontalMargin = 16.dp,
            bottomMargin = 10.dp
        )
        val placement = createButtonPlacement(dock)

        assertEquals(20.dp, placement.endPadding)
        assertEquals(DefaultNavigationDockGap + 4.dp, placement.bottomPadding)
        // The content already ends above the capsule, so the button only needs its own small gap plus the dock's air.
        assertEquals(dock.height + dock.bottomMargin + DefaultNavigationDockGap, dock.reservedHeight)
    }

    @Test fun aWiderCapsuleMarginMovesTheButtonAndNotTheList() {
        val narrow = createButtonPlacement(NavigationDockGeometry(floating = true, horizontalMargin = 12.dp))
        val wide = createButtonPlacement(NavigationDockGeometry(floating = true, horizontalMargin = 20.dp))

        assertEquals(16.dp, narrow.endPadding)
        assertEquals(24.dp, wide.endPadding)
        assertEquals(narrow.listClearance, wide.listClearance)
    }

    @Test fun theListAlwaysKeepsRoomForTheWholeButton() {
        listOf(false, true).forEach { floating ->
            val placement = createButtonPlacement(NavigationDockGeometry(floating = floating))
            assertTrue(
                "the last row must scroll clear of the button",
                placement.listClearance >= CreateButtonSize + placement.bottomPadding
            )
        }
    }

    @Test fun anUnknownDockHeightNeverReservesNegativeSpace() {
        val dock = NavigationDockGeometry(floating = true, height = 0.dp, bottomMargin = 8.dp)
        assertEquals(DefaultNavigationDockGap + 8.dp, dock.reservedHeight)
    }
}
