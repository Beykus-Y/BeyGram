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
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.ui.theme.BeyGramTheme
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
            ForumTopic(id = 3L, title = "Beta topic", lastMessagePreview = "Beta preview")
        )
        composeRule.setContent {
            BeyGramTheme {
                ForumTopicsScreen(
                    chatId = 1L, loadPreview = { Result.success(null) },
                    chatTitle = "Test forum", topics = topics, canCreate = false,
                    busy = false, actionError = null,
                    onBack = {}, onTopic = { opened += it.id },
                    onCreate = {}, onRename = { _, _ -> },
                    onClosed = { _, _ -> }, onPinned = { _, _ -> },
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
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(R.string.forum_topic_regular)
            ))
            .performClick()
        composeRule.runOnIdle { assertEquals(listOf(2L, 3L), opened) }
    }
}
