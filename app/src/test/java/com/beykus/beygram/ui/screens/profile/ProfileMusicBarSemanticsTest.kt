package com.beykus.beygram.ui.screens.profile

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ProfileAudio
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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
    fun oneTrackOnAnotherProfileExposesOnePlayClick() {
        showBar(listOf(track))

        val play = label(R.string.profile_music_play)
        val row = compose.onNodeWithTag(ProfileMusicRowTag)
        row.assert(SemanticsMatcher("has only the labelled play click") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == play &&
                node.config.getOrNull(SemanticsActions.CustomActions).isNullOrEmpty()
        })
        assertEquals(1, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun severalTracksExposeOnePlayActionAndOneListClick() {
        showBar(listOf(track, track.copy(fileId = 8, title = "Second song")))

        val play = label(R.string.profile_music_play)
        val row = compose.onNodeWithTag(ProfileMusicRowTag)
        row.assert(SemanticsMatcher("has one separate play action") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions)?.map { it.label } == listOf(play) &&
                node.config.getOrNull(SemanticsActions.OnClick) != null
        })
        assertEquals(1, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
        row.performClick()
        compose.onNodeWithText(label(R.string.profile_music_title)).assertIsDisplayed()
    }

    private fun label(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun showBar(tracks: List<ProfileAudio>) {
        compose.setContent {
            ProfileMusicBar(
                ownerId = 1L,
                tracks = tracks,
                own = false,
                observeFile = { fileId -> MutableStateFlow(MediaFileState(fileId)) },
                downloadFile = {},
                autoDownloadFile = null,
                removeTrack = { Result.success(Unit) },
                moveTrackToFront = { Result.success(Unit) }
            )
        }
    }
}
