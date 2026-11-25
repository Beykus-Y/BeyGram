package com.beykus.beygram.ui.screens.playback

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.beykus.beygram.R
import com.beykus.beygram.media.audio.AudioDetails
import com.beykus.beygram.media.audio.AudioSourceOrigin
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudioDetailsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun displaysEstimatedBitrateAndHidesUnknownFields() {
        val details = AudioDetails(
            sourceOrigin = AudioSourceOrigin.YandexMusic,
            fileAvailable = true,
            codec = "MP3",
            container = "MP3",
            bitrateKbps = 320,
            bitrateEstimated = true,
            sampleRateHz = 44_100,
            channelCount = 2,
            bitDepth = null,
            fileSizeBytes = null,
        )

        compose.setContent {
            BeyGramTheme {
                AudioDetailsSection(details)
            }
        }

        compose.onNodeWithText(context.getString(R.string.audio_details_source_yandex)).assertIsDisplayed()
        compose.onNodeWithText("MP3").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.audio_details_bitrate_estimated, 320)).assertIsDisplayed()
        compose.onNodeWithText("44.1 kHz").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.audio_details_channels_stereo)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.audio_details_streaming)).assertDoesNotExist()
    }

    @Test
    fun displaysStreamingNoticeWhenFileNotCachedYet() {
        val details = AudioDetails(
            sourceOrigin = AudioSourceOrigin.Telegram,
            fileAvailable = false,
        )

        compose.setContent {
            BeyGramTheme {
                AudioDetailsSection(details)
            }
        }

        compose.onNodeWithText(context.getString(R.string.audio_details_source_telegram)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.audio_details_streaming)).assertIsDisplayed()
    }
}
