package com.beykus.beygram.ui.screens.chats

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val createLabel = context.getString(R.string.new_chat_group_create)
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
        composeRule.onNodeWithText(createLabel)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Tester 1").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("Test group")
        composeRule.onNodeWithText(createLabel)
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
        composeRule.onNodeWithText(context.getString(R.string.new_chat_group_create))
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
}
