package com.beykus.beygram.ui.screens.profile

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ProfileAudio
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The play/pause control of the profile music bar is one TalkBack target with its own name. It is not
 * announced twice, once on the disc and again as a row action, so Voice and Switch Access see one control.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: keeps TDLib and the native media libraries out of this test process.
@Config(sdk = [34], application = Application::class)
class ProfileMusicBarSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val track = ProfileAudio(
        fileId = 7,
        title = "Song",
        performer = "Artist",
        fileName = "song.mp3",
        durationSeconds = 120,
        mimeType = "audio/mpeg",
        sizeBytes = 10L
    )

    @Test
    fun playButtonIsExactlyOneNamedTargetWhenNotPlaying() {
        val files = MutableStateFlow(MediaFileState(id = 7))
        compose.setContent {
            ProfileMusicBar(
                ownerId = 1L,
                tracks = listOf(track),
                own = false,
                observeFile = { files },
                downloadFile = {},
                autoDownloadFile = null,
                removeTrack = { Result.success(Unit) },
                moveTrackToFront = { Result.success(Unit) }
            )
        }
        val playLabel = RuntimeEnvironment.getApplication().getString(R.string.profile_music_play)
        // One play/pause form: the row's custom action. No node may also carry the play label as its own description.
        compose.onNodeWithTag(ProfileMusicRowTag, useUnmergedTree = true).assert(
            SemanticsMatcher("has play custom action") { node ->
                node.config.getOrNull(SemanticsProperties.CustomActions)?.any { it.label == playLabel } == true
            }
        )
        compose.onAllNodesWithContentDescription(playLabel, useUnmergedTree = true).assertCountEquals(0)
    }
}
