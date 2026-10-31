package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageReaction
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageReactionBubblePlacementTest {
    @get:Rule val rule = createComposeRule()

    private val files = MutableStateFlow(MediaFileState(0))
    private val custom = MutableStateFlow<CustomEmoji?>(null)

    @Test fun reactionTapStaysInsideBubbleAndLongPressOpensActions() {
        val msg = ChatMessage(
            id = 101L, chatId = 42L, text = "Hello", time = "12:30",
            isOutgoing = false, reactions = listOf(MessageReaction("🔥", count = 2))
        )
        val toggled = mutableListOf<ReactionOption>()
        var opened = 0
        rule.setContent {
            BeyGramTheme {
                MessageBubble(
                    message = msg,
                    observeFile = { files },
                    onDownloadFile = {},
                    onOpen = {},
                    onActions = { opened++ },
                    onReaction = { _, reaction -> toggled += reaction },
                    observeCustomEmoji = { custom }
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val chip = rule.onNodeWithContentDescription(
            context.getString(R.string.chat_reaction_description, "🔥", 2)
        )
        val bubble = rule.onNodeWithTag("message-bubble:101").getUnclippedBoundsInRoot()
        val chipBounds = chip.getUnclippedBoundsInRoot()
        assertTrue(chipBounds.top >= bubble.top)
        assertTrue(chipBounds.bottom <= bubble.bottom)
        chip.performTouchInput { down(center); up() }
        rule.runOnIdle { assertEquals(listOf(ReactionOption("🔥")), toggled) }
        rule.onNodeWithTag("message-bubble:101").performTouchInput { longClick() }
        rule.runOnIdle { assertEquals(1, opened) }
    }
}
