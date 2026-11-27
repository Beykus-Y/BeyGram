package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class VideoNoteOverlayInputTest {
    @get:Rule val compose = createComposeRule()

    @Test fun transparentSpaceDoesNotTapTheTimelineBehindTheOverlay() {
        val show = mutableStateOf(false)
        var timelineTaps = 0
        compose.setContent {
            BeyGramTheme {
                val context = LocalContext.current
                val owner = LocalLifecycleOwner.current
                val scope = rememberCoroutineScope()
                val recorder = remember(context, owner, scope) {
                    VideoNoteRecorder(
                        context, owner, scope,
                        currentSettings = { error("No recording starts in this test") },
                        currentDestination = { error("No recording starts in this test") },
                        onTakeReady = { _, _, _, _, _ -> },
                        onError = {}
                    )
                }
                Box(Modifier.fillMaxSize().testTag("chat")) {
                    Box(Modifier.fillMaxSize().clickable { timelineTaps++ })
                    if (show.value) VideoNoteRecordingOverlay(recorder)
                }
            }
        }
        val chat = compose.onNodeWithTag("chat")
        chat.performTouchInput { click(Offset(20f, 20f)) }
        compose.runOnIdle { show.value = true }
        chat.performTouchInput { click(Offset(20f, 20f)) }
        compose.runOnIdle { assertEquals(1, timelineTaps) }
    }
}
