package com.beykus.beygram.ui.screens.playback

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.LyricLine
import com.beykus.beygram.data.music.provider.ProviderLyrics
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackRepeatMode
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests lyrics loading, rapid track switching, stale-result rejection, network error retry, missing text,
 * track-ended state, and tap-to-seek on [LyricsPanel].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class LyricsPanelUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()

    private fun playerUi(
        key: String = "ym:100",
        title: String = "Aurora",
        performer: String = "North",
        playing: Boolean = true,
        ended: Boolean = false
    ) = MusicPlayerUi(
        item = PlaybackItem(
            key = key,
            kind = MediaKind.Audio,
            path = "",
            title = "$performer — $title",
            durationSeconds = 180,
            performer = performer,
            trackTitle = title
        ),
        playing = playing,
        intentPlaying = playing,
        buffering = false,
        failed = false,
        ended = ended,
        canNext = true,
        canPrevious = true,
        shuffleEnabled = false,
        repeatMode = PlaybackRepeatMode.Off,
        speed = 1f,
        queueSize = 3,
        queueIndex = 1
    )

    @Test
    fun rapidTrackSwitch_neverDisplaysStaleLyricsFromPreviousTrack() {
        val slowFirst = CompletableDeferred<YandexLyricsState>()
        val fastSecond = CompletableDeferred<YandexLyricsState>()
        var currentPlayer by mutableStateOf(playerUi(key = "ym:1", title = "First"))
        val pos = mutableLongStateOf(5_000L)

        compose.setContent {
            BeyGramTheme {
                LyricsPanel(
                    player = currentPlayer,
                    onBack = {},
                    positionMsOverride = pos,
                    fetchLyrics = { key ->
                        if (key == "ym:1") slowFirst.await() else fastSecond.await()
                    }
                )
            }
        }

        // Switch quickly before track 1 finishes loading.
        currentPlayer = playerUi(key = "ym:2", title = "Second")
        compose.waitForIdle()

        // Track 2 resolves first; track 1 resolves late with stale text.
        fastSecond.complete(
            YandexLyricsState.Ready(
                ProviderLyrics(
                    lines = listOf(LyricLine(1_000L, "Fresh line of track two")),
                    plainText = null,
                    copyrightHolder = null
                )
            )
        )
        slowFirst.complete(
            YandexLyricsState.Ready(
                ProviderLyrics(
                    lines = listOf(LyricLine(1_000L, "Stale line of track one")),
                    plainText = null,
                    copyrightHolder = null
                )
            )
        )
        compose.waitForIdle()

        compose.onNodeWithText("Fresh line of track two").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Stale line of track one").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun networkFailure_offersRetryAndLoadsOnSecondAttempt() {
        val attempts = AtomicInteger(0)
        val pos = mutableLongStateOf(0L)

        compose.setContent {
            BeyGramTheme {
                LyricsPanel(
                    player = playerUi(key = "ym:42"),
                    onBack = {},
                    positionMsOverride = pos,
                    fetchLyrics = {
                        if (attempts.incrementAndGet() == 1) {
                            YandexLyricsState.Unavailable(LyricsUnavailableReason.Failed)
                        } else {
                            YandexLyricsState.Ready(
                                ProviderLyrics(
                                    lines = listOf(LyricLine(2_000L, "Recovered lyrics")),
                                    plainText = null,
                                    copyrightHolder = "Label"
                                )
                            )
                        }
                    }
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(context.getString(R.string.lyrics_failed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.player_retry)).assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertEquals(2, attempts.get())
        compose.onNodeWithText("Recovered lyrics").assertIsDisplayed()
    }

    @Test
    fun missingLyrics_showsEmptyMessageWithoutRetryButton() {
        val pos = mutableLongStateOf(0L)
        compose.setContent {
            BeyGramTheme {
                LyricsPanel(
                    player = playerUi(key = "ym:404"),
                    onBack = {},
                    positionMsOverride = pos,
                    fetchLyrics = { YandexLyricsState.Unavailable(LyricsUnavailableReason.NoText) }
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(context.getString(R.string.lyrics_none_for_track)).assertIsDisplayed()
        assertTrue(
            compose.onAllNodesWithText(context.getString(R.string.player_retry)).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun tapOnTimedLine_seeksToLineTimestamp() {
        val pos = mutableLongStateOf(1_000L)
        var soughtMs = -1L
        val lyrics = ProviderLyrics(
            lines = listOf(
                LyricLine(1_000L, "Opening line"),
                LyricLine(12_500L, "Chorus line")
            ),
            plainText = null,
            copyrightHolder = null
        )

        compose.setContent {
            BeyGramTheme {
                LyricsPanel(
                    player = playerUi(key = "ym:77"),
                    onBack = {},
                    positionMsOverride = pos,
                    onSeekToMs = { soughtMs = it },
                    fetchLyrics = { YandexLyricsState.Ready(lyrics) }
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Chorus line").assertIsDisplayed().performClick()
        assertEquals(12_500L, soughtMs)
    }

    @Test
    fun endedTrack_showsReplayActionAndKeepsLastLineStable() {
        val pos = mutableLongStateOf(180_000L)
        val lyrics = ProviderLyrics(
            lines = listOf(
                LyricLine(1_000L, "First verse"),
                LyricLine(60_000L, "Final refrain")
            ),
            plainText = null,
            copyrightHolder = null
        )

        compose.setContent {
            BeyGramTheme {
                LyricsPanel(
                    player = playerUi(key = "ym:88", playing = false, ended = true),
                    onBack = {},
                    positionMsOverride = pos,
                    fetchLyrics = { YandexLyricsState.Ready(lyrics) }
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription(context.getString(R.string.player_replay)).assertIsDisplayed()
        compose.onNodeWithText("Final refrain").assertIsDisplayed()
    }

    @Test
    fun telegramTrackInLyricsPanel_gracefullyShowsNoText() {
        val pos = mutableLongStateOf(0L)
        compose.setContent {
            BeyGramTheme {
                LyricsPanel(
                    player = playerUi(key = "tg:999", title = "Voice Memo"),
                    onBack = {},
                    positionMsOverride = pos
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(context.getString(R.string.lyrics_none_for_track)).assertIsDisplayed()
    }
}
