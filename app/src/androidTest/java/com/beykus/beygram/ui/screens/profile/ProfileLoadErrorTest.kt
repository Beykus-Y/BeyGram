package com.beykus.beygram.ui.screens.profile

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileLoadErrorTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun retryInvokesRefreshWithoutDismissingErrorOrExistingContent() {
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.common_retry)
        var refreshes = 0
        composeRule.setContent {
            BeyGramTheme {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text("Cached profile title")
                    ProfileLoadError("Network error", loading = false) { refreshes++ }
                }
            }
        }
        composeRule.onNodeWithText("Cached profile title").assertExists()
        composeRule.onNodeWithText("Network error").assertExists()
        composeRule.onNodeWithText(label).assertHasClickAction().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun retryDisabledDuringLoad() {
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.common_retry)
        var refreshes = 0
        composeRule.setContent {
            BeyGramTheme { ProfileLoadError("Network error", loading = true) { refreshes++ } }
        }
        composeRule.onNodeWithText(label).assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(0, refreshes) }
    }
}
