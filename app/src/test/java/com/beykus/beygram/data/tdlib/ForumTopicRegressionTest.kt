package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ForumTopic
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class ForumTopicRegressionTest {
    @Test fun duplicateForumTopicsAcrossOverlappingPagesHaveUniqueKeys() {
        val topics = listOf(
            ForumTopic(id = 3586, title = "Болталка", order = 300),
            ForumTopic(id = 20, title = "Фото", order = 200),
            ForumTopic(id = 3586, title = "Болталка", order = 300),
            ForumTopic(id = 10, title = "Задания", order = 100)
        )
        val unique = uniqueForumTopics(topics)
        assertEquals(listOf(3586L, 20L, 10L), unique.map { it.id })
        assertEquals(unique.size, unique.map { it.id }.toSet().size)
    }

    @Test fun joiningByLinkOpensActualJoiningUserNotInviter() {
        val service = TdApi.MessageChatJoinByLink()
        val sender = TdApi.MessageSenderUser(456L)
        assertEquals(456L, serviceAffectedUserId(service, sender))
    }

    @Test fun memberAddedResolvesOnlyUnambiguousTarget() {
        val single = TdApi.MessageChatAddMembers().apply { memberUserIds = longArrayOf(789L) }
        val several = TdApi.MessageChatAddMembers().apply { memberUserIds = longArrayOf(789L, 791L) }
        assertEquals(789L, serviceAffectedUserId(single, TdApi.MessageSenderUser(1L)))
        assertEquals(0L, serviceAffectedUserId(several, TdApi.MessageSenderUser(1L)))
    }
}
