package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ForumTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumAllMessagesTest {
    private val generalId = 1L

    private fun message(id: Long, topicId: Long, senderId: Long = 0L, senderName: String = "") = ChatMessage(
        id = id, chatId = 7L, text = "m$id", time = "", isOutgoing = false, dateSeconds = id,
        forumTopicId = topicId, senderId = senderId, senderName = senderName
    )

    private fun row(message: ChatMessage) = ChatMessageListItem("message:${message.id}", listOf(message))

    /** The timeline is newest first, as the feed's list reads it. */
    private fun newestFirst(vararg oldestFirst: ChatMessageListItem): List<ChatMessageListItem> = oldestFirst.reversed()

    private fun topicOfMessage(message: ChatMessage): Long = forumFeedTopicId(message, generalId)

    private fun topic(id: Long, general: Boolean = false, canWrite: Boolean = true) =
        ForumTopic(id = id, title = "Topic $id", isGeneral = general, canWrite = canWrite)

    private fun forumChat() = ChatPreview(
        id = 7L, title = "Forum", initials = "F", lastMessage = UiText.Raw(""), lastMessageTime = "",
        unreadCount = 0, avatarColorArgb = 0L, isGroup = true
    )

    @Test fun messageWithoutTopicIdBelongsToGeneral() {
        assertEquals(generalId, forumFeedTopicId(message(1L, topicId = 0L), generalId))
    }

    @Test fun messageKeepsItsOwnTopic() {
        assertEquals(5L, forumFeedTopicId(message(1L, topicId = 5L), generalId))
    }

    @Test fun topicLessMessageStaysZeroWithoutAGeneralTopic() {
        assertEquals(0L, forumFeedTopicId(message(1L, topicId = 0L), generalTopicId = 0L))
    }

    @Test fun replyGoesToTheTopicOfTheRepliedMessage() {
        val target = forumFeedSendTarget(replyToMessageId = 42L, replyTopicId = 5L, generalTopic = topic(generalId, general = true))
        assertEquals(ForumFeedSendTarget.Reply(5L), target)
    }

    @Test fun replyStaysAReplyWhenItsTopicIsNotLoaded() {
        assertEquals(ForumFeedSendTarget.Reply(0L), forumFeedSendTarget(replyToMessageId = 42L, replyTopicId = 0L, generalTopic = null))
    }

    @Test fun replyIsSentEvenWhenGeneralCannotTakeMessages() {
        val closedGeneral = topic(generalId, general = true, canWrite = false)
        assertEquals(ForumFeedSendTarget.Reply(5L), forumFeedSendTarget(replyToMessageId = 42L, replyTopicId = 5L, generalTopic = closedGeneral))
    }

    @Test fun messageWithoutReplyGoesToWritableGeneral() {
        assertEquals(ForumFeedSendTarget.General(generalId), forumFeedSendTarget(null, 0L, topic(generalId, general = true)))
    }

    @Test fun zeroReplyIdCountsAsNoReply() {
        assertEquals(ForumFeedSendTarget.General(generalId), forumFeedSendTarget(0L, 0L, topic(generalId, general = true)))
    }

    @Test fun closedGeneralLeavesTheComposerDisabledWithoutAReply() {
        val closedGeneral = topic(generalId, general = true, canWrite = false)
        assertEquals(ForumFeedSendTarget.Unavailable, forumFeedSendTarget(null, 0L, closedGeneral))
    }

    @Test fun hiddenGeneralLeavesTheComposerDisabledWithoutAReply() {
        assertEquals(ForumFeedSendTarget.Unavailable, forumFeedSendTarget(null, 0L, null))
    }

    @Test fun headerOpensAtEachRunOfOneTopic() {
        val items = newestFirst(
            row(message(1L, 5L)), row(message(2L, 5L)), row(message(3L, 6L)), row(message(4L, 6L)), row(message(5L, 5L))
        )
        val headers = forumFeedTopicHeaders(items) { topicOfMessage(it) }
        assertEquals(mapOf("message:1" to 5L, "message:3" to 6L, "message:5" to 5L), headers)
    }

    @Test fun unreadSeparatorStartsANewRunOfTheSameTopic() {
        val separator = ChatMessageListItem("unread", emptyList(), unreadSeparator = true)
        val items = listOf(row(message(2L, 5L)), separator, row(message(1L, 5L)))
        val headers = forumFeedTopicHeaders(items) { topicOfMessage(it) }
        assertEquals(mapOf("message:1" to 5L, "message:2" to 5L), headers)
    }

    @Test fun topicLessMessagesJoinTheGeneralRun() {
        val items = newestFirst(row(message(1L, 0L)), row(message(2L, generalId)))
        val headers = forumFeedTopicHeaders(items) { topicOfMessage(it) }
        assertEquals(mapOf("message:1" to generalId), headers)
    }

    @Test fun authorIsNamedAgainAfterATopicLabel() {
        val first = message(1L, 5L, senderId = 9L, senderName = "Ann")
        val second = message(2L, 6L, senderId = 9L, senderName = "Ann")
        val items = newestFirst(row(first), row(second))
        val plain = senderHeaderVisibility(forumChat(), items)
        val inFeed = senderHeaderVisibility(forumChat(), items, topicOf = { topicOfMessage(it) })
        assertFalse(plain.getValue("message:2"))
        assertTrue(inFeed.getValue("message:2"))
    }
}
