package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatSettingsSwitchAccessibilityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun rowAndThumbExposeOnlyOneToggleAction() {
        var checked by mutableStateOf(false)
        var calls = 0
        composeRule.setContent {
            BeyGramTheme {
                SettingSwitch("Notifications", checked, enabled = true) {
                    calls++
                    checked = it
                }
            }
        }
        val matcher = hasText("Notifications") and hasClickAction()
        composeRule.onAllNodes(matcher).assertCountEquals(1)
        composeRule.onNode(matcher).performClick()
        composeRule.runOnIdle {
            assertTrue(checked)
            assertEquals("One tap must update once", 1, calls)
        }
        composeRule.onNode(matcher).performClick()
        composeRule.runOnIdle {
            assertFalse(checked)
            assertEquals("A second tap must update once more", 2, calls)
        }
    }

    @Test fun disabledSwitchDoesNotExposeAnEnabledAction() {
        var calls = 0
        composeRule.setContent {
            BeyGramTheme {
                SettingSwitch("Notifications", checked = false, enabled = false) { calls++ }
            }
        }
        composeRule.onNode(hasText("Notifications")).assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(0, calls) }
    }
}
