package com.beykus.beygram.ui.screens.chats

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.GroupCreationState
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateGroupScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val contacts = (1..120).map { id ->
        ContactPreview(
            userId = id.toLong(),
            title = "Tester $id",
            subtitle = "@tester$id",
            photo = null,
            avatarColorArgb = 0xFF556677L
        )
    }

    @Test
    fun createActionRemainsVisibleWithLongContactListAndSubmitsSelection() {
        val submitted = mutableListOf<Pair<String, List<Long>>>()
        val fileState = MutableStateFlow(MediaFileState(id = 0))

        composeRule.setContent {
            BeyGramTheme {
                CreateGroupScreen(
                    loadContacts = { Result.success(contacts) },
                    creationState = GroupCreationState.Idle,
                    startGroupCreation = { title, members ->
                        submitted += title to members
                        Result.success(Unit)
                    },
                    acknowledgeGroupCreation = {},
                    acknowledgeUncertainGroupCreation = {},
                    observeFile = { fileState },
                    downloadFile = {},
                    onBack = {},
                    onCreated = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        // The footer stays in the viewport even though the list has 120 items.
        composeRule.onNodeWithTag("group_create_submit")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Tester 1").performClick()
        composeRule.onNodeWithTag("group_title_input").performTextInput("Test group")
        composeRule.onNodeWithTag("group_create_submit")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("Test group" to listOf(1L)), submitted)
        }
    }

    @Test
    fun uncertainCreationKeepsVerificationVisibleAndBlocksDuplicateSubmit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileState = MutableStateFlow(MediaFileState(id = 0))
        var verified = 0
        var submitted = 0

        composeRule.setContent {
            BeyGramTheme {
                CreateGroupScreen(
                    loadContacts = { Result.success(contacts) },
                    creationState = GroupCreationState.NeedsVerification,
                    startGroupCreation = { _, _ ->
                        submitted++
                        Result.success(Unit)
                    },
                    acknowledgeGroupCreation = {},
                    acknowledgeUncertainGroupCreation = { verified++ },
                    observeFile = { fileState },
                    downloadFile = {},
                    onBack = {},
                    onCreated = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.new_chat_group_unchanged_unknown))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("group_create_submit")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.new_chat_group_checked))
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, verified)
            assertEquals(0, submitted)
        }
    }

    @Test
    fun failedCreationFeedbackVisibleWithoutScrollingThroughContacts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileState = MutableStateFlow(MediaFileState(id = 0))
        composeRule.setContent {
            BeyGramTheme {
                CreateGroupScreen(
                    loadContacts = { Result.success(contacts) },
                    creationState = GroupCreationState.Failed,
                    startGroupCreation = { _, _ -> Result.success(Unit) },
                    acknowledgeGroupCreation = {},
                    acknowledgeUncertainGroupCreation = {},
                    observeFile = { fileState },
                    downloadFile = {},
                    onBack = {},
                    onCreated = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.new_chat_group_failed))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("group_create_submit").assertIsDisplayed()
    }

    @Test
    fun verificationActionCanScrollIntoViewAtConstrainedHeight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileState = MutableStateFlow(MediaFileState(id = 0))
        var verified = 0
        composeRule.setContent {
            BeyGramTheme {
                // Simulate a small-height landscape/IME viewport with a long list.
                Box(Modifier.height(300.dp)) {
                    CreateGroupScreen(
                        loadContacts = { Result.success(contacts) },
                        creationState = GroupCreationState.NeedsVerification,
                        startGroupCreation = { _, _ -> Result.success(Unit) },
                        acknowledgeGroupCreation = {},
                        acknowledgeUncertainGroupCreation = { verified++ },
                        observeFile = { fileState },
                        downloadFile = {},
                        onBack = {},
                        onCreated = { _, _ -> }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("group_create_submit")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.new_chat_group_checked))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, verified) }
    }

    @Test
    fun rejectedGroupCreationShowsInlineErrorAndKeepsActionReachable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileState = MutableStateFlow(MediaFileState(id = 0))
        var attempts = 0
        composeRule.setContent {
            BeyGramTheme {
                CreateGroupScreen(
                    loadContacts = { Result.success(contacts) },
                    creationState = GroupCreationState.Idle,
                    startGroupCreation = { _, _ ->
                        attempts++
                        Result.failure(IllegalStateException("simulated failure"))
                    },
                    acknowledgeGroupCreation = {},
                    acknowledgeUncertainGroupCreation = {},
                    observeFile = { fileState },
                    downloadFile = {},
                    onBack = {},
                    onCreated = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tester 1").performClick()
        composeRule.onNodeWithTag("group_title_input").performTextInput("Test group")
        composeRule.onNodeWithTag("group_create_submit").performClick()
        composeRule.onNodeWithText(context.getString(R.string.new_chat_group_failed))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("group_create_submit")
            .assertIsDisplayed()
            .assertIsEnabled()
        composeRule.runOnIdle { assertEquals(1, attempts) }
    }
}
