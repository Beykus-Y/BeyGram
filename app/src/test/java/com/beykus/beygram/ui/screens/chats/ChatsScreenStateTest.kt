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

        assertEquals(listOf<Any>("bey", 7, 12, 34), parts)
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
}
