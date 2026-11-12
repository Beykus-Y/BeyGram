package com.beykus.beygram.ui.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.ui.screens.theme.StyleScene
import com.beykus.beygram.ui.screens.theme.StyleScenePreview
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot goldens of the style preview: each style's three screens at phone size, in the placement the style picks
 * by default. Balanced frosting keeps the glass deterministic on every runner (docs/screenshot-tests.md).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// SDK 34 runs on the JDK 17 used by CI; a plain Application keeps TDLib and the native media libraries out.
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class StylePreviewScreenshotTest(
    private val style: DesignStyle,
    // The scene's name, not the internal enum: a public constructor cannot expose an internal type.
    private val sceneName: String
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun light() {
        val scene = StyleScene.valueOf(sceneName)
        compose.setContent {
            Box(Modifier.size(360.dp, 780.dp)) {
                StyleScenePreview(
                    config = AppearanceConfig(designStyle = style, glassQuality = GlassQuality.Balanced),
                    systemDark = false,
                    scene = scene,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/StylePreview_${style.name}_${scene.name}_light.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> = DesignStyle.entries.flatMap { style ->
            StyleScene.entries.map { arrayOf<Any>(style, it.name) }
        }
    }
}
