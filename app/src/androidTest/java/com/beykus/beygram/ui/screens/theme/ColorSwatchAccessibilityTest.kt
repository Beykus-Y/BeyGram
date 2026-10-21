package com.beykus.beygram.ui.screens.theme

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHeightIsAtLeast
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
class ColorSwatchAccessibilityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun swatchKeepsRadioSelectionAnd48dpTarget() {
        var selected = 0
        composeRule.setContent {
            BeyGramTheme {
                ColorSwatch(0xFF6A4FDC.toInt(), "Violet", selected = true) { selected++ }
            }
        }
        composeRule.onNodeWithContentDescription("Violet")
            .assertHasClickAction().assertIsSelected()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        composeRule.runOnIdle { assertEquals(1, selected) }
    }
}
