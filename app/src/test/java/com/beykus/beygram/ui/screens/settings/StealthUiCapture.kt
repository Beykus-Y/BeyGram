package com.beykus.beygram.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.settings.ChatStealthMode
import com.beykus.beygram.data.settings.StealthSettings
import com.beykus.beygram.data.settings.StealthSettingsStore
import com.beykus.beygram.data.settings.StealthStorage
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Standalone capture using synthetic data. It only records when the caller supplies an output directory. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-rRU-w360dp-h780dp-xhdpi", application = Application::class)
class StealthUiCapture {
    @get:Rule val compose = createComposeRule()

    @Test fun capture() {
        val outputDirectory = System.getenv("BEYGRAM_STEALTH_CAPTURE_DIR")
        assumeNotNull(outputDirectory)
        val target = File(checkNotNull(outputDirectory))
        val stage = System.getenv("BEYGRAM_STEALTH_CAPTURE_STAGE") ?: "after"
        require(stage == "before" || stage == "after")
        val store = StealthSettingsStore(object : StealthStorage {
            override fun entries(): Map<String, *> = emptyMap<String, Any>()
            override fun replace(values: Map<String, Any>) = true
        })
        store.update {
            stealthPreset(StealthPresetKind.Maximum, it).copy(
                chats = mapOf(42L to ChatStealthMode.Block, 63L to ChatStealthMode.Allow),
                scheduledSendDelaySeconds = 30
            )
        }
        val style = mutableStateOf(DesignStyle.Default to false)
        compose.setContent {
            val (design, glassV2) = style.value
            BeyGramTheme(darkTheme = true, design = designFor(design, GlassQuality.Balanced, liquidGlassV2 = glassV2)) {
                StealthSettingsScreen(store, onServerPrivacy = {}, onBack = {})
            }
        }
        val views = if (stage == "before") listOf(DesignStyle.Default to false)
            else DesignStyle.entries.map { it to false } + (DesignStyle.LiquidGlass to true)
        views.forEach { (design, glassV2) ->
            compose.runOnIdle { style.value = design to glassV2 }
            val suffix = if (glassV2) "V2" else ""
            val output = File(target, "$stage-$design$suffix.png")
            compose.onRoot().captureRoboImage(output.absolutePath)
            check(output.length() > 0L) { "No screenshot was recorded at $output" }
        }
    }

    @Test
    @Config(qualifiers = "ru-rRU-w320dp-h640dp-mdpi")
    fun smallLightNormal() = captureReview("phone-small-light-normal", dark = false, settings = StealthSettings())

    @Test
    @Config(qualifiers = "ru-rRU-w411dp-h891dp-xhdpi")
    fun largeDarkCustom() = captureReview(
        "phone-large-dark-custom", dark = true,
        settings = StealthSettings(noReadReceipts = true, hideTyping = true, chats = mapOf(42L to ChatStealthMode.Block))
    )

    @Test
    @Config(qualifiers = "ru-rRU-w851dp-h393dp-land-mdpi")
    fun landscapeLightHiddenReading() = captureReview(
        "landscape-light-hidden-reading", dark = false,
        settings = stealthPreset(StealthPresetKind.HiddenReading, StealthSettings())
    )

    @Test
    @Config(qualifiers = "ru-rRU-w800dp-h1000dp-mdpi")
    fun tabletDarkFailClosed() = captureReview("tablet-dark-fail-closed", dark = true, settings = StealthSettings.FailClosed)

    private fun captureReview(name: String, dark: Boolean, settings: StealthSettings) {
        assumeTrue(System.getenv("BEYGRAM_STEALTH_CAPTURE_MATRIX") == "review")
        val outputDirectory = System.getenv("BEYGRAM_STEALTH_CAPTURE_DIR")
        assumeNotNull(outputDirectory)
        val target = File(checkNotNull(outputDirectory))
        val store = StealthSettingsStore(object : StealthStorage {
            override fun entries(): Map<String, *> = if (settings.failClosed) error("Synthetic read failure") else emptyMap<String, Any>()
            override fun replace(values: Map<String, Any>) = true
        })
        if (!settings.failClosed) store.update { settings }
        compose.setContent {
            BeyGramTheme(darkTheme = dark) {
                StealthSettingsScreen(store, onServerPrivacy = {}, onBack = {})
            }
        }
        val output = File(target, "$name.png")
        compose.onRoot().captureRoboImage(output.absolutePath)
        check(output.length() > 0L) { "No screenshot was recorded at $output" }
    }
}
