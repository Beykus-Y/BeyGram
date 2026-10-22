package com.beykus.beygram.ui.screens.share

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTargetCandidatesTest {
    private fun chat(
        id: Long, title: String = "Chat $id", order: Long = 0L,
        archive: Long = 0L, saved: Boolean = false, canPost: Boolean = true
    ) = ChatPreview(
        id = id, title = title, initials = "C", lastMessage = UiText.Raw(""),
        lastMessageTime = "", unreadCount = 0, avatarColorArgb = 0L,
        order = order, archiveOrder = archive, isSavedMessages = saved, canPost = canPost
    )

    @Test fun writableRecentsComeBeforeArchivedAndAlphabetical() {
        val list = listOf(chat(1, "A", 100), chat(2, "B", 500), chat(3, "C", archive = 300),
            chat(4, "D", canPost = false), chat(5, "Saved", saved = true))
        assertEquals(listOf(5L, 2L, 1L, 3L),
            shareTargetCandidates(list, "").map { it.id })
    }

    @Test fun quickSavedDoesNotDuplicateAndSearchRestoresIt() {
        val list = listOf(chat(9, "Избранное", saved = true), chat(8, "Notes", order = 40))
        assertEquals(listOf(8L), shareTargetCandidates(list, "", excludeSaved = true).map { it.id })
        assertEquals(listOf(9L), shareTargetCandidates(list, "изБР", excludeSaved = false).map { it.id })
    }

    @Test fun noForbiddenTargetsOrDuplicatedIds() {
        val list = listOf(chat(1, "Allowed", order = 4), chat(1, "Duplicated", order = 1),
            chat(2, "No write", order = 7, canPost = false), chat(3, ""))
        assertEquals(listOf(1L), shareTargetCandidates(list, "").map { it.id })
        assertTrue(shareTargetCandidates(list, "missing").isEmpty())
    }
}
