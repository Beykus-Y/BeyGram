package com.beykus.beygram.domain.model

import org.junit.Assert.*
import org.junit.Test

class TelegramFolderEditorTest {
    @Test fun rejectsBlankAndOverlongNames() {
        assertFalse(TelegramFolderDraft(title = "", includeGroups = true).valid())
        assertFalse(TelegramFolderDraft(title = "1234567890123", includeGroups = true).valid())
        assertFalse(TelegramFolderDraft(title = "abc\ndef", includeGroups = true).valid())
        assertTrue(TelegramFolderDraft(title = "Семья", includeGroups = true).valid())
    }

    @Test fun requiresAtLeastOneIncludeAndDisjointLists() {
        assertFalse(TelegramFolderDraft(title = "Важное").valid())
        assertTrue(TelegramFolderDraft(title = "Важное", includedChatIds = setOf(3L)).valid())
        assertFalse(TelegramFolderDraft(title = "Важное", includedChatIds = setOf(3L), excludedChatIds = setOf(3L)).valid())
    }

    @Test fun movesServerFolderWithoutLosingMainPosition() {
        val state = TelegramChatFolders(
            initialized = true, folders = listOf(
                TelegramChatFolder(11, "Work"),
                TelegramChatFolder(12, "Family"),
                TelegramChatFolder(13, "News")
            ), mainChatListPosition = 0
        )
        assertEquals(listOf(11, 13, 12), movedTelegramFolderIds(state, 12, 1))
        assertEquals(listOf(12, 11, 13), movedTelegramFolderIds(state, 12, -1))
        assertNull(movedTelegramFolderIds(state, 11, -1))
        assertNull(movedTelegramFolderIds(state, 40, 1))
    }

    @Test fun mainTabRemainsInServerSuppliedSlot() {
        val state = TelegramChatFolders(true, listOf(TelegramChatFolder(11, "Work"), TelegramChatFolder(12, "News")), 1)
        assertEquals(
            listOf(TelegramFolderTab.Folder(11), TelegramFolderTab.Main, TelegramFolderTab.Folder(12)),
            orderedTelegramFolderTabs(state)
        )
    }
}
