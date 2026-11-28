package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-xhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageContextGestureTest {
    @get:Rule val compose = createComposeRule()

    private var quote: SelectedMessageQuote? = null
    private var reaction: ReactionOption? = null
    private val quickReaction = ReactionOption("👍")
    private val editor get() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetSelection), useUnmergedTree = true)

    @Test fun tapsStayInSceneAndLongPressQuotesTheExactWord() {
        render()
        open()
        editor.performTouchInput { click() }
        editor.assertIsDisplayed()
        compose.onNode(hasTestTag("message-bubble:1") and hasAnyAncestor(isPopup()), useUnmergedTree = true)
            .performTouchInput { click(Offset(3f, center.y)) }
        editor.assertIsDisplayed()
        editor.performTouchInput { longClick(Offset(9f, center.y)) }
        compose.onNodeWithText("Quote").performTouchInput { click() }
        compose.runOnIdle { assertEquals(SelectedMessageQuote("Repeat", 0), quote) }
        editor.assertDoesNotExist()
    }

    @Test fun outsideTapClosesAndQuickReactionStillDispatchesAfterReopening() {
        render()
        open()
        val textOrigin = editor.fetchSemanticsNode().boundsInRoot.topLeft
        editor.performTouchInput { click(Offset(4f, 4f) - textOrigin) }
        editor.assertDoesNotExist()
        open()
        compose.onNodeWithText("👍").performTouchInput { click() }
        compose.runOnIdle { assertEquals(quickReaction, reaction) }
        editor.assertDoesNotExist()
    }

    @Test fun aLongMessageStillExposesSelectableText() {
        render(tallAnchor = true)
        open()
        editor.assertIsDisplayed()
        editor.performTouchInput { longClick(Offset(9f, center.y)) }
        compose.onNodeWithText("Quote").assertIsDisplayed()
    }

    @Test fun aMessageWithoutCopyPermissionDoesNotExposeQuoteOrSelection() {
        render(canCopy = false)
        open()
        editor.assertDoesNotExist()
        compose.onNodeWithText("Quote").assertDoesNotExist()
        compose.onNodeWithText("Reply").assertIsDisplayed()
    }

    private fun open() {
        compose.onNodeWithTag("message-bubble:1").performTouchInput { longClick() }
    }

    private fun render(canCopy: Boolean = true, tallAnchor: Boolean = false) {
        val message = ChatMessage(1, 10, "Repeat repeat repeat", "18:41", isOutgoing = false)
        val file = MutableStateFlow(MediaFileState(id = 1))
        val emoji = MutableStateFlow<CustomEmoji?>(null)
        compose.setContent {
            var open by remember { mutableStateOf(false) }
            var anchor by remember { mutableStateOf<Rect?>(null) }
            BeyGramTheme {
                Box(Modifier.fillMaxSize().padding(top = 180.dp)) {
                    MessageBubble(
                        message = message,
                        observeFile = { file },
                        onDownloadFile = {},
                        onOpen = {},
                        onActions = { open = true },
                        onBubblePositioned = {
                            val bounds = it.boundsInWindow()
                            anchor = if (tallAnchor) bounds.copy(bottom = bounds.top + 2000) else bounds
                        }
                    )
                    if (open) MessageContextPopover(
                        anchorBounds = anchor,
                        foreground = { selection ->
                            MessageBubble(
                                message = message,
                                observeFile = { file },
                                onDownloadFile = {},
                                onOpen = {},
                                contextScenePassive = true,
                                contextTextSelection = selection,
                                paneInset = false
                            )
                        },
                        foregroundText = message.text,
                        onQuoteFragment = { quote = it; open = false },
                        isOutgoing = false,
                        capabilities = MessageCapabilities(canReply = true, canCopy = canCopy),
                        error = null,
                        hasText = true,
                        isMedia = false,
                        canPin = false,
                        isPinned = false,
                        reactionOptions = listOf(quickReaction),
                        chosenReactions = emptySet(),
                        observeCustomEmoji = { emoji },
                        observeFile = { file },
                        onDownloadFile = {},
                        onQuickReaction = { reaction = it; open = false },
                        onClose = { open = false },
                        onRetry = {},
                        onReply = {},
                        onCopy = {},
                        onEdit = {},
                        onDelete = {},
                        onTogglePin = {}
                    )
                }
            }
        }
    }
}
