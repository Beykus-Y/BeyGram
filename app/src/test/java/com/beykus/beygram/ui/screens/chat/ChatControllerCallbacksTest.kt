package com.beykus.beygram.ui.screens.chat

import android.app.Application
import android.content.ClipboardManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.beykus.beygram.data.unavailable.UnavailableTelegramRepository
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChatControllerCallbacksTest {
    @get:Rule val compose = createComposeRule()

    private fun message(id: Long) = ChatMessage(
        id = id,
        chatId = 10L,
        text = "msg $id",
        time = "12:00",
        isOutgoing = false
    )

    @Test
    fun searchControllerUsesLatestCallbacksWithoutRecreatingInstance() {
        val context = RuntimeEnvironment.getApplication()
        val repository = object : ConversationRepository by UnavailableTelegramRepository() {}
        var topicId by mutableLongStateOf(0L)
        var generation by mutableIntStateOf(1)
        val jumped = mutableListOf<Pair<Int, Long>>()
        val cancelled = mutableListOf<Int>()
        val errors = mutableListOf<Int>()
        lateinit var controller: ChatSearchController

        compose.setContent {
            val scope = rememberCoroutineScope()
            val gen = generation
            val onJump: (Long, Int) -> Unit = { id, _ -> jumped += gen to id }
            val onCancel: () -> Unit = { cancelled += gen }
            val onErr: (String) -> Unit = { errors += gen }
            BeyGramTheme {
                controller = rememberChatSearchController(
                    chatId = 10L,
                    forumTopicId = topicId,
                    conversation = repository,
                    context = context,
                    scope = scope,
                    cancelActiveJump = onCancel,
                    onJumpToMessage = onJump,
                    onError = onErr
                )
            }
        }

        val firstController = controller
        compose.runOnIdle {
            generation = 2
        }
        compose.waitForIdle()
        assertSame(firstController, controller)

        compose.runOnIdle {
            val hit = message(42L)
            controller.searchResults = listOf(hit)
            controller.openSearchResult(hit, scrollIndex = 0, scrollOffset = 0)
            controller.closeSearch(clearMatchSession = true)
        }
        assertEquals(listOf(2 to 42L), jumped)
        assertEquals(listOf(2), cancelled)

        compose.runOnIdle {
            controller.searching = true
            controller.searchQuery = "hello"
            topicId = 99L
        }
        compose.waitForIdle()
        assertNotSame(firstController, controller)
        assertFalse(controller.searching)
        assertEquals("", controller.searchQuery)
    }

    @Test
    fun selectionControllerUsesLatestCallbacksAndResetsOnForumTopicChange() {
        val context = RuntimeEnvironment.getApplication()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val repository = object : ConversationRepository by UnavailableTelegramRepository() {}
        var topicId by mutableLongStateOf(1L)
        var generation by mutableIntStateOf(1)
        val closed = mutableListOf<Int>()
        val fileState = MutableStateFlow(MediaFileState(id = 7))
        var messages by mutableStateOf(listOf(message(100L)))
        lateinit var controller: ChatSelectionController

        compose.setContent {
            val scope = rememberCoroutineScope()
            val gen = generation
            val closeActions: () -> Unit = { closed += gen }
            BeyGramTheme {
                controller = rememberChatSelectionController(
                    chatId = 10L,
                    forumTopicId = topicId,
                    accountId = "acc-1",
                    conversation = repository,
                    context = context,
                    clipboard = clipboard,
                    scope = scope,
                    messages = messages,
                    observeFile = { fileState },
                    onDownloadFile = {},
                    closeActions = closeActions
                )
            }
        }

        val firstController = controller
        compose.runOnIdle {
            generation = 2
        }
        compose.waitForIdle()
        assertSame(firstController, controller)

        compose.runOnIdle {
            controller.enterSelection(messages.first())
        }
        assertEquals(listOf(2), closed)
        assertTrue(controller.selectionMode)
        assertEquals(setOf(100L), controller.selectionIds)

        compose.runOnIdle {
            topicId = 2L
        }
        compose.waitForIdle()
        assertNotSame(firstController, controller)
        assertFalse(controller.selectionMode)
        assertTrue(controller.selectionIds.isEmpty())
    }
}
