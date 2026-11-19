package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ForumTopic
import org.junit.Assert.assertEquals
import org.junit.Test

class ForumTopicPanelsTest {
    private fun topic(id: Long) = ForumTopic(id = id, title = "Topic $id")

    @Test fun opensTheLastTopicWhenItStillExists() {
        val topics = listOf(topic(1L), topic(2L), topic(3L))

        assertEquals(2L, initialPanelTopicId(topics, lastTopicId = 2L))
    }

    @Test fun fallsBackToTheFirstTopicWhenTheLastOneIsGone() {
        val topics = listOf(topic(1L), topic(3L))

        assertEquals(1L, initialPanelTopicId(topics, lastTopicId = 9L))
    }

    @Test fun fallsBackToTheFirstTopicWhenNoneWasRemembered() {
        val topics = listOf(topic(5L), topic(6L))

        assertEquals(5L, initialPanelTopicId(topics, lastTopicId = 0L))
    }

    @Test fun returnsZeroForAForumWithoutTopics() {
        assertEquals(0L, initialPanelTopicId(emptyList(), lastTopicId = 4L))
    }
}
