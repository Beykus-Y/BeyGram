package com.beykus.beygram.ui.screens.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatsScreenStateTest {
    @Test fun saverKeepsSearchSelectedFolderAndScrollPosition() {
        val state = ChatsScreenState(
            query = "bey",
            selectedFolderId = 7,
            firstVisibleItemIndex = 12,
            firstVisibleItemScrollOffset = 34
        )

        val parts = state.saveableParts()

        assertEquals("bey", parts[0])
        assertEquals(7, parts[1])
        assertEquals(12, parts[2])
        assertEquals(34, parts[3])
        // Search writes no folder position: the folder keeps only the list position it had before the search.
        assertEquals(emptyList<Int>(), (parts[4] as IntArray).toList())
        val restored = ChatsScreenStateSaver.restore(parts)!!
        assertEquals("bey", restored.query)
        assertEquals(7, restored.selectedFolderId)
        assertEquals(12, restored.listState.firstVisibleItemIndex)
        assertEquals(34, restored.listState.firstVisibleItemScrollOffset)
    }

    @Test fun searchDoesNotWriteItsPositionOrHeaderIntoAFolder() {
        val state = ChatsScreenState.restore(
            query = "bey",
            selectedFolderId = 0,
            firstVisibleItemIndex = 30,
            firstVisibleItemScrollOffset = 5,
            folderPositions = emptyMap()
        )

        state.selectFolder(4, headerProgress = 0.7f)

        assertEquals(0, state.listState.firstVisibleItemIndex)
        assertEquals(null, state.savedHeaderProgress(0))
        state.query = ""
        state.selectFolder(0)
        assertEquals(0, state.listState.firstVisibleItemIndex)
    }

    @Test fun choosingAnotherFolderStartsItAtTheTop() {
        val state = ChatsScreenState(firstVisibleItemIndex = 30, firstVisibleItemScrollOffset = 5)

        state.selectFolder(4)

        assertEquals(4, state.selectedFolderId)
        assertEquals(0, state.listState.firstVisibleItemIndex)
        assertEquals(0, state.listState.firstVisibleItemScrollOffset)
    }

    @Test fun choosingTheSameFolderKeepsTheScrollPosition() {
        val state = ChatsScreenState(firstVisibleItemIndex = 30, firstVisibleItemScrollOffset = 5)

        state.selectFolder(0)

        assertEquals(0, state.selectedFolderId)
        assertEquals(30, state.listState.firstVisibleItemIndex)
        assertEquals(5, state.listState.firstVisibleItemScrollOffset)
    }

    @Test fun returningToAFolderRestoresItsOwnScrollPosition() {
        val state = ChatsScreenState.restore(
            query = "",
            selectedFolderId = 0,
            firstVisibleItemIndex = 30,
            firstVisibleItemScrollOffset = 5,
            folderPositions = mapOf(4 to ChatListScrollPosition(index = 12, offset = 3))
        )

        state.selectFolder(4)

        assertEquals(12, state.listState.firstVisibleItemIndex)
        assertEquals(3, state.listState.firstVisibleItemScrollOffset)

        state.selectFolder(0)

        assertEquals(30, state.listState.firstVisibleItemIndex)
        assertEquals(5, state.listState.firstVisibleItemScrollOffset)
    }

    @Test fun folderPositionsSurviveSaveAndRestore() {
        val state = ChatsScreenState.restore(
            query = "",
            selectedFolderId = 4,
            firstVisibleItemIndex = 12,
            firstVisibleItemScrollOffset = 3,
            folderPositions = mapOf(0 to ChatListScrollPosition(index = 30, offset = 5))
        )

        val restored = ChatsScreenStateSaver.restore(state.saveableParts())!!

        assertEquals(4, restored.selectedFolderId)
        assertEquals(12, restored.listState.firstVisibleItemIndex)
        restored.selectFolder(0)
        assertEquals(30, restored.listState.firstVisibleItemIndex)
        assertEquals(5, restored.listState.firstVisibleItemScrollOffset)
    }

    @Test fun restoreAcceptsStateSavedBeforePerFolderPositions() {
        val restored = ChatsScreenStateSaver.restore(listOf<Any>("", 0, 8, 2))!!

        assertEquals(0, restored.selectedFolderId)
        assertEquals(8, restored.listState.firstVisibleItemIndex)
        assertEquals(2, restored.listState.firstVisibleItemScrollOffset)
    }

    @Test fun encodesChatAndSystemRowKeysAndIgnoresOthers() {
        assertEquals("c:42", encodeChatListKey(42L))
        assertEquals("s:story_bar", encodeChatListKey("story_bar"))
        assertNull(encodeChatListKey(3))
        assertNull(encodeChatListKey(null))
    }

    @Test fun chatListItemKeysFollowTheLazyColumnOrder() {
        val keys = chatListItemKeys(
            showStoryBar = true,
            showArchiveRow = true,
            pinnedIds = listOf(1L),
            showLocalSearchHeader = false,
            regularIds = listOf(2L, 3L),
            showFolderFooter = false
        )

        assertEquals(
            listOf("s:story_bar", "s:archive_row", "s:pinned_header", "c:1", "s:pinned_separator", "c:2", "c:3"),
            keys
        )
    }

    @Test fun emptyFolderShowsEmptyStateBeforeTheFooter() {
        val keys = chatListItemKeys(
            showStoryBar = false,
            showArchiveRow = false,
            pinnedIds = emptyList(),
            showLocalSearchHeader = true,
            regularIds = emptyList(),
            showFolderFooter = true
        )

        assertEquals(listOf("s:local_search_header", "s:empty_state", "s:server_folder_load_more"), keys)
    }

    @Test fun anchorFollowsItsRowAfterReorder() {
        val before = listOf("s:story_bar", "c:1", "c:2", "c:3")
        val after = listOf("s:story_bar", "c:3", "c:2", "c:1")
        val position = ChatListScrollPosition(index = 1, offset = 4, anchorKey = "c:2")

        assertEquals(2, resolveChatListAnchorIndex(before, position.copy(index = 1)))
        assertEquals(2, resolveChatListAnchorIndex(after, position))
    }

    @Test fun missingAnchorFallsBackToClampedIndex() {
        val keys = listOf("c:1", "c:2")

        assertEquals(1, resolveChatListAnchorIndex(keys, ChatListScrollPosition(5, 0, "c:9")))
        assertEquals(0, resolveChatListAnchorIndex(keys, ChatListScrollPosition(-3, 0, null)))
    }

    @Test fun emptyKeysHaveNoAnchorIndex() {
        assertNull(resolveChatListAnchorIndex(emptyList(), ChatListScrollPosition(2, 0, "c:1")))
    }

    @Test fun saverRoundTripKeepsAnchors() {
        val state = ChatsScreenState.restore(
            query = "",
            selectedFolderId = 4,
            firstVisibleItemIndex = 12,
            firstVisibleItemScrollOffset = 3,
            folderPositions = mapOf(0 to ChatListScrollPosition(index = 30, offset = 5, anchorKey = "c:9")),
            firstVisibleAnchorKey = "c:77"
        )

        val parts = state.saveableParts()

        // Anchors follow the folder order (0, then 4); an empty string means no anchor.
        assertEquals(listOf("c:9", "c:77"), parts[5])
        val restored = ChatsScreenStateSaver.restore(parts)!!
        assertEquals(ChatListScrollPosition(12, 3, "c:77"), restored.pendingAnchor)
        restored.clearPendingAnchor()
        restored.selectFolder(0)
        assertEquals(ChatListScrollPosition(30, 5, "c:9"), restored.pendingAnchor)
        assertEquals(30, restored.listState.firstVisibleItemIndex)
    }

    @Test fun restoreAcceptsStateSavedWithoutAnchors() {
        val restored = ChatsScreenStateSaver.restore(listOf<Any>("", 4, 12, 3, intArrayOf(0, 30, 5, 4, 12, 3)))!!

        assertEquals(4, restored.selectedFolderId)
        assertEquals(12, restored.listState.firstVisibleItemIndex)
        assertNull(restored.pendingAnchor)
        restored.selectFolder(0)
        assertEquals(30, restored.listState.firstVisibleItemIndex)
    }

    @Test fun decodeAlignsAnchorsWithFolders() {
        val decoded = decodeChatListScrollPositions(intArrayOf(4, 12, 3), listOf("c:5"))

        assertEquals(mapOf(4 to ChatListScrollPosition(12, 3, "c:5")), decoded)
    }

    @Test fun pruneFoldersDropsDeletedFoldersOnly() {
        val state = ChatsScreenState.restore(
            query = "",
            selectedFolderId = 0,
            firstVisibleItemIndex = 30,
            firstVisibleItemScrollOffset = 5,
            folderPositions = mapOf(4 to ChatListScrollPosition(12, 3), 5 to ChatListScrollPosition(7, 0))
        )

        state.pruneFolders(setOf(0, 5))

        state.selectFolder(4)
        assertEquals(0, state.listState.firstVisibleItemIndex)
        state.selectFolder(5)
        assertEquals(7, state.listState.firstVisibleItemIndex)
        state.selectFolder(0)
        assertEquals(30, state.listState.firstVisibleItemIndex)
    }

    @Test fun headerProgressIsRememberedPerFolderAndPruned() {
        val state = ChatsScreenState()

        state.selectFolder(4, headerProgress = 0.5f)

        assertEquals(0.5f, state.savedHeaderProgress(0)!!, 0.0001f)
        state.selectFolder(0, headerProgress = 0.2f)
        assertEquals(0.2f, state.savedHeaderProgress(4)!!, 0.0001f)
        // Папка 4 удалена: её шапка забывается, а основной список свою сохраняет.
        state.pruneFolders(setOf(0))
        assertNull(state.savedHeaderProgress(4))
        assertEquals(0.5f, state.savedHeaderProgress(0)!!, 0.0001f)
    }

    @Test fun malformedPositionPayloadIsIgnored() {
        assertEquals(emptyMap<Int, ChatListScrollPosition>(), decodeChatListScrollPositions(intArrayOf(1, 2)))
    }
}
