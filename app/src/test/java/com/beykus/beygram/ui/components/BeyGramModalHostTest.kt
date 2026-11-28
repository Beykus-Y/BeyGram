package com.beykus.beygram.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
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
 * The rules of the in-window modal host: a sheet and a dialog are drawn in the app window (so their material can
 * sample the screen), they honour the dismissal veto, and they stay Material windows outside Liquid Glass.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xhdpi")
class BeyGramModalHostTest {

    @get:Rule
    val rule = createComposeRule()

    private fun liquidGlass(quality: GlassQuality = GlassQuality.High) = AppearanceConfig(
        designStyle = DesignStyle.LiquidGlass,
        glassQuality = quality
    )

    @Test
    fun sheetInLiquidGlassIsAnInWindowGlassSurface() {
        val dismissals = mutableListOf<Unit>()
        rule.setContent {
            val host = rememberBeyGramModalHost()
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    Box(Modifier.fillMaxSize()) {
                        BeyGramSheet(onDismissRequest = { dismissals += Unit }) {
                            Text("sheet body")
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("sheet body").assertIsDisplayed()
        rule.onNodeWithTag("beyGramGlassSheet").assertIsDisplayed()
        rule.onAllNodesWithTag("beyGramPlainSheet").assertCountEquals(0)
        rule.onNodeWithTag("beyGramSheetScrim").performClick()
        rule.runOnIdle { assert(dismissals.size == 1) { "a tap on the scrim must request dismissal" } }
    }

    @Test
    fun sheetDismissalVetoKeepsInFlightWorkAlive() {
        val dismissals = mutableListOf<Unit>()
        rule.setContent {
            val host = rememberBeyGramModalHost()
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    Box(Modifier.fillMaxSize()) {
                        BeyGramSheet(
                            onDismissRequest = { dismissals += Unit },
                            sheetState = rememberBeyGramSheetState(canDismiss = { false })
                        ) {
                            Text("busy sheet")
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("busy sheet").assertIsDisplayed()
        rule.onNodeWithTag("beyGramSheetScrim").performClick()
        rule.runOnIdle {
            assert(dismissals.isEmpty()) { "a vetoed sheet must not be dismissed by a scrim tap" }
        }
        rule.onNodeWithText("busy sheet").assertIsDisplayed()
    }

    @Test
    fun sheetOutsideLiquidGlassKeepsTheMaterialWindow() {
        rule.setContent {
            BeyGramTheme(design = rememberDesign(AppearanceConfig(designStyle = DesignStyle.Default))) {
                BeyGramModalHostProvider(host = rememberBeyGramModalHost()) {
                    Box(Modifier.fillMaxSize()) { Text("screen") }
                }
                BeyGramSheet(onDismissRequest = {}) { Text("plain sheet body") }
            }
        }
        rule.onNodeWithText("plain sheet body").assertIsDisplayed()
        // The signed-in window must not get glass anywhere: the sheet went through Material's own window.
        rule.onAllNodesWithTag("beyGramGlassSheet").assertCountEquals(0)
    }

    @Test
    fun sheetInLiteQualityIsDenseGlassWithoutMovingToAnotherWindow() {
        rule.setContent {
            val host = rememberBeyGramModalHost()
            BeyGramTheme(design = rememberDesign(liquidGlass(GlassQuality.Lite))) {
                BeyGramModalHostProvider(host = host) {
                    Box(Modifier.fillMaxSize()) {
                        BeyGramSheet(onDismissRequest = {}) { Text("lite sheet body") }
                    }
                }
            }
        }
        rule.onNodeWithText("lite sheet body").assertIsDisplayed()
        // Lite keeps the glass geometry and layout; it simply does not sample a backdrop.
        rule.onNodeWithTag("beyGramGlassSheet").assertIsDisplayed()
    }

    @Test
    fun dialogInLiquidGlassIsHostedInTheWindowAndDismissesOutside() {
        var dismissals = 0
        rule.setContent {
            val host = rememberBeyGramModalHost()
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    Box(Modifier.fillMaxSize()) {
                        BeyGramDialog(
                            onDismissRequest = { dismissals++ },
                            title = "Dialog title",
                            confirmText = "OK",
                            onConfirm = {}
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("Dialog title").assertIsDisplayed()
        rule.onNodeWithTag("beyGramGlassDialog").assertIsDisplayed()
        // The panel covers the middle of the window, so the outside tap is made near the top edge of the scrim.
        rule.onNodeWithTag("beyGramDialogScrim").performTouchInput { click(Offset(centerX, 8f)) }
        rule.runOnIdle { assert(dismissals == 1) { "a tap outside a hosted dialog must dismiss it" } }
    }

    @Test
    fun dialogThatForbidsOutsideDismissIgnoresTheScrim() {
        var dismissals = 0
        rule.setContent {
            val host = rememberBeyGramModalHost()
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    Box(Modifier.fillMaxSize()) {
                        BeyGramDialog(
                            onDismissRequest = { dismissals++ },
                            title = "Pinned dialog",
                            confirmText = "OK",
                            onConfirm = {},
                            dismissOnClickOutside = false
                        )
                    }
                }
            }
        }
        rule.onNodeWithTag("beyGramDialogScrim").performTouchInput { click(Offset(centerX, 8f)) }
        rule.runOnIdle { assert(dismissals == 0) { "this dialog asked not to close on an outside tap" } }
    }

    @Test
    fun modalHostSeesRegistrationWhileASheetIsComposed() {
        var open by mutableStateOf(false)
        var hostRef: BeyGramModalHost? = null
        rule.setContent {
            val host = rememberBeyGramModalHost()
            hostRef = host
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    Box(Modifier.fillMaxSize()) {
                        if (open) BeyGramSheet(onDismissRequest = { open = false }) { Text("open sheet") }
                    }
                }
            }
        }
        rule.runOnIdle { assert(!hostRef!!.isOpen) }
        rule.runOnIdle { open = true }
        rule.waitForIdle()
        rule.onNodeWithText("open sheet").assertIsDisplayed()
        rule.runOnIdle { assert(hostRef!!.isOpen) { "the host must know a modal is on screen" } }
    }
}