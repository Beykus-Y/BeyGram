package com.beykus.beygram.ui.screens.share

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.FolderChatPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareRecipientBrowserTest {
    private fun chat(id: Long, write: Boolean = true, member: Boolean = false) =
        ChatPreview(id, "Chat $id", "C", UiText.Raw(""), "", 0, 0,
            canPost = write, folderPositions = if (member) mapOf(7 to FolderChatPosition(10, false)) else emptyMap())

    @Test fun folderOnlyChatsAndMembershipAreRespected() {
        val first = chat(1, member = true)
        val hidden = chat(2)
        val cached = chat(3)
        val denied = chat(4, write = false, member = true)
        val directory = ShareRecipientDirectory(listOf(first, hidden, cached, denied),
            folderLists = mapOf(7 to listOf(cached)))
        assertEquals(setOf(1L, 3L), folderShareCandidates(directory, 7, "").map { it.id }.toSet())
        assertEquals(setOf(1L, 2L, 3L), folderShareCandidates(directory, null, "").map { it.id }.toSet())
        assertEquals(listOf(3L), folderShareCandidates(directory, 7, "Chat 3").map { it.id })
    }
}
