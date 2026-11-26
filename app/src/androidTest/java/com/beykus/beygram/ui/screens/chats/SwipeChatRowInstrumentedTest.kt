package com.beykus.beygram.ui.screens.chats

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verify that tapping a revealed action closes it before navigating in either direction. */
@RunWith(AndroidJUnit4::class)
class SwipeChatRowInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun rightSwipeClosesOnFirstTapAndOpensChatOnSecond() = assertTapClosesSwipe(direction = 1)

    @Test fun leftSwipeClosesOnFirstTapAndOpensChatOnSecond() = assertTapClosesSwipe(direction = -1)

    private fun assertTapClosesSwipe(direction: Int) {
        val chat = ChatPreview(
            id = 42L,
            title = "QA Chat",
            unreadCount = 0,
            initials = "QA",
            lastMessage = UiText.Raw("hello"),
            lastMessageTime = "12:00",
            avatarColorArgb = 0xFF5599CCL
        )
        val file = MutableStateFlow(MediaFileState(id = 0))
        var opened by mutableStateOf<Long?>(null)
        var navigations = 0
        var performed = 0

        composeRule.setContent {
            BeyGramTheme {
                val actionScope = rememberCoroutineScope()
                val action = ChatSwipeAction(
                    label = "Archive",
                    icon = Icons.Rounded.Archive,
                    container = Color.Gray,
                    content = Color.White,
                    enabled = true,
                    kind = ChatSwipeActionKind.Archive,
                    perform = { performed++; true }
                )
                Box {
                    SwipeChatRow(
                        modifier = Modifier.testTag("swipe_chat"),
                        chat = chat,
                        observeFile = { file },
                        downloadFile = {},
                        openChatId = opened,
                        onOpenChange = { opened = it },
                        onClick = { navigations++ },
                        left = action,
                        right = action,
                        actionScope = actionScope
                    )
                }
            }
        }

        // A quarter of the row width reveals the action but stays below the full-swipe threshold.
        composeRule.onNodeWithTag("swipe_chat").performTouchInput {
            swipe(start = center, end = Offset(center.x + width * direction * 0.25f, center.y), durationMillis = 450L)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(42L, opened)
            assertEquals(0, performed)
        }

        composeRule.onNodeWithContentDescription("QA Chat", substring = true).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(0, navigations)
            assertEquals(null, opened)
        }

        composeRule.onNodeWithContentDescription("QA Chat", substring = true).performClick()
        composeRule.runOnIdle {
            assertEquals(1, navigations)
            assertEquals(0, performed)
        }
    }
}
