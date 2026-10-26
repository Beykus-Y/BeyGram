package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.MediaFileStore
import com.beykus.beygram.data.tdlib.TdMediaMapper
import com.beykus.beygram.data.tdlib.expiredMediaKindOf
import com.beykus.beygram.data.tdlib.withUpdatedContent
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.MessageSelfDestruct
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A TDLib MessageExpired* state shows exactly one expired card, never the unsupported fallback. */
@RunWith(AndroidJUnit4::class)
class ExpiredMediaBubbleTest {
    @get:Rule val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mapper = TdMediaMapper(MediaFileStore())
    private val fileState = MutableStateFlow(MediaFileState(id = 10))
    private val noEmoji = MutableStateFlow<CustomEmoji?>(null)

    private fun expiredMessage(id: Long, content: TdApi.MessageContent, outgoing: Boolean = false) = ChatMessage(
        id = id,
        chatId = 2L,
        text = "",
        time = "12:00",
        isOutgoing = outgoing,
        media = mapper.media(content),
        special = mapper.special(content),
        expiredMediaKind = expiredMediaKindOf(content)
    )

    private fun show(message: ChatMessage, onOpen: () -> Unit = {}) {
        composeRule.setContent {
            BeyGramTheme {
                MessageBubble(
                    message = message,
                    observeFile = { fileState },
                    onDownloadFile = {},
                    onOpen = onOpen,
                    onReaction = { _, _ -> },
                    observeCustomEmoji = { noEmoji }
                )
            }
        }
    }

    @Test
    fun expiredPhotoShowsOnlyItsCardAndCannotBeOpenedAgain() {
        var opened = 0
        show(expiredMessage(id = 1L, content = TdApi.MessageExpiredPhoto()), onOpen = { opened++ })

        composeRule.onNodeWithText(context.getString(R.string.media_expired_photo)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.chat_unsupported_message)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.chat_unsupported_hint)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.media_kind_photo)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.a11y_media_download, context.getString(R.string.media_kind_photo)))
            .assertDoesNotExist()

        composeRule.onNodeWithText(context.getString(R.string.media_expired_photo)).performClick()
        composeRule.runOnIdle { assertEquals(0, opened) }
    }

    @Test
    fun viewOnceVideoBecomesOneExpiredCardAfterTdlibUpdate() {
        var message by mutableStateOf(
            ChatMessage(
                id = 3L,
                chatId = 2L,
                text = "",
                time = "12:00",
                isOutgoing = true,
                media = MessageMedia(MediaKind.Video, fileId = 10, mimeType = "video/mp4", isSecret = true),
                selfDestruct = MessageSelfDestruct.Immediately
            )
        )
        composeRule.setContent {
            BeyGramTheme {
                MessageBubble(
                    message = message,
                    observeFile = { fileState },
                    onDownloadFile = {},
                    onOpen = {},
                    onReaction = { _, _ -> },
                    observeCustomEmoji = { noEmoji }
                )
            }
        }
        val viewOnceHint = context.getString(R.string.media_view_once_hint, context.getString(R.string.media_kind_video))
        composeRule.onNodeWithText(viewOnceHint).assertExists()

        val expired = TdApi.MessageExpiredVideo()
        val updated = message.withUpdatedContent(
            content = expired,
            media = mapper.media(expired),
            special = mapper.special(expired),
            text = "",
            entities = emptyList(),
            serviceText = null
        )
        composeRule.runOnIdle { message = updated }

        composeRule.onNodeWithText(viewOnceHint).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.media_expired_video)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.chat_unsupported_message)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.chat_unsupported_hint)).assertDoesNotExist()
    }

    @Test
    fun trulyUnknownContentStillUsesTheUnsupportedFallback() {
        val content = TdApi.MessageUnsupported()
        show(
            ChatMessage(
                id = 4L,
                chatId = 2L,
                text = "",
                time = "12:00",
                isOutgoing = false,
                special = mapper.special(content)
            )
        )

        composeRule.onNodeWithText(context.getString(R.string.chat_unsupported_message)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.chat_unsupported_hint)).assertExists()
    }
}
