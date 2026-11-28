package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.MessageLineSpacing
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A short text message keeps one text row and one line of time beside it. The reported #1247 symptom is a bubble that
 * is tall with the text at the top and the time at the bottom, so this walks the whole appearance axis the settings
 * offer (#1363, investigation): every text size and every line spacing must still leave the time on the final line and
 * must not add an empty metadata row.
 *
 * The check is deterministic and synthetic; it does not replace the device reproduction the Issue still needs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class MessageLayoutV2BubbleHeightTest {
    @get:Rule val compose = createComposeRule()

    private val text = "Ахазахахах"
    private val time = "18:41"
    private var textSizeSp by mutableStateOf(16)
    private var spacing by mutableStateOf(MessageLineSpacing.Normal)

    private fun renderOnce() {
        val file = MutableStateFlow(MediaFileState(id = 1))
        val emoji = MutableStateFlow<CustomEmoji?>(null)
        compose.setContent {
            BeyGramTheme(messageTextSizeSp = textSizeSp, messageLineSpacing = spacing) {
                Box(Modifier.width(360.dp)) {
                    MessageBubble(
                        message = ChatMessage(1, 1, text, time, isOutgoing = true),
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

    private fun checkShortBubble(case: String) {
        compose.waitForIdle()
        val layouts = mutableListOf<TextLayoutResult>()
        val textNode = compose.onNodeWithText(text, useUnmergedTree = true)
        textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val textBounds = textNode.fetchSemanticsNode().boundsInRoot
        val timeBounds = compose.onNodeWithText(time, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bubbleBounds = compose.onNodeWithTag("message-bubble:1").fetchSemanticsNode().boundsInRoot

        assert(layout.lineCount == 1) { "$case: the short message must stay on one line" }
        // The text and the time must together fill the bubble: the reported #1247 symptom is a band of empty space
        // between a text pinned to the top and a time pinned to the bottom. Whether the time shares the final line or
        // takes the next one depends on the text size, and both are legitimate; a gap is not.
        val contentTop = minOf(textBounds.top, timeBounds.top)
        val contentBottom = maxOf(textBounds.bottom, timeBounds.bottom)
        // The insets above the text and below the time: 8dp top and 6dp bottom, with room for glyph overhang.
        val slack = with(compose.density) { 20.dp.roundToPx() }
        val uncovered = bubbleBounds.height - (contentBottom - contentTop)
        assert(uncovered <= slack) {
            "$case: ${uncovered}px of the bubble is neither text nor time (line height ${layout.size.height}px, " +
                "text ${textBounds.height}px, time ${timeBounds.height}px), which means an empty band is reserved"
        }
    }

    @Test
    fun everyTextSizeKeepsAShortBubbleToItsLastLine() {
        renderOnce()
        for (sizeSp in listOf(12, 14, 16, 18, 20, 22)) {
            compose.runOnIdle { textSizeSp = sizeSp }
            checkShortBubble("text $sizeSp sp / ${spacing.name}")
        }
    }

    @Test
    fun everyLineSpacingKeepsAShortBubbleToItsLastLine() {
        renderOnce()
        for (candidate in MessageLineSpacing.entries) {
            for (sizeSp in listOf(16, 22)) {
                compose.runOnIdle {
                    spacing = candidate
                    textSizeSp = sizeSp
                }
                checkShortBubble("text $sizeSp sp / ${candidate.name}")
            }
        }
    }
}