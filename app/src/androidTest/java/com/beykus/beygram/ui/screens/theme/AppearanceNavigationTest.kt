package com.beykus.beygram.ui.screens.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.BubbleStyle
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun subsectionEditsUseTheSharedDraftAndApplyOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var saved by mutableStateOf(AppearanceConfig())
        var draft by mutableStateOf(saved)
        composeRule.setContent {
            BeyGramTheme {
                ThemeSettingsScreen(
                    saved = saved,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSave = { saved = it; draft = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.appearance_messages_title))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.theme_editor_shape_square))
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.theme_editor_undo))
            .performClick()
        composeRule.runOnIdle { assertEquals(BubbleStyle.Rounded, draft.bubbleStyle) }

        composeRule.onNodeWithText(context.getString(R.string.theme_editor_shape_square)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.theme_apply)).assertIsEnabled()
        composeRule.onNodeWithText(context.getString(R.string.theme_apply)).performClick()
        composeRule.runOnIdle {
            assertEquals(BubbleStyle.Square, saved.bubbleStyle)
            assertEquals(saved, draft)
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.common_back)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.theme_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.appearance_applied)).assertIsDisplayed()
    }

    @Test fun systemBackReturnsToOverviewBeforeLeavingTheSection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appearance = AppearanceConfig()
        composeRule.setContent {
            BeyGramTheme {
                ThemeSettingsScreen(
                    saved = appearance,
                    draft = appearance,
                    onDraftChange = {},
                    onSave = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.appearance_messages_title)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.theme_editor_shape_square)).assertIsDisplayed()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText(context.getString(R.string.theme_title)).assertIsDisplayed()
    }

    @Test fun leavingTheOverviewWithUnsavedChangesAsksBeforeDiscarding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var draft by mutableStateOf(AppearanceConfig())
        composeRule.setContent {
            BeyGramTheme {
                ThemeSettingsScreen(
                    saved = AppearanceConfig(),
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSave = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.appearance_messages_title)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.theme_editor_shape_square)).performClick()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText(context.getString(R.string.theme_title)).assertIsDisplayed()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText(context.getString(R.string.theme_discard_title)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(BubbleStyle.Square, draft.bubbleStyle) }
    }

    @Test fun draftAndOpenSubsectionSurviveActivityRecreation() {        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            var draft by rememberSaveable(stateSaver = AppearanceDraftSaver) {
                mutableStateOf<AppearanceConfig?>(AppearanceConfig())
            }
            BeyGramTheme {
                ThemeSettingsScreen(
                    saved = AppearanceConfig(),
                    draft = draft ?: AppearanceConfig(),
                    onDraftChange = { draft = it },
                    onSave = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.appearance_messages_title)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.theme_editor_shape_square)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.theme_editor_shape_square)).assertIsSelected()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText(context.getString(R.string.appearance_messages_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.theme_editor_shape_square)).assertIsSelected()
    }
}
