package com.beykus.beygram.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTabRailTest {
    @get:Rule val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun railShowsVisibleTabsVerticallyWithTabSemanticsAndTouchTargets() {
        val opened = mutableListOf<MainTab>()
        composeRule.setContent {
            BeyGramTheme {
                MainTabRail(
                    tabs = listOf(MainTab.Chats, MainTab.Settings),
                    selected = MainTab.Chats,
                    onSelect = { opened += it }
                )
            }
        }
        composeRule.onNodeWithTag(MainTabRailTag).assertExists()

        val chats = composeRule.onNodeWithText(context.getString(R.string.tabs_chats))
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        val settings = composeRule.onNodeWithText(context.getString(R.string.settings_title))
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        assertTrue(
            "the rail must stack tabs, not place them in a row",
            chats.getUnclippedBoundsInRoot().bottom <= settings.getUnclippedBoundsInRoot().top
        )

        settings.performClick()
        composeRule.runOnIdle { assertEquals(listOf(MainTab.Settings), opened) }
    }

    @Test
    fun railUsesTheSamePressedCapsuleAsTheBottomBar() {
        composeRule.setContent {
            BeyGramTheme {
                MainTabRail(
                    tabs = listOf(MainTab.Chats, MainTab.Calls),
                    selected = MainTab.Chats,
                    onSelect = {}
                )
            }
        }
        val calls = composeRule.onNodeWithText(context.getString(R.string.tabs_calls))
        calls.performTouchInput { down(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MainTabPressCapsuleTag, useUnmergedTree = true).assertExists()
        calls.performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MainTabPressCapsuleTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun shortWindowCanScrollToTheLastTab() {
        composeRule.setContent {
            BeyGramTheme {
                Box(Modifier.size(width = 120.dp, height = 120.dp)) {
                    MainTabRail(
                        tabs = MainTab.entries,
                        selected = MainTab.Chats,
                        onSelect = {}
                    )
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.settings_title))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
