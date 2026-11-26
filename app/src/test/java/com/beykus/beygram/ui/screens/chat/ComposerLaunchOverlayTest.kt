package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class ComposerLaunchOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overlayAppearsOnlyWhileItHasAMeasuredDestination() {
        val state = ComposerLaunchState().apply {
            overlayBounds = Rect(0f, 0f, 720f, 1500f)
            fieldBounds = Rect(20f, 900f, 650f, 980f)
        }
        val text = "Synthetic outgoing message"
        val launch = state.start(composerLaunchPayload(text, emptyList(), 100), null, 0, 1f)!!
        val row = Rect(300f, 780f, 650f, 880f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BeyGramTheme {
                Box(Modifier.fillMaxSize()) { ChatComposerLaunchOverlay(state, chatId = 1) }
            }
        }

        compose.onNodeWithText(text).assertDoesNotExist()

        compose.runOnIdle { launch.positionRow(10, row) }
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithText(text).assertIsDisplayed()

        compose.runOnIdle {
            state.fieldBounds = Rect(20f, 730f, 650f, 800f)
            launch.awaitingSend = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(text).assertDoesNotExist()

        compose.mainClock.advanceTimeBy(COMPOSER_LAUNCH_POST_SEND_WAIT_MS + 160L)
        compose.runOnIdle { check(state.launches.isEmpty()) }
    }
}
