package com.beykus.beygram.ui.components

import com.beykus.beygram.ui.screens.chats.ChatsTwoPaneMinWidthDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveNavigationRailTest {
    @Test fun phonesKeepTheBottomBar() {
        assertFalse(usesMainNavigationRail(0f))
        assertFalse(usesMainNavigationRail(360f))
        assertFalse(usesMainNavigationRail(599f))
        assertFalse(usesMainNavigationRail(599.9f))
    }

    @Test fun tabletsFoldsAndWideWindowsUseTheRail() {
        assertTrue(usesMainNavigationRail(600f))
        assertTrue(usesMainNavigationRail(800f))
        assertTrue(usesMainNavigationRail(1280f))
    }

    @Test fun railAndChatsTwoPaneShareTheSameBreakpoint() {
        // Рейка появляется ровно там, где список чатов уже стоит рядом с перепиской:
        // на такой ширине нижнее меню и две панели одновременно помещаются без тесноты.
        assertEquals(MainNavigationRailMinWidthDp, ChatsTwoPaneMinWidthDp, 0f)
    }

    @Test fun railKeepsRoomForALabelledTab() {
        assertTrue(MainNavigationRailWidth.value >= 80f)
    }
}
