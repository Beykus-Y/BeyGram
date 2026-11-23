package com.beykus.beygram.share

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectShareCandidateTest {
    private fun chat(id: Long, group: Boolean = false, post: Boolean = true, title: String = "Chat $id") =
        ChatPreview(id = id, title = title, initials = "C", lastMessage = UiText.Raw(""),
            lastMessageTime = "", unreadCount = 0, avatarColorArgb = 0L, isGroup = group, canPost = post)

    @Test fun chatOnlyInFolderIsPreselected() {
        val folderChat = chat(10)
        val catalog = shareRecipientCatalog(emptyList(), emptyList(), mapOf(1 to listOf(folderChat)))
        assertEquals(folderChat, directShareCandidate(10L, catalog))
    }

    @Test fun chatOnlyInArchiveIsPreselected() {
        val archivedChat = chat(20)
        val catalog = shareRecipientCatalog(emptyList(), listOf(archivedChat), emptyMap())
        assertEquals(archivedChat, directShareCandidate(20L, catalog))
    }

    @Test fun groupChatIsNotPreselected() {
        val catalog = shareRecipientCatalog(listOf(chat(30, group = true)), emptyList(), emptyMap())
        assertNull(directShareCandidate(30L, catalog))
    }

    @Test fun chatWithoutPostRightIsNotPreselected() {
        val catalog = shareRecipientCatalog(listOf(chat(40, post = false)), emptyList(), emptyMap())
        assertNull(directShareCandidate(40L, catalog))
    }

    @Test fun zeroChatIdMeansNoCandidate() {
        val catalog = shareRecipientCatalog(listOf(chat(0)), emptyList(), emptyMap())
        assertNull(directShareCandidate(0L, catalog))
    }

    @Test fun duplicatesAcrossListsAreRemovedFromCatalog() {
        val inMain = chat(50, title = "Main")
        val inArchive = chat(50, title = "Archive copy")
        val inFolder = chat(50, title = "Folder copy")
        val catalog = shareRecipientCatalog(
            listOf(inMain),
            listOf(inArchive),
            mapOf(1 to listOf(inFolder), 2 to listOf(chat(51)))
        )
        assertEquals(listOf(inMain, chat(51)), catalog)
    }
}
