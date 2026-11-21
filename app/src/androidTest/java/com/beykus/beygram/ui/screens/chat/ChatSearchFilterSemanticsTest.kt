package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatSearchFilterSemanticsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun selectedChipAnnouncesRadioRoleAndSelectedState() {
        var selected by mutableStateOf(true)
        var clicked = false
        composeRule.setContent {
            BeyGramTheme {
                ChatSearchFilterChip(label = "Media", selected = selected) {
                    clicked = true
                    selected = false
                }
            }
        }
        val chip = composeRule.onNodeWithText("Media")
        chip.assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("Media").assertIsNotSelected()
        composeRule.runOnIdle { assertTrue(clicked) }
    }
}
