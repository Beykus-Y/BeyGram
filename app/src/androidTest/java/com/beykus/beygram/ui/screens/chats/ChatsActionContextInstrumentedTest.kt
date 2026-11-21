package com.beykus.beygram.ui.screens.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.TelegramChatFolder
import com.beykus.beygram.domain.model.TelegramChatFolders
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.TelegramFolderLoadState
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A chat action sheet must not retain an old account/folder's selection. */
@RunWith(AndroidJUnit4::class)
class ChatsActionContextInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun switchingAccountDismissesActionsOfPreviousAccount() {
        exerciseContextChange(changeFolder = false)
    }

    @Test fun switchingServerFolderDismissesActionsOfPreviousFolder() {
        exerciseContextChange(changeFolder = true)
    }

    private fun exerciseContextChange(changeFolder: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = ChatsScreenState()
        var accountId by mutableStateOf("account-a")
        val chat = ChatPreview(
            id = 42L,
            title = "QA Chat",
            initials = "QA",
            lastMessage = UiText.Raw("hello"),
            lastMessageTime = "12:00",
            avatarColorArgb = 0xFF5599CCL
        )
        val fileState = MutableStateFlow(MediaFileState(id = 0))

        composeRule.setContent {
            BeyGramTheme {
                ChatsScreen(
                    chats = listOf(chat),
                    archivedChats = emptyList(),
                    folderSnapshot = TelegramChatFolders(
                        initialized = true,
                        folders = listOf(TelegramChatFolder(id = 1, title = "Work"))
                    ),
                    folderChats = mapOf(1 to listOf(chat)),
                    folderLoadStates = mapOf(1 to TelegramFolderLoadState(loadedOnce = true, hasMore = false)),
                    connectionState = TelegramConnectionState.Connected,
                    accountId = accountId,
                    onFolderLoadMore = {},
                    onPinFolderChat = { _, _, _ -> Result.success(Unit) },
                    onLoadFolder = { Result.failure(IllegalStateException("unused")) },
                    onSaveFolder = { Result.success(1) },
                    onDeleteFolder = { Result.success(Unit) },
                    onReorderFolders = { _, _ -> Result.success(Unit) },
                    myProfile = null,
                    observeFile = { fileState },
                    downloadFile = {},
                    onMyProfile = {},
                    onArchiveClick = {},
                    onMarkRead = { true },
                    onMarkUnread = { Result.success(Unit) },
                    onArchive = { true },
                    onPinChat = { _, _ -> Result.success(Unit) },
                    onMuteChat = { _, _ -> Result.success(Unit) },
                    onDeleteChat = { Result.success(Unit) },
                    onNewChat = {},
                    archiveHidden = true,
                    onArchiveHiddenChange = {},
                    onChatClick = {},
                    state = state
                )
            }
        }
        composeRule.onNodeWithContentDescription("QA Chat", substring = true)
            .performTouchInput { longClick() }
        val copyChatLabel = context.getString(R.string.feature_copy_chat_title)
        composeRule.onNodeWithText(copyChatLabel).assertIsDisplayed()

        composeRule.runOnIdle {
            if (changeFolder) state.selectFolder(1)
            else accountId = "account-b"
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(copyChatLabel).assertDoesNotExist()
    }
}
