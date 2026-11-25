package com.beykus.beygram.ui.screens.playback

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.LyricLine
import com.beykus.beygram.data.music.provider.ProviderLyrics
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackRepeatMode
import com.beykus.beygram.ui.motion.LocalMotionSettings
import com.beykus.beygram.ui.motion.MotionPreset
import com.beykus.beygram.ui.motion.MotionSettings
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Adaptivity and accessibility verification for the music player and lyrics mode:
 * small screens, landscape orientation, large system font scale, TalkBack semantics, and reduced motion.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class MusicPlayerAdaptivityUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()

    private fun samplePlayer(
        key: String = "tg:1",
        title: String = "Midnight Echoes Across The Valley",
        performer: String = "Northern Lights Ensemble",
        playing: Boolean = true
    ) = MusicPlayerUi(
        item = PlaybackItem(
            key = key,
            kind = MediaKind.Audio,
            path = "",
            title = "$performer — $title",
            durationSeconds = 215,
            performer = performer,
            trackTitle = title
        ),
        playing = playing,
        intentPlaying = playing,
        buffering = false,
        failed = false,
        ended = false,
        canNext = true,
        canPrevious = true,
        shuffleEnabled = false,
        repeatMode = PlaybackRepeatMode.Off,
        speed = 1f,
        queueSize = 4,
        queueIndex = 1
    )

    @Test
    fun smallDisplay_rendersPlayerControlsAndMetadata() {
        val player = samplePlayer(playing = true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BeyGramTheme {
                Box(Modifier.size(width = 320.dp, height = 560.dp)) {
                    MusicPlayerContent(
                        player = player,
                        panel = PlayerPanel.Player,
                        onPanel = {},
                        onCollapse = {},
                        onLeave = {},
                        onOpenEqualizer = null,
                        onOpenMusicSearch = null,
                        onOpenMyMusic = null
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(500L)

        compose.onNodeWithText(player.item.musicTitle()).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.common_pause)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.player_progress)).assertExists()
        compose.onNodeWithText(context.getString(R.string.player_queue)).assertExists()
        compose.onNodeWithText(context.getString(R.string.player_sound)).assertExists()
    }

    @Test
    @Config(qualifiers = "w800dp-h400dp-xhdpi")
    fun landscapeOrientation_rendersPlayerAndLyricsCleanly() {
        val player = samplePlayer(key = "ym:10")
        val pos = mutableLongStateOf(2_000L)
        compose.setContent {
            BeyGramTheme {
                LyricsPanel(
                    player = player,
                    onBack = {},
                    positionMsOverride = pos,
                    fetchLyrics = {
                        YandexLyricsState.Ready(
                            ProviderLyrics(
                                lines = listOf(LyricLine(1_000L, "Landscape synced lyric line")),
                                plainText = null,
                                copyrightHolder = null
                            )
                        )
                    }
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription(context.getString(R.string.queue_back_to_player)).assertIsDisplayed()
        compose.onNodeWithText("Landscape synced lyric line").assertIsDisplayed()
    }

    @Test
    fun largeSystemFontScale_preservesAllCoreSemantics() {
        val player = samplePlayer(playing = true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.6f)) {
                BeyGramTheme {
                    MusicPlayerContent(
                        player = player,
                        panel = PlayerPanel.Player,
                        onPanel = {},
                        onCollapse = {},
                        onLeave = {},
                        onOpenEqualizer = null,
                        onOpenMusicSearch = null,
                        onOpenMyMusic = null
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(500L)

        compose.onNodeWithContentDescription(context.getString(R.string.player_collapse)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.common_pause)).assertExists()
        compose.onNodeWithText(context.getString(R.string.player_queue)).assertExists()
        compose.onNodeWithText(context.getString(R.string.player_sound)).assertExists()
    }

    @Test
    fun talkBackSemantics_andReducedMotion_workAcrossPlayerAndLyrics() {
        val player = samplePlayer(key = "ym:55", playing = true)
        val pos = mutableLongStateOf(0L)
        compose.setContent {
            CompositionLocalProvider(LocalMotionSettings provides MotionSettings(preset = MotionPreset.Off)) {
                BeyGramTheme {
                    LyricsPanel(
                        player = player,
                        onBack = {},
                        positionMsOverride = pos,
                        fetchLyrics = {
                            YandexLyricsState.Ready(
                                ProviderLyrics(
                                    lines = listOf(LyricLine(5_000L, "Accessible lyric line")),
                                    plainText = null,
                                    copyrightHolder = null
                                )
                            )
                        }
                    )
                }
            }
        }
        compose.waitForIdle()

        // Intro instrumental dots have TalkBack description and render statically under reduced motion.
        compose.onNodeWithContentDescription(context.getString(R.string.lyrics_instrumental_a11y)).assertIsDisplayed()
        // Lyric row is clickable with TalkBack action label.
        compose.onNodeWithText("Accessible lyric line")
            .assertIsDisplayed()
            .assert(hasClickAction())
        // Play/Pause header button exposes both contentDescription and stateDescription.
        compose.onNodeWithContentDescription(context.getString(R.string.common_pause))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.a11y_state_playing)))
    }
}
