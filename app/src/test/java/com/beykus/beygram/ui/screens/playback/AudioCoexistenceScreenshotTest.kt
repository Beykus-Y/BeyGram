package com.beykus.beygram.ui.screens.playback

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackState
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-rUS-w320dp-h640dp-xhdpi", application = Application::class)
class AudioCoexistenceScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun speechAndMusicControlsDispatchIndependently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val music = mutableStateOf<PlaybackState?>(
            PlaybackState(PlaybackItem("track", MediaKind.Audio, "", "Track", 100), intentPlaying = true)
        )
        val speech = PlaybackState(PlaybackItem("voice", MediaKind.Voice, "", "Voice", 30), positionMs = 12000)
        val commands = mutableListOf<String>()
        compose.setContent {
            BeyGramTheme {
                SpeechTransportBar(
                    music = music.value,
                    speech = speech,
                    bottomInset = false,
                    floatingDock = false,
                    compact = true,
                    onOpenMusic = {},
                    onToggleMusic = { commands += "music" },
                    onToggleSpeech = { commands += it },
                    onOpenSpeech = {},
                )
            }
        }
        val musicControl = context.getString(R.string.coexistence_pause_lane, context.getString(R.string.coexistence_internal))
        val voiceControl = context.getString(R.string.coexistence_play_lane, context.getString(R.string.media_kind_voice))
        compose.onNodeWithContentDescription(musicControl).performClick()
        compose.onNodeWithContentDescription(voiceControl).performClick()
        compose.runOnIdle {
            assertEquals(listOf("music", "voice"), commands)
            music.value = null
        }
        compose.onNodeWithContentDescription(musicControl).assertDoesNotExist()
        compose.onNodeWithContentDescription(voiceControl).assertExists()
    }
}
