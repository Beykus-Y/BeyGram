package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The actual chat-bubble pipeline must not place time over the message's glyphs. */
@RunWith(AndroidJUnit4::class)
class MessageBubbleTimestampGeometryTest {
    @get:Rule val composeRule = createComposeRule()

    private val media = MutableStateFlow(MediaFileState(id = 42))
    private val emoji = MutableStateFlow<CustomEmoji?>(null)

    private fun render(content: String) {
        composeRule.setContent {
            BeyGramTheme {
                MessageBubble(
                    message = ChatMessage(
                        id = 101L, chatId = 1L, text = content, time = "23:02",
                        isOutgoing = true
                    ),
                    observeFile = { media },
                    onDownloadFile = {},
                    onOpen = {},
                    onReaction = { _, _ -> },
                    observeCustomEmoji = { emoji }
                )
            }
        }
    }

    @Test fun shortOutgoingMessageKeepsTimeOutsideTextBounds() {
        render("почему")
        val text = composeRule.onNodeWithText("почему", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val time = composeRule.onNodeWithText("23:02", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Time cannot overlap the last word", time.left >= text.right - 1f || time.top >= text.bottom - 1f)
    }

    @Test fun longOutgoingMessagePlacesTimeBelowText() {
        val content = "покажи свой взгляд в кружочек и расскажи, что ты думаешь об этом"
        render(content)
        val text = composeRule.onNodeWithText(content, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val time = composeRule.onNodeWithText("23:02", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Long text must have its own metadata row", time.top >= text.bottom - 1f)
    }
}
