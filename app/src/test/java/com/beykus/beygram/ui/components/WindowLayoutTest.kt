package com.beykus.beygram.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowLayoutTest {
    @Test fun phoneInPortraitKeepsTheBottomBar() {
        val layout = WindowLayout(393f, 851f)
        assertFalse(layout.twoPane)
        assertEquals(WindowNavigation.BottomBar, layout.navigation)
    }

    @Test fun phoneInLandscapeGetsTheSlimRailBecauseTheWindowIsShort() {
        val layout = WindowLayout(851f, 393f)
        assertTrue(layout.twoPane)
        assertTrue(layout.heightCompact)
        assertEquals(WindowNavigation.SlimRail, layout.navigation)
        assertTrue(layout.usesRail)
    }

    @Test fun unfoldedPhoneKeepsTheStyleBarInsideTheListPane() {
        val layout = WindowLayout(673f, 841f)
        assertTrue(layout.twoPane)
        assertFalse(layout.heightCompact)
        assertEquals(WindowNavigation.BottomBarInListPane, layout.navigation)
        assertFalse(layout.usesRail)
    }

    @Test fun wideTabletGetsTheLabelledRail() {
        val layout = WindowLayout(1280f, 800f)
        assertEquals(WindowNavigation.Rail, layout.navigation)
        assertTrue(layout.usesRail)
    }

    @Test fun heightDecidesNotOrientation() {
        // A tall narrow split-screen window and a short wide one are told apart by the height alone.
        assertEquals(WindowNavigation.SlimRail, WindowLayout(1280f, 479f).navigation)
        assertEquals(WindowNavigation.Rail, WindowLayout(1280f, 480f).navigation)
    }

    @Test fun anUnmeasuredWindowIsNeverCompact() {
        assertFalse(WindowLayout(0f, 0f).heightCompact)
    }

    @Test fun breakpointsMatchTheTwoPaneAndRailConstants() {
        assertEquals(MainNavigationRailMinWidthDp, WindowLayout.TwoPaneMinWidthDp, 0f)
        assertTrue(MainNavigationSlimRailWidth.value < MainNavigationRailWidth.value)
    }
}
