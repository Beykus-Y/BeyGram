package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.MediaFileState
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class ForumTopicsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchUsesOneDebouncedServerCallAndReplacesTheList() {
        var calls = 0
        var receivedQuery: String? = null
        val found = listOf(ForumTopic(9L, "Found topic"))
        render(
            topics = listOf(ForumTopic(2L, "Alpha topic"), ForumTopic(3L, "Beta topic")),
            searchTopics = { query, onPage ->
                calls++
                receivedQuery = query
                onPage(found)
                Result.success(found)
            }
        )

        compose.onNodeWithContentDescription("Search topics").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("found")
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Found topic").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()

        assertEquals(1, calls)
        assertEquals("found", receivedQuery)
        assertEquals(0, compose.onAllNodesWithText("Alpha topic").fetchSemanticsNodes().size)
    }

    @Test fun unreadFilterKeepsOnlyTopicsThatNeedAttention() {
        render(
            topics = listOf(
                ForumTopic(2L, "Unread topic", unreadCount = 1),
                ForumTopic(3L, "Read topic")
            )
        )

        compose.onNodeWithText("Unread").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Unread topic").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Read topic").fetchSemanticsNodes().size)
    }

    private fun render(
        topics: List<ForumTopic>,
        searchTopics: suspend (String, (List<ForumTopic>) -> Unit) -> Result<List<ForumTopic>> =
            { _, _ -> Result.success(emptyList()) }
    ) {
        val chat = ChatPreview(
            id = 1L,
            title = "Test forum",
            initials = "TF",
            lastMessage = UiText.Raw(""),
            lastMessageTime = "",
            unreadCount = 0,
            avatarColorArgb = 0xFF336699,
            isGroup = true
        )
        compose.setContent {
            BeyGramTheme {
                ForumTopicsScreen(
                    chatId = 1L,
                    chat = chat,
                    chatInfo = null,
                    topics = topics,
                    totalCount = topics.size,
                    topicsComplete = true,
                    searchTopics = searchTopics,
                    canCreate = true,
                    busy = false,
                    actionError = null,
                    observeFile = { id -> MutableStateFlow(MediaFileState(id)) },
                    downloadFile = {},
                    observeCustomEmoji = { MutableStateFlow(null) },
                    onBack = {},
                    onOpenGroupInfo = {},
                    onTopic = {},
                    onCreate = { _, _, done -> done(null) },
                    onEdit = { _, _, _, done -> done(null) },
                    loadDefaultIcons = { Result.success(emptyList()) },
                    onClosed = { _, _ -> },
                    onPinned = { _, _ -> },
                    onPinnedOrder = { _, done -> done(null) },
                    onDelete = {}
                )
            }
        }
        compose.waitForIdle()
    }
}
