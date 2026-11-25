package com.beykus.beygram.ui.screens.music

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.canvas.BgCanvasMode
import com.beykus.beygram.data.canvas.CanvasSettingsStore
import com.beykus.beygram.data.lyrics.LyricsSettingsStore
import com.beykus.beygram.media.bgbeat.BgBeatSettingsStore
import com.beykus.beygram.media.bgmix.BgMixSettingsStore
import com.beykus.beygram.media.bgrhythm.BgRhythmMode
import com.beykus.beygram.media.bgrhythm.BgRhythmSettingsStore
import com.beykus.beygram.playback.TrackTransitionSettings
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-rRU-w360dp-h780dp-xhdpi", application = Application::class)
class MusicSettingsSyncTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BgMixSettingsStore.resetForTests()
        BgBeatSettingsStore.getInstance(context).setEnabled(true)
        TrackTransitionSettings.resetForTests()
        CanvasSettingsStore.setMode(context, BgCanvasMode.Off)
        BgRhythmSettingsStore.setMode(context, BgRhythmMode.Off)
        LyricsSettingsStore.setExternalLookupEnabled(context, false)
    }

    @Test
    fun hubDisplaysFourSectionsAndOpensPlaybackSubpage() {
        var equalizerOpened = false
        compose.setContent {
            BeyGramTheme(darkTheme = true, design = designFor(DesignStyle.Default, GlassQuality.Balanced)) {
                MusicSettingsScreen(
                    onBack = {},
                    onOpenEqualizer = { equalizerOpened = true }
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.music_settings_hero_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.music_settings_card_playback_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.music_settings_card_appearance_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.music_settings_card_lyrics_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.music_settings_card_storage_title)).assertIsDisplayed()

        compose.onNodeWithText(context.getString(R.string.music_settings_card_playback_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.settings_bgmix_title)).assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.player_equalizer)) and hasClickAction())
            .performScrollTo()
            .performClick()
        assertTrue(equalizerOpened)
    }

    @Test
    fun playbackAndAppearanceChangesSyncWithSharedStores() {
        compose.setContent {
            BeyGramTheme(darkTheme = true, design = designFor(DesignStyle.Default, GlassQuality.Balanced)) {
                MusicSettingsScreen(
                    onBack = {},
                    onOpenEqualizer = {}
                )
            }
        }

        // Toggle BG Mix off from Playback sub-page
        compose.onNodeWithText(context.getString(R.string.music_settings_card_playback_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.settings_bgmix_title)).performClick()
        assertFalse(BgMixSettingsStore.enabled.value)

        // Go back to Hub
        compose.onNodeWithContentDescription(context.getString(R.string.common_back)).performClick()

        // Open Appearance sub-page and change BG Rhythm & BG Canvas
        compose.onNodeWithText(context.getString(R.string.music_settings_card_appearance_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.rhythm_mode_dynamic)).performClick()
        compose.onNodeWithText(context.getString(R.string.canvas_mode_fullscreen)).performClick()

        assertEquals(BgRhythmMode.Dynamic, BgRhythmSettingsStore.flow(context).value)
        assertEquals(BgCanvasMode.Fullscreen, CanvasSettingsStore.flow(context).value.mode)
    }
}
