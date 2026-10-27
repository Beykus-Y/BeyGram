package com.beykus.beygram.ui.screens.chats

import org.junit.Assert.assertEquals
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
        assertEquals(listOf(7, 12, 34), (parts[4] as IntArray).toList())
        val restored = ChatsScreenStateSaver.restore(parts)!!
        assertEquals("bey", restored.query)
        assertEquals(7, restored.selectedFolderId)
        assertEquals(12, restored.listState.firstVisibleItemIndex)
        assertEquals(34, restored.listState.firstVisibleItemScrollOffset)
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

    @Test fun malformedPositionPayloadIsIgnored() {
        assertEquals(emptyMap<Int, ChatListScrollPosition>(), decodeChatListScrollPositions(intArrayOf(1, 2)))
    }
}
