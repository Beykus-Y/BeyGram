package com.beykus.beygram.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.ui.design.rememberDesign
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shared chrome of secondary screens and threads (#1358 R1/R2): the bars are real glass panes over the body that
 * scrolls beneath them, the body learns their height, and every other style keeps the old structure untouched.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xhdpi")
class SecondaryScreenChromeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun config(style: DesignStyle, quality: GlassQuality = GlassQuality.High) =
        AppearanceConfig(designStyle = style, glassQuality = quality)

    @Test
    fun theBarsAreGlassPanesAndTheBodyLearnsTheirHeight() {
        var top: Dp = 0.dp
        var bottom: Dp = 0.dp
        rule.setContent {
            BeyGramTheme(design = rememberDesign(config(DesignStyle.LiquidGlass))) {
                GlassScreenScaffold(
                    modifier = Modifier.fillMaxSize(),
                    toolbar = { Text("Subscreen title", modifier = Modifier.testTag("barText")) },
                    bottomBar = { Text("Composer") }
                ) { chrome ->
                    top = chrome.top
                    bottom = chrome.bottom
                    LazyColumn(Modifier.fillMaxWidth().height(80.dp)) {
                        item { Text("row") }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("screenChromeTopPane").assertExists()
        rule.onNodeWithTag("screenChromeBottomPane").assertExists()
        rule.onNodeWithText("Subscreen title").assertIsDisplayed()
        rule.runOnIdle {
            assert(top > 0.dp) { "the body must be told how tall the toolbar is" }
            assert(bottom > 0.dp) { "the body must be told how tall the composer is" }
        }
    }

    @Test
    fun withoutGlassTheBarsStayInTheFlowAndNothingIsInset() {
        var top: Dp = 0.dp
        var bottom: Dp = 0.dp
        rule.setContent {
            BeyGramTheme(design = rememberDesign(config(DesignStyle.Material3))) {
                GlassScreenScaffold(
                    modifier = Modifier.fillMaxSize(),
                    toolbar = { Text("Material3 toolbar") },
                    bottomBar = { Text("Material3 composer") }
                ) { chrome ->
                    top = chrome.top
                    bottom = chrome.bottom
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Material3 toolbar").assertIsDisplayed()
        rule.onNodeWithText("Material3 composer").assertIsDisplayed()
        rule.onAllNodesWithTag("screenChromeTopPane").assertCountEquals(0)
        rule.onAllNodesWithTag("screenChromeBottomPane").assertCountEquals(0)
        rule.runOnIdle {
            assert(top == 0.dp) { "no glass means no overlay inset" }
            assert(bottom == 0.dp) { "no glass means no overlay inset" }
        }
    }

    @Test
    fun liteQualityKeepsTheLayoutWithoutRecording() {
        var top: Dp = 0.dp
        rule.setContent {
            BeyGramTheme(design = rememberDesign(config(DesignStyle.LiquidGlass, GlassQuality.Lite))) {
                GlassScreenScaffold(
                    modifier = Modifier.fillMaxSize(),
                    toolbar = { Text("Lite toolbar") }
                ) { chrome ->
                    top = chrome.top
                    Spacer(Modifier.height(chrome.top))
                    Text("lite body")
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Lite toolbar").assertIsDisplayed()
        rule.onNodeWithText("lite body").assertIsDisplayed()
        rule.runOnIdle { assert(top == 0.dp) { "Lite does not sample, so the body is not inset for a pane" } }
    }

    @Test
    fun theBarSkipsItsOwnFillOnThePane() {
        var onGlass = false
        rule.setContent {
            BeyGramTheme(design = rememberDesign(config(DesignStyle.LiquidGlass))) {
                GlassScreenScaffold(
                    modifier = Modifier.fillMaxSize(),
                    toolbar = { onGlass = LocalScreenChromeOnGlass.current }
                ) { chrome ->
                    Spacer(Modifier.height(chrome.top))
                }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { assert(onGlass) { "a bar inside the pane must know it is on glass" } }
    }

    @Test
    fun everyStyleRendersTheSameChrome() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                DesignStyle.entries.forEach { style ->
                    BeyGramTheme(design = rememberDesign(AppearanceConfig(designStyle = style))) {
                        GlassScreenScaffold(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            toolbar = { Text("bar-${style.name}") }
                        ) { chrome ->
                            Text("body-${style.name} (${chrome.top})")
                        }
                    }
                }
            }
        }
        DesignStyle.entries.forEach { style ->
            rule.onNodeWithText("bar-${style.name}").assertExists()
        }
    }

    @Test
    fun aBottomBarKeepsItsContentAboveIt() {
        var bottom: Dp = 0.dp
        rule.setContent {
            BeyGramTheme(design = rememberDesign(config(DesignStyle.LiquidGlass))) {
                GlassScreenScaffold(
                    modifier = Modifier.fillMaxSize(),
                    toolbar = { Text("top") },
                    bottomBar = { Text("bottom bar text", color = Color.Unspecified) }
                ) { chrome ->
                    bottom = chrome.bottom
                    LazyColumn(Modifier.fillMaxSize()) { item { Text("row") } }
                }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { assert(bottom > 0.dp) }
        rule.onNodeWithText("bottom bar text").assertIsDisplayed()
    }

    @Test
    fun theBodyRecomposesWhenTheBarHeightChanges() {
        var tall by mutableStateOf(false)
        rule.setContent {
            BeyGramTheme(design = rememberDesign(config(DesignStyle.LiquidGlass))) {
                GlassScreenScaffold(
                    modifier = Modifier.fillMaxSize(),
                    toolbar = {
                        Text(if (tall) "two line title" else "one")
                        if (tall) Spacer(Modifier.height(24.dp))
                    }
                ) { chrome ->
                    Text("inset ${chrome.top}", modifier = Modifier.testTag("bodyInset"))
                }
            }
        }
        rule.waitForIdle()
        val short = rule.onNodeWithTag("bodyInset").fetchSemanticsNode().config.toString()
        rule.runOnIdle { tall = true }
        rule.waitForIdle()
        val tallText = rule.onNodeWithTag("bodyInset").fetchSemanticsNode().config.toString()
        assert(short != tallText) { "the body must see the new bar height" }
    }
}