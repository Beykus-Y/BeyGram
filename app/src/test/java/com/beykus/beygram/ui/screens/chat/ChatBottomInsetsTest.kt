package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatBottomInsetsTest {
    @Test
    fun keyboardReplacementDoesNotReserveNavigationTwice() {
        assertEquals(276, chatKeyboardReplacementHeight(300, 24))
        assertEquals(252, chatKeyboardReplacementHeight(300, 48))
        assertEquals(300, chatKeyboardReplacementHeight(300, 0))
        assertEquals(0, chatKeyboardReplacementHeight(12, 24))
        assertEquals(0, chatKeyboardReplacementHeight(0, 24))
    }

    @Test
    fun reverseTimelineIgnoresRowsBehindBottomAndTopChrome() {
        // With content padding, the physical bottom is at -80, the readable
        // area starts at zero, and the header obscures offsets 360..400.
        assertEquals(0, chatVisibleItemHeight(-72, 40, -80, 400, 40, 80))
        assertEquals(10, chatVisibleItemHeight(-10, 20, -80, 400, 40, 80))
        assertEquals(24, chatVisibleItemHeight(0, 24, -80, 400, 40, 80))
        assertEquals(10, chatVisibleItemHeight(350, 20, -80, 400, 40, 80))
        assertEquals(0, chatVisibleItemHeight(360, 20, -80, 400, 40, 80))
        assertEquals(360, chatUncoveredViewportHeight(480, 40, 80))
        assertEquals(0, chatUncoveredViewportHeight(80, 40, 80))
    }
}
