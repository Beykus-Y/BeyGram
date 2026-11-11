package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.ui.components.WindowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBubbleWidthTest {
    @Test fun phoneBubbleKeepsTheOldCap() {
        assertEquals(292f, chatBubbleMaxWidthDp(360f), 0.01f)
        assertEquals(292f, chatBubbleMaxWidthDp(0f), 0.01f)
    }

    @Test fun bubbleGrowsWithThePaneAndStopsAt480() {
        assertEquals(350f, chatBubbleMaxWidthDp(500f), 0.01f)
        assertEquals(480f, chatBubbleMaxWidthDp(900f), 0.01f)
        assertEquals(480f, chatBubbleMaxWidthDp(2000f), 0.01f)
    }

    @Test fun paneWidthIsWhatTheRailAndTheListLeave() {
        assertEquals(393f, chatPaneWidthDp(WindowLayout(393f, 851f)), 0.01f)
        // 851 - 56 rail - 320 list - 1 divider
        assertEquals(474f, chatPaneWidthDp(WindowLayout(851f, 393f)), 0.01f)
        assertTrue(chatPaneWidthDp(WindowLayout(1280f, 800f)) > 800f)
    }
}
