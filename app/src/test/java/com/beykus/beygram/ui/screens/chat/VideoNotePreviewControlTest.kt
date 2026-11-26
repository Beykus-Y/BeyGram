package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.beykus.beygram.BeyGramApplication
import com.beykus.beygram.R
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = BeyGramApplication::class, qualifiers = "ru-w360dp-h720dp-xhdpi")
class VideoNotePreviewControlTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<BeyGramApplication>()

    @Test
    fun thePreviewControlAnnouncesPlayPauseAndTogglesIt() {
        var playing by mutableStateOf(false)
        var toggles = 0
        compose.setContent {
            BeyGramTheme {
                VideoNotePlaybackControl(
                    playing = playing,
                    onToggle = {
                        toggles++
                        playing = !playing
                    },
                    modifier = Modifier.size(320.dp)
                )
            }
        }

        compose.onNodeWithContentDescription(app.getString(R.string.common_play)).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(playing)
            assertEquals(1, toggles)
        }

        compose.onNodeWithContentDescription(app.getString(R.string.common_pause)).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertFalse(playing)
            assertEquals(2, toggles)
        }
    }
}