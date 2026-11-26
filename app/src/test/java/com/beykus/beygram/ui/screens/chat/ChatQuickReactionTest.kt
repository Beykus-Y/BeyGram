package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class ChatQuickReactionTest {
    @get:Rule val compose = createComposeRule()
    private val reactions = mutableListOf<Pair<Long, ReactionOption>>()
    private val allowed = ReactionOption("👍")

    @Test fun doubleTapOnTheFreeSideReactsToTheCorrectRowInEitherDirection() {
        render()
        val incomingSpace = blankPoint(1, outgoing = false)
        val outgoingSpace = blankPoint(2, outgoing = true)
        compose.onNodeWithTag("rows").performTouchInput { doubleClick(incomingSpace) }
        compose.onNodeWithTag("rows").performTouchInput { doubleClick(outgoingSpace) }
        compose.runOnIdle { assertEquals(listOf(1L to allowed, 2L to allowed), reactions) }
    }

    @Test fun theBubbleAndTheRowDispatchOnlyOneReactionForOneDoubleTap() {
        render()
        val origin = compose.onNodeWithTag("rows").fetchSemanticsNode().boundsInRoot.topLeft
        val bubble = compose.onNodeWithTag("message-bubble:1").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("rows").performTouchInput { doubleClick(bubble.center - origin) }
        compose.runOnIdle { assertEquals(listOf(1L to allowed), reactions) }
    }

    @Test fun tapsOnDifferentRowsDoNotFormADoubleTap() {
        render()
        val first = blankPoint(1, outgoing = false)
        val second = blankPoint(2, outgoing = true)
        compose.onNodeWithTag("rows").performTouchInput {
            click(first)
            advanceEventTime(80)
            click(second)
        }
        compose.runOnIdle { assertEquals(emptyList<Pair<Long, ReactionOption>>(), reactions) }
    }

    @Test fun multiselectDisablesTheFreeSideGesture() {
        render(selectionMode = true)
        val blank = blankPoint(1, outgoing = false)
        compose.onNodeWithTag("rows").performTouchInput { doubleClick(blank) }
        compose.runOnIdle { assertEquals(emptyList<Pair<Long, ReactionOption>>(), reactions) }
    }

    @Test fun theDefaultIsOnlyUsedIfAllowedAndPaidReactionsAreExcluded() {
        val default = ReactionOption("❤️")
        val paid = ReactionOption("", isPaid = true)
        assertEquals(default, preferredQuickReaction(listOf(allowed, default)))
        assertEquals(allowed, preferredQuickReaction(listOf(paid, allowed)))
        assertNull(preferredQuickReaction(listOf(paid)))
    }

    private fun render(selectionMode: Boolean = false) {
        val file = MutableStateFlow(MediaFileState(id = 1))
        val emoji = MutableStateFlow<CustomEmoji?>(null)
        compose.setContent {
            BeyGramTheme {
                Column(Modifier.fillMaxSize().testTag("rows"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(false, true).forEachIndexed { index, outgoing ->
                        MessageBubble(
                            message = ChatMessage(index + 1L, 10L, "Text", "18:41", isOutgoing = outgoing),
                            observeFile = { file },
                            onDownloadFile = {},
                            onOpen = {},
                            onReaction = { id, option -> reactions += id to option },
                            observeCustomEmoji = { emoji },
                            loadQuickReactions = { _, _ -> Result.success(listOf(allowed)) },
                            quickReactionAcrossRow = true,
                            selectionMode = selectionMode
                        )
                    }
                }
            }
        }
    }

    private fun blankPoint(id: Long, outgoing: Boolean): Offset {
        val root = compose.onNodeWithTag("rows").fetchSemanticsNode().boundsInRoot
        val bubble = compose.onNodeWithTag("message-bubble:$id").fetchSemanticsNode().boundsInRoot
        return Offset(if (outgoing) 30f else root.width - 30f, bubble.center.y - root.top)
    }
}
