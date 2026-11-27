package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.beykus.beygram.R
import com.beykus.beygram.data.unavailable.UnavailableTelegramRepository
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChatJumpControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aLookupBlocksTheOtherBadgeUntilItCompletes() {
        val result = CompletableDeferred<Result<Long?>>()
        var calls = 0
        var emptyResults = 0
        val repository = object : ConversationRepository by UnavailableTelegramRepository() {
            override suspend fun nextUnreadMention(chatId: Long, forumTopicId: Long): Result<Long?> {
                calls++
                return result.await()
            }

            override suspend fun nextUnreadReaction(chatId: Long, forumTopicId: Long): Result<Long?> {
                calls++
                return Result.success(null)
            }
        }
        compose.setContent {
            BeyGramTheme {
                ChatJumpControls(
                    chatId = 10,
                    forumTopicId = 20,
                    unreadMentions = 1,
                    unreadReactions = 1,
                    showJumpLatest = false,
                    conversation = repository,
                    onJumpToMessage = { error("Empty results cannot jump") },
                    onJumpEmpty = { emptyResults++ },
                    onJumpFailed = { error("The lookup must succeed") },
                    jumpLatestButton = {}
                )
            }
        }
        val context = RuntimeEnvironment.getApplication()
        val mention = compose.onNodeWithContentDescription(context.getString(R.string.chat_jump_to_mention))
        val reaction = compose.onNodeWithContentDescription(context.getString(R.string.chat_jump_to_reaction))
        mention.performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        reaction.performClick()
        compose.runOnIdle {
            assertEquals(1, calls)
            result.complete(Result.success(null))
        }
        compose.runOnIdle { assertEquals(1, emptyResults) }
        reaction.performClick()
        compose.runOnIdle {
            assertEquals(2, calls)
            assertEquals(2, emptyResults)
        }
    }
}
