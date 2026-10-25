package com.beykus.beygram.ui.screens.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveChatsLayoutTest {
    @Test fun phonesStaySinglePane() {
        assertFalse(isTwoPaneChatsWidth(0f))
        assertFalse(isTwoPaneChatsWidth(360f))
        assertFalse(isTwoPaneChatsWidth(599f))
        assertFalse(isTwoPaneChatsWidth(599.9f))
    }

    @Test fun tabletsFoldsAndWideWindowsUseTwoPanes() {
        assertTrue(isTwoPaneChatsWidth(600f))
        assertTrue(isTwoPaneChatsWidth(800f))
        assertTrue(isTwoPaneChatsWidth(1280f))
    }

    @Test fun listPaneKeepsAReadableWidthAndLeavesRoomForTheChat() {
        assertEquals(300f, chatListPaneWidthDp(600f), 0.01f)
        assertEquals(320f, chatListPaneWidthDp(800f), 0.01f)
        assertEquals(380f, chatListPaneWidthDp(1280f), 0.01f)
        assertEquals(380f, chatListPaneWidthDp(2000f), 0.01f)
        listOf(600f, 700f, 800f, 1000f, 1280f, 2000f).forEach { window ->
            val list = chatListPaneWidthDp(window)
            assertTrue("list pane must stay comfortable at $window", list >= 300f)
            assertTrue("list pane must not starve the chat at $window", window - list >= 299f)
        }
    }

    @Test fun onlyTheVisibleChatIsHighlighted() {
        assertEquals(42L, chatListHighlightedId(showChat = true, selectedChatId = 42L))
        assertNull(chatListHighlightedId(showChat = false, selectedChatId = 42L))
        assertNull(chatListHighlightedId(showChat = true, selectedChatId = 0L))
    }
}
