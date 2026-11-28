package com.beykus.beygram.ui.components

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
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

/** A local of a screen, provided below the modal host so only the caller's context carries it. */
private val LocalScreenToken = compositionLocalOf { "no screen" }

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

    @Test
    fun aModalOfAnotherWindowMustNotRegisterWithThisHost() {
        val host = BeyGramModalHost()
        val ownWindow = android.view.View(ApplicationProvider.getApplicationContext())
        val otherWindow = android.view.View(ApplicationProvider.getApplicationContext())
        assert(!host.ownsWindow(ownWindow)) { "an unbound host owns no window" }
        host.windowRoot = ownWindow
        assert(host.ownsWindow(ownWindow)) { "the host owns the window it was bound to" }
        assert(!host.ownsWindow(otherWindow)) { "a sheet inside a Dialog window must not host here" }
        assert(!host.ownsWindow(null)) { "a detached composition owns nothing" }
    }

    @Test
    fun aDialogInsideAWindowOfItsOwnStaysInThatWindow() {
        var hostRef: BeyGramModalHost? = null
        rule.setContent {
            val host = rememberBeyGramModalHost()
            hostRef = host
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    Box(Modifier.fillMaxSize()) {
                        // A plain Compose Dialog is a window of its own: its content inherits this host through the
                        // composition locals, but it must not register with it and be drawn behind the window.
                        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                            BeyGramDialog(
                                onDismissRequest = {},
                                title = "Nested dialog",
                                confirmText = "OK",
                                onConfirm = {}
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assert(!hostRef!!.isOpen) { "a dialog in another window must not be hosted in this one" }
        }
    }

    @Test
    fun hostedSheetReadsTheLocalsOfTheScreenThatOpenedIt() {
        rule.setContent {
            val host = rememberBeyGramModalHost()
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    CompositionLocalProvider(LocalScreenToken provides "profile screen") {
                        BeyGramSheet(onDismissRequest = {}) { Text("sheet token: " + LocalScreenToken.current) }
                    }
                }
            }
        }
        rule.onNodeWithText("sheet token: profile screen").assertIsDisplayed()
    }

    @Test
    fun hostedDialogReadsTheLocalsOfTheScreenThatOpenedIt() {
        rule.setContent {
            val host = rememberBeyGramModalHost()
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    CompositionLocalProvider(LocalScreenToken provides "gift market") {
                        BeyGramDialog(
                            onDismissRequest = {},
                            title = "Dialog title",
                            confirmText = "OK",
                            onConfirm = {},
                            content = { Text("dialog token: " + LocalScreenToken.current) }
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("dialog token: gift market").assertIsDisplayed()
    }

    /**
     * The locals of the caller are re-captured on every composition of the declaration: a style or account change must
     * not leave the open sheet on the values of the previous screen.
     */
    @Test
    fun anOpenHostedSheetFollowsAChangedCallerLocal() {
        var token by mutableStateOf("first screen")
        rule.setContent {
            val host = rememberBeyGramModalHost()
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    CompositionLocalProvider(LocalScreenToken provides token) {
                        BeyGramSheet(onDismissRequest = {}) { Text("sheet token: " + LocalScreenToken.current) }
                    }
                }
            }
        }
        rule.onNodeWithText("sheet token: first screen").assertIsDisplayed()
        rule.runOnIdle { token = "second screen" }
        rule.onNodeWithText("sheet token: second screen").assertIsDisplayed()
    }

    @Test
    fun aDialogOpenedFromADialogRestoresItsParentWhenTheChildCloses() {
        var hostRef: BeyGramModalHost? = null
        var childOpen by mutableStateOf(false)
        rule.setContent {
            val host = rememberBeyGramModalHost()
            hostRef = host
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    Box(Modifier.fillMaxSize()) {
                        // The gift market: a dialog that opens the details of one of its lots in a second dialog.
                        BeyGramDialog(
                            onDismissRequest = {},
                            title = "gift market",
                            confirmText = "Close",
                            onConfirm = {}
                        )
                        if (childOpen) {
                            BeyGramDialog(
                                onDismissRequest = { childOpen = false },
                                title = "lot details",
                                confirmText = "OK",
                                onConfirm = {}
                            )
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("gift market").assertIsDisplayed()
        rule.runOnIdle { childOpen = true }
        rule.waitForIdle()
        rule.onNodeWithText("lot details").assertIsDisplayed()
        rule.runOnIdle {
            assert(hostRef!!.dialogs.size == 2) { "the market must stay on the dialog stack under the lot details" }
        }
        rule.runOnIdle { childOpen = false }
        rule.waitForIdle()
        rule.onAllNodesWithText("lot details").assertCountEquals(0)
        rule.onNodeWithText("gift market").assertIsDisplayed()
        rule.runOnIdle {
            assert(hostRef!!.isOpen) { "closing the lot details must not close the market underneath" }
            assert(hostRef!!.dialogs.size == 1) { "only the market is left on the stack" }
        }
    }

    @Test
    fun theSameDialogIsNeverPushedTwice() {
        var hostRef: BeyGramModalHost? = null
        var recompositions by mutableStateOf(0)
        rule.setContent {
            val host = rememberBeyGramModalHost()
            hostRef = host
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    Box(Modifier.fillMaxSize()) {
                        // Reading the counter makes the caller recompose the dialog without replacing it.
                        Text("recompositions: $recompositions")
                        BeyGramDialog(
                            onDismissRequest = {},
                            title = "market " + recompositions,
                            confirmText = "OK",
                            onConfirm = {}
                        )
                    }
                }
            }
        }
        rule.runOnIdle { recompositions = 1 }
        rule.waitForIdle()
        rule.runOnIdle { recompositions = 2 }
        rule.waitForIdle()
        rule.onNodeWithText("market 2").assertIsDisplayed()
        rule.runOnIdle {
            assert(hostRef!!.dialogs.size == 1) { "a recomposing caller must not stack the same dialog again" }
        }
    }

    @Test
    fun backIsSwallowedByASheetThatForbidsDismissal() {
        val dispatcher = OnBackPressedDispatcher()
        var backgroundBacks = 0
        val dismissals = mutableListOf<Unit>()
        rule.setContent {
            val owner = object : OnBackPressedDispatcherOwner {
                override val lifecycle = LocalLifecycleOwner.current.lifecycle
                override val onBackPressedDispatcher = dispatcher
            }
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                val host = rememberBeyGramModalHost()
                BeyGramTheme(design = rememberDesign(liquidGlass())) {
                    BeyGramModalHostProvider(host = host) {
                        Box(Modifier.fillMaxSize()) {
                            androidx.activity.compose.BackHandler(enabled = true) { backgroundBacks++ }
                            BeyGramSheet(
                                onDismissRequest = { dismissals += Unit },
                                sheetState = rememberBeyGramSheetState(canDismiss = { false })
                            ) { Text("busy sheet") }
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("busy sheet").assertIsDisplayed()
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.runOnIdle {
            assert(dismissals.isEmpty()) { "a vetoed sheet must not be dismissed by Back" }
            assert(backgroundBacks == 0) { "Back must not reach the screen under a vetoed sheet" }
        }
    }

    @Test
    fun backIsSwallowedByADialogThatForbidsBackDismissal() {
        val dispatcher = OnBackPressedDispatcher()
        var backgroundBacks = 0
        var dismissals = 0
        rule.setContent {
            val owner = object : OnBackPressedDispatcherOwner {
                override val lifecycle = LocalLifecycleOwner.current.lifecycle
                override val onBackPressedDispatcher = dispatcher
            }
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                val host = rememberBeyGramModalHost()
                BeyGramTheme(design = rememberDesign(liquidGlass())) {
                    BeyGramModalHostProvider(host = host) {
                        Box(Modifier.fillMaxSize()) {
                            androidx.activity.compose.BackHandler(enabled = true) { backgroundBacks++ }
                            BeyGramDialog(
                                onDismissRequest = { dismissals++ },
                                title = "Pinned dialog",
                                confirmText = "OK",
                                onConfirm = {},
                                dismissOnBackPress = false
                            )
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("Pinned dialog").assertIsDisplayed()
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.runOnIdle {
            assert(dismissals == 0) { "this dialog asked not to close on Back" }
            assert(backgroundBacks == 0) { "Back must not navigate the screen under the dialog" }
        }
    }

    @Test
    fun aDialogAboveASheetOwnsBack() {
        // The sheet is still drawn under the dialog, so its handler must stand down for Back.
        val dispatcher = OnBackPressedDispatcher()
        var backgroundBacks = 0
        var sheetVisible by mutableStateOf(true)
        var dialogOpen by mutableStateOf(true)
        rule.setContent {
            val owner = object : OnBackPressedDispatcherOwner {
                override val lifecycle = LocalLifecycleOwner.current.lifecycle
                override val onBackPressedDispatcher = dispatcher
            }
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                val host = rememberBeyGramModalHost()
                BeyGramTheme(design = rememberDesign(liquidGlass())) {
                    BeyGramModalHostProvider(host = host) {
                        Box(Modifier.fillMaxSize()) {
                            androidx.activity.compose.BackHandler(enabled = true) { backgroundBacks++ }
                            if (sheetVisible) BeyGramSheet(onDismissRequest = { sheetVisible = false }) {
                                Text("closing sheet")
                            }
                            if (dialogOpen) BeyGramDialog(
                                onDismissRequest = { dialogOpen = false },
                                title = "upper dialog",
                                confirmText = "OK",
                                onConfirm = {}
                            )
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("upper dialog").assertIsDisplayed()
        // Back belongs to the dialog, not to the sheet below it, so the sheet must still be there afterwards.
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.runOnIdle {
            assert(!dialogOpen) { "Back must close the top dialog" }
            assert(sheetVisible) { "Back must not reach the sheet under a dialog" }
            assert(backgroundBacks == 0) { "Back must not navigate the screen" }
        }
    }

    @Test
    fun backIsSwallowedWhileTheSheetAnimatesAway() {
        val dispatcher = OnBackPressedDispatcher()
        var backgroundBacks = 0
        var sheetVisible by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val owner = object : OnBackPressedDispatcherOwner {
                override val lifecycle = LocalLifecycleOwner.current.lifecycle
                override val onBackPressedDispatcher = dispatcher
            }
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                val host = rememberBeyGramModalHost()
                BeyGramTheme(design = rememberDesign(liquidGlass())) {
                    BeyGramModalHostProvider(host = host) {
                        Box(Modifier.fillMaxSize()) {
                            androidx.activity.compose.BackHandler(enabled = true) { backgroundBacks++ }
                            if (sheetVisible) BeyGramSheet(onDismissRequest = { sheetVisible = false }) {
                                Text("closing sheet")
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("closing sheet").assertIsDisplayed()
        // The first Back closes the sheet; the surface is still on screen for the length of its exit animation.
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(60)
        rule.onNodeWithText("closing sheet").assertIsDisplayed()
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.runOnIdle {
            assert(backgroundBacks == 0) { "Back during the exit animation must not reach the screen underneath" }
        }
        // Once the animation is over, the sheet is really gone and Back belongs to the screen again.
        rule.mainClock.advanceTimeBy(600)
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.runOnIdle { assert(backgroundBacks == 1) { "the screen must answer Back once the sheet is gone" } }
    }

    @Test
    fun aModalDropsTheFocusOfTheScreenUnderneath() {
        var sheetOpen by mutableStateOf(false)
        rule.setContent {
            val host = rememberBeyGramModalHost()
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    Box(Modifier.fillMaxSize()) {
                        val requester = remember { FocusRequester() }
                        TextField(
                            value = "",
                            onValueChange = {},
                            modifier = Modifier.testTag("screenField").focusRequester(requester)
                        )
                        LaunchedEffect(Unit) { requester.requestFocus() }
                        if (sheetOpen) BeyGramSheet(onDismissRequest = { sheetOpen = false }) {
                            Text("covering sheet")
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("screenField").assertIsFocused()
        rule.runOnIdle { sheetOpen = true }
        rule.waitForIdle()
        rule.onNodeWithText("covering sheet").assertIsDisplayed()
        // While a modal is open the content is hidden from accessibility, so the field is only in the unmerged tree.
        rule.onNodeWithTag("screenField", useUnmergedTree = true).assertIsNotFocused()
    }

    @Test
    fun aHostedSheetStillFocusesItsOwnField() {
        rule.setContent {
            val host = rememberBeyGramModalHost()
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    Box(Modifier.fillMaxSize()) {
                        BeyGramSheet(onDismissRequest = {}) {
                            val requester = remember { FocusRequester() }
                            TextField(
                                value = "",
                                onValueChange = {},
                                modifier = Modifier.testTag("sheetField").focusRequester(requester)
                            )
                            LaunchedEffect(Unit) { requester.requestFocus() }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("sheetField").assertIsFocused()
    }

    @Test
    fun aSecureDialogNeverJoinsTheHostedStack() {
        var hostRef: BeyGramModalHost? = null
        rule.setContent {
            val host = rememberBeyGramModalHost()
            hostRef = host
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    Box(Modifier.fillMaxSize()) {
                        BeyGramDialog(
                            onDismissRequest = {},
                            title = "Secret code",
                            confirmText = "OK",
                            onConfirm = {},
                            secure = true
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Secret code").assertIsDisplayed()
        rule.runOnIdle {
            // FLAG_SECURE belongs to a window of its own: a dialog with a secret must keep it.
            assert(!hostRef!!.isOpen) { "a secure dialog must not be drawn inside the activity window" }
        }
    }

    @Test
    fun theScrimCoversTheBottomChromeOfTheWindow() {
        var barTaps = 0
        rule.setContent {
            val host = rememberBeyGramModalHost()
            BeyGramTheme(design = rememberDesign(liquidGlass())) {
                BeyGramModalHostProvider(host = host) {
                    // The shape of the app window: routes above, the mini player as the last row of the column.
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth())
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("miniPlayer")
                                .clickable { barTaps++ }
                        )
                        BeyGramSheet(onDismissRequest = {}) { Text("sheet over the player") }
                    }
                }
            }
        }
        rule.onNodeWithText("sheet over the player").assertIsDisplayed()
        val scrim = rule.onNodeWithTag("beyGramSheetScrim").getUnclippedBoundsInRoot()
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        assert(scrim.bottom - scrim.top == root.bottom - root.top) { "the scrim must cover the mini player, not only the routes" }
        val bar = rule.onNodeWithTag("miniPlayer", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // The routes are covered by the scrim; a tap at the mini player's place must not reach the bar. Its own
        // semantics are cleared while a modal is open, so the position is taken from the measured bounds.
        rule.onRoot().performTouchInput { click(Offset(centerX, ((bar.top.value + bar.bottom.value) / 2f).dp.toPx())) }
        rule.runOnIdle {
            assert(barTaps == 0) { "the mini player must not take a tap while a modal covers it" }
        }
    }
}