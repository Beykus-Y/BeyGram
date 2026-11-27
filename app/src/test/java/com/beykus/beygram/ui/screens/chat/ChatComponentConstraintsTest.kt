package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.MessagePoll
import com.beykus.beygram.domain.model.MessagePollOption
import com.beykus.beygram.domain.model.MessageSpecialContent
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
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xhdpi")
class ChatComponentConstraintsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun musicCardFitsNarrowPaneWithoutOverflow() {
        val media = MessageMedia(
            fileId = 1,
            kind = MediaKind.Audio,
            fileName = "Very Long Track Title That Must Ellipsize Cleanly.mp3",
            title = "Long Symphonic Movement Name For Narrow Split View",
            performer = "Orchestra Ensemble With Long Name",
            durationSeconds = 215
        )
        var containerWidth = 0f
        var childRight = 0f
        var containerRight = 0f

        rule.setContent {
            BeyGramTheme {
                Box(
                    Modifier
                        .width(220.dp)
                        .testTag("narrow_container")
                        .onGloballyPositioned {
                            containerWidth = it.size.width.toFloat()
                            containerRight = it.boundsInRoot().right
                        }
                ) {
                    Box(Modifier.onGloballyPositioned { childRight = it.boundsInRoot().right }) {
                        MediaPreview(
                            media = media,
                            observeFile = { MutableStateFlow(MediaFileState(it, size = 4_200_000L)) },
                            downloadFile = {},
                            onOpen = {}
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        assertTrue("Container should have positive width", containerWidth > 0f)
        assertTrue("Music card right ($childRight) must not overflow container ($containerRight)", childRight <= containerRight + 1f)
        rule.onNodeWithText("Long Symphonic Movement Name For Narrow Split View", substring = true).assertExists()
    }

    @Test
    fun documentRowFitsNarrowRtlLargeFontPane() {
        val media = MessageMedia(
            fileId = 2,
            kind = MediaKind.Document,
            fileName = "Quarterly_Financial_Report_2026_Final_Reviewed.pdf",
            mimeType = "application/pdf",
            durationSeconds = 0
        )
        var childRight = 0f
        var containerRight = 0f

        rule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f)
            ) {
                BeyGramTheme {
                    Box(
                        Modifier
                            .width(220.dp)
                            .onGloballyPositioned { containerRight = it.boundsInRoot().right }
                    ) {
                        Box(Modifier.onGloballyPositioned { childRight = it.boundsInRoot().right }) {
                            MediaPreview(
                                media = media,
                                observeFile = { MutableStateFlow(MediaFileState(it, size = 1_500_000L)) },
                                downloadFile = {},
                                onOpen = {}
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        assertTrue("Document row right ($childRight) must not overflow container ($containerRight)", childRight <= containerRight + 1f)
        rule.onNodeWithText("Quarterly_Financial_Report_2026_Final_Reviewed.pdf").assertExists()
    }

    @Test
    fun voiceBubbleFitsNarrowRtlLargeFontPane() {
        val media = MessageMedia(
            fileId = 3,
            kind = MediaKind.Voice,
            durationSeconds = 42,
            waveform = listOf(10, 40, 80, 120, 60, 30, 90, 50)
        )
        var childRight = 0f
        var containerRight = 0f

        rule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f)
            ) {
                BeyGramTheme {
                    Box(
                        Modifier
                            .width(220.dp)
                            .onGloballyPositioned { containerRight = it.boundsInRoot().right }
                    ) {
                        Box(Modifier.onGloballyPositioned { childRight = it.boundsInRoot().right }) {
                            VoiceBubble(
                                media = media,
                                file = MediaFileState(3, size = 64_000L),
                                downloadFile = {},
                                onPlayed = {},
                                onLongPress = null
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        assertTrue("Voice bubble right ($childRight) must not overflow container ($containerRight)", childRight <= containerRight + 1f)
        rule.onNodeWithText("0:42", substring = true).assertExists()
    }

    @Test
    fun pollCardFitsNarrowRtlLargeFontPane() {
        val poll = MessagePoll(
            id = 100L,
            question = "Which layout constraint verification passes reliably on narrow split screens?",
            options = listOf(
                MessagePollOption(id = "0", optionIndex = 0, text = "Bounded widthIn(max = ...) with fillMaxWidth()", voterCount = 12, percentage = 80, isChosen = true),
                MessagePollOption(id = "1", optionIndex = 1, text = "Hardcoded fixed dp width that clips on split view", voterCount = 3, percentage = 20)
            ),
            totalVoterCount = 15,
            isClosed = false
        )
        val message = ChatMessage(
            id = 500L,
            chatId = 1L,
            text = "",
            time = "12:00",
            isOutgoing = false,
            special = MessageSpecialContent.Poll(poll)
        )
        var childRight = 0f
        var containerRight = 0f

        rule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f)
            ) {
                BeyGramTheme {
                    Box(
                        Modifier
                            .width(220.dp)
                            .onGloballyPositioned { containerRight = it.boundsInRoot().right }
                    ) {
                        Box(Modifier.onGloballyPositioned { childRight = it.boundsInRoot().right }) {
                            SpecialMessageContentView(
                                special = MessageSpecialContent.Poll(poll),
                                message = message
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        assertTrue("Poll card right ($childRight) must not overflow container ($containerRight)", childRight <= containerRight + 1f)
        rule.onNodeWithText("Which layout constraint verification passes reliably on narrow split screens?").assertExists()
        rule.onNodeWithText("80%").assertExists()
    }
}
