package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReplySendTopicTest {
    @Test
    fun `reply without topic sends without topic`() {
        assertNull(replySendTopic(null))
    }

    @Test
    fun `saved messages topic is not copied into a reply`() {
        assertNull(replySendTopic(TdApi.MessageTopicSavedMessages(42L)))
    }

    @Test
    fun `forum topic is kept for a reply`() {
        val topic = TdApi.MessageTopicForum(7)

        assertSame(topic, replySendTopic(topic))
    }

    @Test
    fun `thread topic is kept for a reply`() {
        val topic = TdApi.MessageTopicThread(9L)

        assertSame(topic, replySendTopic(topic))
    }

    @Test
    fun `direct messages topic is kept for a reply`() {
        val topic = TdApi.MessageTopicDirectMessages(11L)

        assertSame(topic, replySendTopic(topic))
    }
}
