package com.beykus.beygram.ui.screens.music

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.canvas.BgCanvasMode
import com.beykus.beygram.data.canvas.CanvasSettingsStore
import com.beykus.beygram.media.bgrhythm.BgRhythmMode
import com.beykus.beygram.media.bgrhythm.BgRhythmSettingsStore
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Visual capture & layout verification for Music Settings 2.0 across Light/Dark themes and
 * BeyGram design systems (Default, Material 3, Signature, Liquid Glass V2).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-rRU-w360dp-h780dp-xhdpi", application = Application::class)
class MusicSettingsVisualCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun captureMusicSettingsHubAndSubpages() {
        val context: Context = ApplicationProvider.getApplicationContext()
        BgRhythmSettingsStore.setMode(context, BgRhythmMode.Calm)
        CanvasSettingsStore.setMode(context, BgCanvasMode.Card)

        val captureDir = System.getenv("BEYGRAM_MUSIC_SETTINGS_CAPTURE_DIR")?.let(::File)?.apply { mkdirs() }
        val themeState = mutableStateOf(Triple(true, DesignStyle.Default, false))

        compose.setContent {
            val (dark, design, glassV2) = themeState.value
            BeyGramTheme(
                darkTheme = dark,
                design = designFor(design, GlassQuality.Balanced, liquidGlassV2 = glassV2)
            ) {
                MusicSettingsScreen(
                    onBack = {},
                    onOpenEqualizer = {}
                )
            }
        }

        val themes = listOf(
            Triple(true, DesignStyle.Default, false) to "hub-dark-default",
            Triple(false, DesignStyle.Default, false) to "hub-light-default",
            Triple(true, DesignStyle.Material3, false) to "hub-dark-material3",
            Triple(true, DesignStyle.Signature, false) to "hub-dark-signature",
            Triple(true, DesignStyle.LiquidGlass, true) to "hub-dark-liquidglass-v2",
        )

        themes.forEach { (config, name) ->
            compose.runOnIdle { themeState.value = config }
            compose.waitForIdle()
            if (captureDir != null) {
                val out = File(captureDir, "$name.png")
                compose.onRoot().captureRoboImage(out.absolutePath)
                check(out.length() > 0L) { "Empty screenshot: $out" }
            }
        }

        compose.runOnIdle { themeState.value = Triple(true, DesignStyle.LiquidGlass, true) }
        val subpages = listOf(
            R.string.music_settings_card_playback_title to "subpage-playback",
            R.string.music_settings_card_appearance_title to "subpage-appearance",
            R.string.music_settings_card_lyrics_title to "subpage-lyrics",
            R.string.music_settings_card_storage_title to "subpage-storage",
        )

        subpages.forEach { (titleRes, name) ->
            compose.onNodeWithText(context.getString(titleRes)).performClick()
            compose.waitForIdle()
            if (captureDir != null) {
                val out = File(captureDir, "$name.png")
                compose.onRoot().captureRoboImage(out.absolutePath)
                check(out.length() > 0L) { "Empty screenshot: $out" }
            }
            compose.onNodeWithContentDescription(context.getString(R.string.common_back)).performClick()
            compose.waitForIdle()
        }
    }
}
