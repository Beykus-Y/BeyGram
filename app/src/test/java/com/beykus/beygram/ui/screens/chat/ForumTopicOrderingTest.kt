package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ForumTopic
import org.junit.Assert.assertEquals
import org.junit.Test

class ForumTopicOrderingTest {
    @Test fun pinnedTopicsFirstWithoutChangingOrderWithinGroups() {
        val input = listOf(
            ForumTopic(2L, "Regular"),
            ForumTopic(3L, "Pinned A", isPinned = true),
            ForumTopic(4L, "Regular 2"),
            ForumTopic(5L, "Pinned B", isPinned = true)
        )
        assertEquals(listOf(3L, 5L, 2L, 4L), orderedForumTopics(input).map { it.id })
    }

    @Test fun duplicateTopicIdsCannotBreakLazyListKeys() {
        val input = listOf(
            ForumTopic(3L, "Pinned", isPinned = true),
            ForumTopic(3L, "Duplicate"),
            ForumTopic(4L, "Regular")
        )
        assertEquals(listOf(3L, 4L), orderedForumTopics(input).map { it.id })
    }

    @Test fun emptyTopicsStayEmpty() {
        assertEquals(emptyList<ForumTopic>(), orderedForumTopics(emptyList()))
    }
}
