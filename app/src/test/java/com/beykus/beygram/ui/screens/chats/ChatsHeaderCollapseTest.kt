package com.beykus.beygram.ui.screens.chats

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatsHeaderCollapseTest {
    @Test fun initialListPositionKeepsLargeHeader() {
        assertFalse(shouldCompactChatListHeader(0, 0, 96))
        assertFalse(shouldCompactChatListHeader(0, 32, 96))
        assertFalse(shouldCompactChatListHeader(0, 96, 96))
    }

    @Test fun scrollingPastThresholdCompactsHeader() {
        assertTrue(shouldCompactChatListHeader(0, 97, 96))
        assertTrue(shouldCompactChatListHeader(2, 0, 96))
    }

    @Test fun returningToBeginningRestoresExpandedHeader() {
        assertTrue(shouldCompactChatListHeader(1, 0, 96))
        assertFalse(shouldCompactChatListHeader(0, 0, 96))
    }
}
