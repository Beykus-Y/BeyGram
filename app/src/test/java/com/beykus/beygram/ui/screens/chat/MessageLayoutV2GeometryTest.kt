package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class MessageLayoutV2GeometryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shortOutgoingTextAndTimeShareALine() {
        val text = "Ахазахахах"
        render(text)
        assertTimeBesideFinalLine(text, LayoutDirection.Ltr)
    }

    @Test fun aSenderHeaderDoesNotForceTheTimeOntoItsOwnRow() {
        val text = "Ну блин"
        render(text, sender = "Анна")
        assertTimeBesideFinalLine(text, LayoutDirection.Ltr)
    }

    @Test fun shortFinalLineInWrappedTextLeavesRoomForTime() {
        val text = "Длинная первая строка с несколькими словами\nКонец"
        render(text)
        assertTimeBesideFinalLine(text, LayoutDirection.Ltr)
    }

    @Test fun rightToLeftTextKeepsTimeAtTheLogicalEnd() {
        val text = "مرحبا"
        render(text, direction = LayoutDirection.Rtl)
        assertTimeBesideFinalLine(text, LayoutDirection.Rtl)
    }

    @Test fun aFullLineKeepsTheTimeBelowTheTextAndAtTheTrailingEdge() {
        val text = "WWWWWWWW"
        val time = "23:59:59"
        render(text, time = time, width = 190.dp)

        val textBounds = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val timeBounds = compose.onNodeWithText(time, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("The time must start below the full text line", timeBounds.top >= textBounds.bottom)
        val bubbleBounds = compose.onNodeWithTag("message-bubble:1").fetchSemanticsNode().boundsInRoot
        assertTrue("The time must stay in the trailing half", timeBounds.center.x > bubbleBounds.center.x)
    }

    private fun render(
        text: String,
        sender: String = "",
        direction: LayoutDirection = LayoutDirection.Ltr,
        time: String = "18:41",
        width: Dp = 360.dp
    ) {
        val file = MutableStateFlow(MediaFileState(id = 1))
        val emoji = MutableStateFlow<CustomEmoji?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                BeyGramTheme {
                    Box(Modifier.width(width)) {
                        MessageBubble(
                            message = ChatMessage(1, 1, text, time, isOutgoing = sender.isEmpty(), senderName = sender),
                            observeFile = { file },
                            onDownloadFile = {},
                            onOpen = {},
                            onReaction = { _, _ -> },
                            observeCustomEmoji = { emoji }
                        )
                    }
                }
            }
        }
    }

    private fun assertTimeBesideFinalLine(text: String, direction: LayoutDirection) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val line = layout.lineCount - 1
        val textBounds = node.fetchSemanticsNode().boundsInRoot
        val timeBounds = compose.onNodeWithText("18:41", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Time should intersect the vertical span of the final line", timeBounds.top < textBounds.bottom)
        assertTrue("Time should reach the final line", timeBounds.bottom > textBounds.top + layout.getLineTop(line))
        if (direction == LayoutDirection.Ltr) {
            assertTrue("Time should clear the text on the right", timeBounds.left >= textBounds.left + layout.getLineRight(line))
        } else {
            assertTrue("Time should clear the text on the left", timeBounds.right <= textBounds.left + layout.getLineLeft(line))
        }
    }
}
