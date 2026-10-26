package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageReaction
import com.beykus.beygram.domain.model.PAID_REACTION_EMOJI
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageReactionsTest {
    @get:Rule val composeRule = createComposeRule()

    private val file = MutableStateFlow(MediaFileState(0))
    private val unavailableCustomEmoji = MutableStateFlow<CustomEmoji?>(null)

    private fun show(reactions: List<MessageReaction>, onToggle: (ReactionOption) -> Unit) {
        composeRule.setContent {
            BeyGramTheme {
                MessageReactionChips(
                    reactions = reactions,
                    observeCustomEmoji = { unavailableCustomEmoji },
                    observeFile = { file },
                    downloadFile = {},
                    onToggle = onToggle
                )
            }
        }
    }

    @Test fun customEmojiWithoutAnAssetKeepsAVisibleFallbackAndItsCount() {
        show(listOf(MessageReaction("", 3, customEmojiId = 42L)), onToggle = {})

        composeRule.onNodeWithText("🙂").assertExists()
        composeRule.onNodeWithText("3").assertExists()
    }

    @Test fun paidReactionIsDisplayOnlyAndDoesNotSpendStars() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val toggled = mutableListOf<ReactionOption>()
        show(listOf(MessageReaction(PAID_REACTION_EMOJI, 5, isPaid = true)), onToggle = { toggled += it })

        val chip = composeRule.onNodeWithContentDescription(context.getString(R.string.chat_reaction_paid_description, 5))
        chip.assertExists()
        chip.performTouchInput { down(center); up() }
        composeRule.runOnIdle { assertTrue(toggled.isEmpty()) }
    }
}
