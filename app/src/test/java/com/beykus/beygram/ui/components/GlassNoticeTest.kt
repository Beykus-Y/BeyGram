package com.beykus.beygram.ui.components

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.rememberDesign
import com.beykus.beygram.ui.design.rememberGlassBackdrop
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One notice, three materials: a real pane when it owns a backdrop, a tone wash when it already sits on a glass plane,
 * and the flat strip of the themes that have no glass. A nested pane on a glass plane would blur and tint twice.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xhdpi")
class GlassNoticeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun liquidGlass() = AppearanceConfig(
        designStyle = DesignStyle.LiquidGlass,
        glassQuality = GlassQuality.High
    )

    @Test
    fun aNoticeWithItsOwnBackdropIsARealPane() {
        rule.setContent {
            val backdrop = rememberGlassBackdrop()
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                    GlassNotice { Text("location denied") }
                }
            }
        }
        rule.onNodeWithText("location denied").assertExists()
        rule.onNodeWithTag("glassNoticePanel").assertExists()
        rule.onAllNodesWithTag("glassNoticeWash").assertCountEquals(0)
    }

    @Test
    fun aNoticeOnAGlassPaneDoesNotStackASecondPane() {
        rule.setContent {
            val backdrop = rememberGlassBackdrop()
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                    GlassNotice(onGlassPlane = true) { Text("waiting for network") }
                }
            }
        }
        rule.onNodeWithText("waiting for network").assertExists()
        rule.onAllNodesWithTag("glassNoticePanel").assertCountEquals(0)
        rule.onNodeWithTag("glassNoticeWash").assertExists()
    }

    @Test
    fun theThemesWithoutGlassKeepTheirFlatNotice() {
        rule.setContent {
            val backdrop = rememberGlassBackdrop()
            BeyGramTheme(design = rememberDesign(AppearanceConfig(designStyle = DesignStyle.Material3))) {
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                    GlassNotice(tone = NoticeTone.Danger) { Text("send failed") }
                }
            }
        }
        rule.onNodeWithText("send failed").assertExists()
        rule.onAllNodesWithTag("glassNoticePanel").assertCountEquals(0)
        rule.onNodeWithTag("glassNoticeWash").assertExists()
    }
}