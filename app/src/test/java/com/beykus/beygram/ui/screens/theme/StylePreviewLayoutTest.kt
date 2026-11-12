package com.beykus.beygram.ui.screens.theme

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.appearance.NavigationPlacement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

private const val PreviewBoxTag = "style-preview-test-box"

/**
 * Where the parts of the style preview land on a phone-sized screen, for every style and both bar placements. The
 * archive row and the create button must not cover each other, the list starts below the header, the bar sits on the
 * bottom edge, and a smaller box shows the whole phone instead of cutting it (the old 300 dp preview did that).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// SDK 34 runs on the JDK 17 used by CI; a plain Application keeps TDLib and the native media libraries out.
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class StylePreviewLayoutTest(
    private val style: DesignStyle,
    private val placement: NavigationPlacement
) {
    @get:Rule
    val compose = createComposeRule()

    private val name = "${style.name}_${placement.name}"

    @Test
    fun chatsKeepsTheArchiveAndTheCreateButtonApartAndTheBarAtTheBottom() {
        show(StyleScene.Chats)
        val header = bounds(SceneHeaderTag)
        val archive = bounds(SceneArchiveTag)
        val fab = bounds(SceneFabTag)
        val bar = bounds(SceneBarTag)
        val screenBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(
            "$name: the archive row starts at ${archive.top}, above the header bottom ${header.bottom}",
            archive.top >= header.bottom
        )
        assertFalse("$name: the create button covers the archive row", overlaps(fab, archive))
        assertTrue(
            "$name: the create button ends at ${fab.bottom}, below the top of the bar at ${bar.top}",
            fab.bottom <= bar.top
        )
        assertTrue(
            "$name: the bar ends at ${bar.bottom}, not on the bottom edge $screenBottom",
            abs(bar.bottom - screenBottom) <= 1f
        )
    }

    @Test
    fun settingsKeepsTheHeaderAboveTheBar() {
        show(StyleScene.Settings)
        val header = bounds(SceneHeaderTag)
        val bar = bounds(SceneBarTag)
        assertTrue(
            "$name: the header ends at ${header.bottom}, below the top of the bar at ${bar.top}",
            header.bottom <= bar.top
        )
    }

    /** The phone keeps its proportions in a box that is too small for it: the whole screen shows, scaled down. */
    @Test
    fun aSmallerBoxShowsTheWholeScreen() {
        compose.setContent {
            Box(Modifier.size(300.dp, 520.dp).testTag(PreviewBoxTag)) {
                StyleScenePreview(
                    config = config(),
                    systemDark = false,
                    scene = StyleScene.Chats,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        compose.waitForIdle()
        val box = bounds(PreviewBoxTag)
        val header = bounds(SceneHeaderTag)
        val bar = bounds(SceneBarTag)
        assertTrue(
            "$name: the header starts at ${header.top}, above the box top ${box.top}",
            header.top >= box.top - 1f
        )
        assertTrue(
            "$name: the bar ends at ${bar.bottom}, not at the box bottom ${box.bottom}",
            abs(bar.bottom - box.bottom) <= 1f
        )
        assertTrue("$name: the header is wider than the box", header.width <= box.width + 1f)
    }

    private fun show(scene: StyleScene) {
        compose.setContent {
            Box(Modifier.size(360.dp, 780.dp)) {
                StyleScenePreview(
                    config = config(),
                    systemDark = false,
                    scene = scene,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        compose.waitForIdle()
    }

    private fun config() = AppearanceConfig(
        designStyle = style,
        navigationPlacement = placement,
        // Balanced frosting is the same on every runner; High depends on the device's render effects.
        glassQuality = GlassQuality.Balanced
    )

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun overlaps(a: Rect, b: Rect): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> = DesignStyle.entries.flatMap { style ->
            listOf(NavigationPlacement.Pinned, NavigationPlacement.Floating).map { arrayOf<Any>(style, it) }
        }
    }
}
