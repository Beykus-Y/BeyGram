package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramChatFoldersTest {
    private fun chat(id: Long, mainOrder: Long = 0, folders: Map<Int, FolderChatPosition>) = ChatPreview(
        id = id, title = "chat $id", initials = "C", lastMessage = UiText.Raw(""),
        lastMessageTime = "", unreadCount = 0, avatarColorArgb = 0L,
        order = mainOrder, folderPositions = folders
    )

    @Test fun tdlibServerTabOrderIncludesPremiumMainPosition() {
        val metadata = TelegramChatFolders(
            initialized = true,
            folders = listOf(TelegramChatFolder(11, "Work"), TelegramChatFolder(12, "Family")),
            mainChatListPosition = 1
        )
        assertEquals(listOf(TelegramFolderTab.Folder(11), TelegramFolderTab.Main, TelegramFolderTab.Folder(12)),
            orderedTelegramFolderTabs(metadata))
        assertEquals(listOf(TelegramFolderTab.Main), orderedTelegramFolderTabs(TelegramChatFolders()))
    }

    @Test fun deletedFolderInvalidatesSelectionWithoutLocalCopy() {
        val before = TelegramChatFolders(true, listOf(TelegramChatFolder(7, "Friends")), 0)
        assertEquals(7, validTelegramFolderSelection(before, 7))
        assertNull(validTelegramFolderSelection(before.copy(folders = emptyList()), 7))
        assertNull(validTelegramFolderSelection(TelegramChatFolders(), 7))
    }

    @Test fun folderShowsChatsNotInMainAndKeepsIndependentPinnedOrder() {
        val first = chat(1, mainOrder = 500, folders = mapOf(7 to FolderChatPosition(100, pinned = false)))
        val second = chat(2, mainOrder = 0, folders = mapOf(7 to FolderChatPosition(300, pinned = true)))
        val third = chat(3, mainOrder = 250, folders = mapOf(8 to FolderChatPosition(999, pinned = true)))
        val result = orderedTelegramFolderChats(listOf(first, second, third), 7)
        assertEquals(listOf(2L, 1L), result.map { it.id })
        assertTrue(result.first().isPinned)
        assertFalse(first.isPinned)
        assertEquals(500L, first.order)
        assertEquals(listOf(3L), orderedTelegramFolderChats(listOf(first, second, third), 8).map { it.id })
    }

    @Test fun zeroOrderIsRemovedAndOrdersFollowTdlib() {
        val first = chat(1, folders = mapOf(3 to FolderChatPosition(0, false)))
        val second = chat(2, folders = mapOf(3 to FolderChatPosition(25, false)))
        val third = chat(3, folders = mapOf(3 to FolderChatPosition(30, false)))
        assertEquals(listOf(3L, 2L), orderedTelegramFolderChats(listOf(first, second, third), 3).map { it.id })
    }

    @Test fun mainListDoesNotIncludeFolderOnlyChat() {
        val chat = chat(42, mainOrder = 0, folders = mapOf(7 to FolderChatPosition(500, true)))
        assertTrue(orderedMainChats(listOf(chat)).isEmpty())
        assertEquals(listOf(42L), orderedTelegramFolderChats(listOf(chat), 7).map { it.id })
    }
}
