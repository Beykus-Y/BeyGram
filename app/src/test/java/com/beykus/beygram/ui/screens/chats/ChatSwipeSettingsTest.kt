package com.beykus.beygram.ui.screens.chats

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSwipeSettingsTest {
    @Test fun defaultsPreserveArchiveLeftAndReadRight() {
        val defaults = ChatSwipePreferences()
        assertEquals(ChatSwipeActionKind.Archive, defaults.left)
        assertEquals(ChatSwipeActionKind.ReadUnread, defaults.right)
    }

    @Test fun damagedPreferencesFallBackWithoutCrashing() {
        assertEquals(
            ChatSwipeActionKind.Archive,
            ChatSwipeActionKind.fromStored("RemovedAction", ChatSwipeActionKind.Archive)
        )
        assertEquals(
            ChatSwipeActionKind.Mute,
            ChatSwipeActionKind.fromStored("Mute", ChatSwipeActionKind.ReadUnread)
        )
    }
}
