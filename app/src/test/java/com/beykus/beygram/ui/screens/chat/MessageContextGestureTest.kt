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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
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
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso.pressBack
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.appearance.NavigationPlacement
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-xhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageContextGestureTest(private val style: DesignStyle) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = DesignStyle.entries.map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    private lateinit var quoteLabel: String
    private lateinit var replyLabel: String
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
        compose.onNodeWithText(quoteLabel).performTouchInput { click() }
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

    @Test fun draggingSelectionHandleExtendsTheQuotedRange() {
        render()
        open()
        editor.performTouchInput { longClick(Offset(9f, center.y)) }
        val endHandle = compose.onNode(
            SemanticsMatcher("native end selection handle") { node ->
                node.config.any { (key, value) ->
                    key.name == "SelectionHandleInfo" && value.toString().contains("SelectionEnd")
                }
            },
            useUnmergedTree = true
        )
        val results = mutableListOf<TextLayoutResult>()
        val readLayout = editor.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
        compose.runOnIdle { check(readLayout.action?.invoke(results) == true) }
        val layout = results.single()
        val distance = layout.getHorizontalPosition(13, true) - layout.getHorizontalPosition(6, true)
        endHandle.performTouchInput { swipe(center, center + Offset(distance, 0f)) }
        compose.onNodeWithText(quoteLabel).performTouchInput { click() }
        compose.runOnIdle { assertEquals(SelectedMessageQuote("Repeat repeat", 0), quote) }
    }

    @Test fun backDismissesTheSceneAndItCanOpenAgain() {
        render()
        open()
        editor.assertIsDisplayed()
        pressBack()
        editor.assertDoesNotExist()
        open()
        editor.assertIsDisplayed()
    }

    @Test fun aLongMessageStillExposesSelectableText() {
        render(tallAnchor = true)
        open()
        editor.assertIsDisplayed()
        editor.performTouchInput { longClick(Offset(9f, center.y)) }
        compose.onNodeWithText(quoteLabel).assertIsDisplayed()
    }

    @Test fun aMessageWithoutCopyPermissionDoesNotExposeQuoteOrSelection() {
        render(canCopy = false)
        open()
        editor.assertDoesNotExist()
        compose.onNodeWithText(quoteLabel).assertDoesNotExist()
        compose.onNodeWithText(replyLabel).assertIsDisplayed()
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
            val context = LocalContext.current
            quoteLabel = context.getString(R.string.chat_quote_action)
            replyLabel = context.getString(R.string.message_action_reply)
            val design = remember { designFor(style, GlassQuality.Balanced, NavigationPlacement.Pinned) }
            BeyGramTheme(design = design) {
                Box(Modifier.fillMaxSize().padding(top = 180.dp)) {
                    MessageBubble(
                        message = message,
                        observeFile = { file },
                        onDownloadFile = {},
                        onOpen = {},n                        onReaction = { _, _ -> },n                        observeCustomEmoji = { emoji },
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
                                onOpen = {},n                                onReaction = { _, _ -> },n                                observeCustomEmoji = { emoji },
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
