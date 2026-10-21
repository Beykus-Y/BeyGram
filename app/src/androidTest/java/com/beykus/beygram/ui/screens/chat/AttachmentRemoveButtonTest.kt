package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentRemoveButtonTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun targetIsAtLeast48dpAndRemovesOnlyTheSelectedFile() {
        val removed = mutableListOf<String>()
        composeRule.setContent {
            BeyGramTheme {
                androidx.compose.foundation.layout.Row {
                    listOf("first.jpg", "second.mp4", "third.pdf").forEach { name ->
                        AttachmentRemoveButton(name, enabled = true) { removed += name }
                    }
                }
            }
        }
        val target = composeRule.onNodeWithContentDescription("Удалить вложение «second.mp4»")
        target.assertHasClickAction().assertIsEnabled()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        composeRule.runOnIdle { assertEquals(listOf("second.mp4"), removed) }
    }

    @Test fun disabledTargetAnnouncesItsStateAndCannotRemove() {
        var removed = 0
        composeRule.setContent {
            BeyGramTheme {
                AttachmentRemoveButton("busy.jpg", enabled = false) { removed++ }
            }
        }
        val target = composeRule.onNodeWithContentDescription("Удалить вложение «busy.jpg»")
        target.assertIsNotEnabled().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        composeRule.runOnIdle { assertEquals(0, removed) }
    }
}
