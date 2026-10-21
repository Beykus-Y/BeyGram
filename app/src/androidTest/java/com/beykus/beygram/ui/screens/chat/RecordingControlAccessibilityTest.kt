package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.data.media.ComposerRecordingMode
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingControlAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun accessibilityClickStartsRecordingHandsFreeAndActiveClickLocks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actions = mutableListOf<String>()
        var active by mutableStateOf(false)
        rule.setContent {
            BeyGramTheme {
                RecordingControlButton(
                    mode = ComposerRecordingMode.Voice, active = active,
                    canceling = false, enabled = true,
                    onTap = { actions.add("mode") },
                    onStart = { actions.add("gesture") },
                    onAccessibleStart = { actions.add("start"); active = true },
                    onCanceling = {},
                    onLock = { actions.add("lock") },
                    onRelease = { actions.add("release") }
                )
            }
        }
        rule.onNodeWithContentDescription(context.getString(R.string.recording_a11y_start_voice))
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.recording_a11y_lock))
            .assertHasClickAction()
            .performClick()
        rule.runOnIdle { assertEquals(listOf("start", "lock"), actions) }
    }

    @Test fun disabledVideoRecorderCannotStart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actions = mutableListOf<String>()
        rule.setContent {
            BeyGramTheme {
                RecordingControlButton(
                    mode = ComposerRecordingMode.Video, active = false,
                    canceling = false, enabled = false,
                    onTap = { actions.add("mode") },
                    onStart = { actions.add("gesture") },
                    onAccessibleStart = { actions.add("start") },
                    onCanceling = {}, onLock = {}, onRelease = {}
                )
            }
        }
        rule.onNodeWithContentDescription(context.getString(R.string.recording_a11y_start_video))
            .assertIsNotEnabled()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.runOnIdle { assertEquals(emptyList<String>(), actions) }
    }
}
