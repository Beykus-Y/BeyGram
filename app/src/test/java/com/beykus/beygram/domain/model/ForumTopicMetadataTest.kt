package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumTopicMetadataTest {
    @Test fun announcementsTreatEveryAttentionKindAsUnread() {
        assertFalse(ForumTopic(2L, "Read").hasUnread)
        assertTrue(ForumTopic(3L, "Message", unreadCount = 1).hasUnread)
        assertTrue(ForumTopic(4L, "Mention", unreadMentionCount = 1).hasUnread)
        assertTrue(ForumTopic(5L, "Reaction", unreadReactionCount = 1).hasUnread)
        assertTrue(ForumTopic(6L, "Poll", unreadPollVoteCount = 1).hasUnread)
    }

    @Test fun aTopicInheritsItsParentMuteUntilItOverridesIt() {
        assertTrue(ForumTopic(2L, "Inherited", useDefaultMuteFor = true).isMutedIn(parentChatMuted = true))
        assertFalse(ForumTopic(3L, "Inherited loud", useDefaultMuteFor = true).isMutedIn(parentChatMuted = false))
        assertTrue(ForumTopic(4L, "Own mute", useDefaultMuteFor = false, muteFor = 3600).isMutedIn(parentChatMuted = false))
        assertFalse(ForumTopic(5L, "Own loud", useDefaultMuteFor = false, muteFor = 0).isMutedIn(parentChatMuted = true))
    }

    @Test fun aPartialPageNeverReportsFewerTopicsThanItAlreadyHas() {
        val page = ForumTopicsPage(
            topics = listOf(ForumTopic(2L, "Alpha"), ForumTopic(3L, "Beta")),
            totalCount = 0,
            isComplete = false
        )
        assertEquals(2, page.loadedCount)
        assertEquals(2, page.effectiveTotalCount)
    }

    @Test fun aPageKeepsTheServerTotalAboveTheLoadedSize() {
        val page = ForumTopicsPage(
            topics = listOf(ForumTopic(2L, "Alpha")),
            totalCount = 120,
            isComplete = true
        )
        assertEquals(120, page.effectiveTotalCount)
        assertEquals(UiText.Raw(""), ForumTopicLastMessage().preview)
    }
}
