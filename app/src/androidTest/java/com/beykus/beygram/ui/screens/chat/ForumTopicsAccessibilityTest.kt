package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.ForumTopicLastMessage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForumTopicsAccessibilityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun clickableRowsRetainTheirTopicTitlesAndAnnounceStatusAndUnread() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val opened = mutableListOf<Long>()
        val topics = listOf(
            ForumTopic(id = 2L, title = "Alpha topic", lastMessagePreview = "Alpha preview",
                isPinned = true, isClosed = true, unreadCount = 4),
            ForumTopic(id = 3L, title = "Beta topic", lastMessagePreview = "Beta preview"),
            ForumTopic(
                id = 4L,
                title = "Gamma topic",
                unreadMentionCount = 1,
                unreadReactionCount = 2,
                unreadPollVoteCount = 3,
                useDefaultMuteFor = false,
                muteFor = 3600,
                hasDraft = true,
                draftPreview = "Unfinished thought",
                lastMessage = ForumTopicLastMessage(
                    messageId = 10L,
                    senderName = "You",
                    isOutgoing = true,
                    sendState = MessageSendState.Sent
                ),
                lastReadOutboxMessageId = 10L
            )
        )
        val chat = ChatPreview(
            id = 1L,
            title = "Test forum",
            initials = "TF",
            lastMessage = UiText.Raw(""),
            lastMessageTime = "",
            unreadCount = 0,
            avatarColorArgb = 0L
        )
        composeRule.setContent {
            BeyGramTheme {
                ForumTopicsScreen(
                    chatId = 1L,
                    chat = chat,
                    chatInfo = null,
                    topics = topics,
                    totalCount = topics.size,
                    topicsComplete = true,
                    searchTopics = { _, _ -> Result.success(emptyList()) },
                    canCreate = false,
                    busy = false,
                    actionError = null,
                    observeFile = { id -> MutableStateFlow(MediaFileState(id)) },
                    downloadFile = {},
                    observeCustomEmoji = { MutableStateFlow(null) },
                    onBack = {},
                    onOpenGroupInfo = {},
                    onTopic = { opened += it.id },
                    onCreate = { _, done -> done(null) },
                    onRename = { _, _, done -> done(null) },
                    onClosed = { _, _ -> },
                    onPinned = { _, _ -> },
                    onDelete = {}
                )
            }
        }
        val pinned = listOf(
            context.getString(R.string.forum_topic_pinned),
            context.getString(R.string.forum_topic_closed),
            context.getString(R.string.forum_topic_unread_count, 4)
        ).joinToString(", ")
        composeRule.onNodeWithText("Alpha topic")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, pinned))
            .performClick()
        composeRule.onNodeWithText("Beta topic")
            .assertHasClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            .performClick()
        val rich = listOf(
            context.getString(R.string.forum_topic_mentions, 1),
            context.getString(R.string.forum_topic_reactions, 2),
            context.getString(R.string.forum_topic_poll_votes, 3),
            context.getString(R.string.forum_topic_muted),
            context.getString(R.string.forum_topic_draft),
            context.getString(R.string.chat_read_description)
        ).joinToString(", ")
        composeRule.onNodeWithText("Gamma topic")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, rich))
            .performClick()
        composeRule.runOnIdle { assertEquals(listOf(2L, 3L, 4L), opened) }
    }
}
