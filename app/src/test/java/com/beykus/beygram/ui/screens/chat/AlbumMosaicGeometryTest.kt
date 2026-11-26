package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class AlbumMosaicGeometryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threePortraitTilesFillTheMosaicAndTimeOverlaysTheLastTile() {
        val media = (1..3).associateWith { MutableStateFlow(MediaFileState(id = it)) }
        val emoji = MutableStateFlow<CustomEmoji?>(null)
        compose.setContent {
            BeyGramTheme {
                MessageAlbumBubble(
                    messages = (1..3).map { index ->
                        ChatMessage(
                            id = index.toLong(), chatId = 10L, text = "", time = "18:4$index",
                            isOutgoing = true, dateSeconds = index.toLong(), mediaAlbumId = 42L,
                            media = MessageMedia(MediaKind.Photo, index, width = 900, height = 1600)
                        )
                    },
                    observeFile = { media.getValue(it) },
                    onDownloadFile = {},
                    isRead = true,
                    highlighted = false,
                    onOpen = {},
                    onActions = {},
                    onReplyClick = {},
                    onReaction = { _, _ -> },
                    observeCustomEmoji = { emoji },
                    onAutoDownloadFile = {}
                )
            }
        }

        val photo = RuntimeEnvironment.getApplication().getString(R.string.media_kind_photo)
        val nodes = compose.onAllNodesWithContentDescription(photo, useUnmergedTree = true)
        nodes.assertCountEquals(3)
        val (left, top, bottom) = nodes.fetchSemanticsNodes().map { it.boundsInRoot }
        val time = compose.onNodeWithText("18:43", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(left.right < top.left && left.right < bottom.left)
        assertTrue(top.bottom < bottom.top)
        assertTrue(time.left >= bottom.left && time.right <= bottom.right)
        assertTrue(time.top >= bottom.top && time.bottom <= bottom.bottom)
    }
}
