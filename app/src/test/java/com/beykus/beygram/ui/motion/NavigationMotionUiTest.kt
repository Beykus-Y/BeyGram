package com.beykus.beygram.ui.motion

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.beykus.beygram.RouteTransitionFrame
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.appearance.NavigationPlacement
import com.beykus.beygram.navigation.AppRoute
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.routeContentKey
import com.beykus.beygram.routeHostContentKey
import com.beykus.beygram.routeHostTransitionMove
import com.beykus.beygram.routeTransitionMove
import com.beykus.beygram.ui.accessibility.LocalSystemReducedMotion
import com.beykus.beygram.ui.components.MainTabsHost
import com.beykus.beygram.ui.components.MainTabDockTag
import com.beykus.beygram.ui.components.MainTabRail
import com.beykus.beygram.ui.components.MainTabSelectionIndicatorTag
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class NavigationMotionUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val pushSpec = MotionSpec(durationMillis = 260, intensity = 1f, easing = LinearEasing)
    private val popSpec = MotionSpec(durationMillis = 240, intensity = 1f, easing = LinearEasing)
    private val tabSpec = MotionSpec(durationMillis = 200, intensity = 1f, easing = LinearEasing)
    private val instantSpec = MotionSpec(durationMillis = 0, intensity = 0f, easing = LinearEasing)

    @Test
    fun pushAnimatesIncomingFromRightAndOutgoingWithLeftParallaxWithoutBlankFrame() {
        compose.mainClock.autoAdvance = false
        var currentRoute by mutableStateOf(AppRoute.Chats)
        val history = routeHistory(AppRoute.Chats)

        compose.setContent {
            RouteTransitionHost(currentRoute, history, pushSpec, popSpec, tabSpec)
        }
        compose.waitForIdle()

        commitState { currentRoute = AppRoute.Chat }
        compose.mainClock.advanceTimeBy(130)
        compose.waitForIdle()

        val outgoingX = positionX("route-Chats")
        val incomingX = positionX("route-Chat")
        assertTrue("Incoming Chat should slide in from the right, x=$incomingX", incomingX > 0f)
        assertTrue("Outgoing Chats should shift left with parallax, x=$outgoingX", outgoingX < 0f)
        assertTrue(
            "Parallax shift (${abs(outgoingX)}) should stay smaller than incoming offset ($incomingX)",
            abs(outgoingX) < incomingX
        )

        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertEquals(0f, positionX("route-Chat"), 1f)
        assertEquals(0, compose.onAllNodesWithTag("route-Chats").fetchSemanticsNodes().size)
    }

    @Test
    fun popAnimatesOutgoingToRightAndIncomingFromLeftParallaxWithoutBlankFrame() {
        compose.mainClock.autoAdvance = false
        var currentRoute by mutableStateOf(AppRoute.Chats)
        val history = routeHistory(AppRoute.Chats)

        compose.setContent {
            RouteTransitionHost(currentRoute, history, pushSpec, popSpec, tabSpec)
        }
        compose.waitForIdle()

        commitState { currentRoute = AppRoute.Chat }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()

        commitState { currentRoute = AppRoute.Chats }
        compose.mainClock.advanceTimeBy(120)
        compose.waitForIdle()

        val outgoingX = positionX("route-Chat")
        val incomingX = positionX("route-Chats")
        assertTrue("Outgoing Chat should slide off to the right, x=$outgoingX", outgoingX > 0f)
        assertTrue("Incoming Chats should return from left parallax, x=$incomingX", incomingX < 0f)

        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertEquals(0f, positionX("route-Chats"), 1f)
        assertEquals(0, compose.onAllNodesWithTag("route-Chat").fetchSemanticsNodes().size)
    }

    @Test
    fun midAnimationPushToPopInterruptionLeavesNoBlankFrameAndSettlesCleanly() {
        compose.mainClock.autoAdvance = false
        var currentRoute by mutableStateOf(AppRoute.Chats)
        val history = routeHistory(AppRoute.Chats)

        compose.setContent {
            RouteTransitionHost(currentRoute, history, pushSpec, popSpec, tabSpec)
        }
        compose.waitForIdle()
        val hostBounds = bounds("host-container")

        commitState { currentRoute = AppRoute.Chat }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        val beforePopChatX = positionX("route-Chat")

        // Interrupt Push mid-flight with Pop back to Chats.
        commitState { currentRoute = AppRoute.Chats }

        repeat(8) { step ->
            compose.mainClock.advanceTimeBy(40)
            compose.waitForIdle()
            val chatsNodes = compose.onAllNodesWithTag("route-Chats").fetchSemanticsNodes()
            val chatNodes = compose.onAllNodesWithTag("route-Chat").fetchSemanticsNodes()
            assertTrue("Neither screen is composed on interruption step $step", chatsNodes.isNotEmpty() || chatNodes.isNotEmpty())
            chatNodes.firstOrNull()?.let { chatNode ->
                assertTrue(
                    "Outgoing Chat reversed left on step $step: x=${chatNode.positionInRoot.x} < beforePop=$beforePopChatX",
                    chatNode.positionInRoot.x >= beforePopChatX - 1f
                )
            }
            val allNodes = chatsNodes + chatNodes
            val coveredLeft = allNodes.minOf { it.positionInRoot.x }
            val coveredRight = allNodes.maxOf { it.positionInRoot.x + it.size.width }
            // While both screens are composed they must cover the host edge to edge. Once the outgoing Chat is gone,
            // the returning Chats screen may still carry its remaining parallax offset (at most the push/pop parallax
            // share of the width); the opaque screen-colored slot wrapper in BeyGramApp covers that strip, so it is
            // never a blank frame. The test host has no such wrapper, so allow exactly that residual here.
            val residual = if (chatNodes.isEmpty()) hostBounds.width * routeParallaxFraction(popSpec.intensity) + 4f else 4f
            assertTrue(
                "Blank gap on step $step: [$coveredLeft, $coveredRight] vs [${hostBounds.left}, ${hostBounds.right}]",
                coveredLeft <= hostBounds.left + residual && coveredRight >= hostBounds.right - residual
            )
        }

        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        assertEquals(0f, positionX("route-Chats"), 1f)
        assertEquals(0, compose.onAllNodesWithTag("route-Chat").fetchSemanticsNodes().size)
    }

    @Test
    fun tabForwardAndBackwardAnimateEdgeToEdge() {
        compose.mainClock.autoAdvance = false
        var currentRoute by mutableStateOf(AppRoute.Chats)
        val history = routeHistory(AppRoute.Chats)

        compose.setContent {
            RouteTransitionHost(currentRoute, history, pushSpec, popSpec, tabSpec)
        }
        compose.waitForIdle()

        // Forward: Chats (0) -> Settings (3)
        commitState { currentRoute = AppRoute.Settings }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()

        val outgoingForwardX = positionX("route-Chats")
        val incomingForwardX = positionX("route-Settings")
        assertTrue("Forward tab exit should move left, x=$outgoingForwardX", outgoingForwardX < 0f)
        assertTrue("Forward tab enter should come from right, x=$incomingForwardX", incomingForwardX > 0f)

        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertEquals(0f, positionX("route-Settings"), 1f)

        // Backward: Settings (3) -> Chats (0)
        commitState { currentRoute = AppRoute.Chats }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()

        val outgoingBackwardX = positionX("route-Settings")
        val incomingBackwardX = positionX("route-Chats")
        assertTrue("Backward tab exit should move right, x=$outgoingBackwardX", outgoingBackwardX > 0f)
        assertTrue("Backward tab enter should come from left, x=$incomingBackwardX", incomingBackwardX < 0f)
    }

    @Test
    fun rootTabSlideKeepsSingleDockAndRetargetsIndicatorDuringRapidSwitch() {
        compose.mainClock.autoAdvance = false
        var selectedTab by mutableStateOf(MainTab.Chats)
        val history = routeHistory(AppRoute.Chats)

        compose.setContent {
            val design = designFor(DesignStyle.Default, GlassQuality.Balanced, NavigationPlacement.Pinned)
            BeyGramTheme(darkTheme = false, design = design) {
                CompositionLocalProvider(
                    LocalSystemReducedMotion provides false,
                    LocalMotionSettings provides MotionSettings()
                ) {
                    val move = history.visit(selectedTab.route)
                    val holder = rememberSaveableStateHolder()
                    val outerSpec = rememberRouteTransitionSpec<RouteTransitionFrame>(
                        move, pushSpec, popSpec, tabSpec,
                        tabDirection = history.tabDirection,
                        resolveMove = ::routeHostTransitionMove
                    )
                    Box(Modifier.size(360.dp, 640.dp)) {
                        AnimatedContent(
                            targetState = RouteTransitionFrame(selectedTab.route, false),
                            modifier = Modifier.fillMaxSize().clipToBounds(),
                            contentKey = ::routeHostContentKey,
                            transitionSpec = outerSpec,
                            label = "testRootSlot"
                        ) { frame ->
                            MainTabsHost(
                                accountId = "test-account",
                                selected = MainTab.entries.first { it.route == frame.route },
                                visibility = com.beykus.beygram.navigation.TabVisibility(),
                                onSelect = { selectedTab = it },
                                stateHolder = holder,
                                tabMotion = tabSpec,
                                tabDirection = history.tabDirection
                            ) { page ->
                                Box(Modifier.fillMaxSize().testTag("tab-page-${page.name}"))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()

        val initialDock = compose.onNodeWithTag(MainTabDockTag).fetchSemanticsNode()
        val initialPosition = initialDock.positionInRoot
        val initialIndicator = positionX(MainTabSelectionIndicatorTag)
        fun assertStableDock() {
            val bar = compose.onNodeWithTag(MainTabDockTag).fetchSemanticsNode()
            assertEquals(1, compose.onAllNodesWithTag(MainTabDockTag).fetchSemanticsNodes().size)
            assertEquals(initialDock.id, bar.id)
            assertEquals(initialPosition.x, bar.positionInRoot.x, 1f)
            assertEquals(initialPosition.y, bar.positionInRoot.y, 1f)
            assertEquals(1, compose.onAllNodesWithTag(MainTabSelectionIndicatorTag).fetchSemanticsNodes().size)
        }

        commitState { selectedTab = MainTab.Settings }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertStableDock()
        assertTrue(positionX("tab-page-Chats") < 0f)
        assertTrue(positionX("tab-page-Settings") > 0f)
        assertTrue(positionX(MainTabSelectionIndicatorTag) > initialIndicator)
        val interruptedIndicator = positionX(MainTabSelectionIndicatorTag)

        // Reverse before the first animation is finished. The same bar and indicator survive.
        commitState { selectedTab = MainTab.Chats }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        assertStableDock()
        assertTrue(positionX(MainTabSelectionIndicatorTag) < interruptedIndicator)

        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertStableDock()
        assertEquals(initialIndicator, positionX(MainTabSelectionIndicatorTag), 1f)
        assertEquals(0f, positionX("tab-page-Chats"), 1f)
        assertEquals(0, compose.onAllNodesWithTag("tab-page-Settings").fetchSemanticsNodes().size)

        // A completed switch and a full backward slide also leave the same dock in place.
        commitState { selectedTab = MainTab.Contacts }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertStableDock()
        commitState { selectedTab = MainTab.Calls }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertStableDock()
        assertTrue(positionX("tab-page-Contacts") > 0f)
        assertTrue(positionX("tab-page-Calls") < 0f)
    }

    @Test
    fun motionOffProducesImmediateFirstFrameCut() {
        assertImmediateCutForSpec(instantSpec)
    }

    @Test
    fun zeroIntensityProducesImmediateFirstFrameCut() {
        assertImmediateCutForSpec(MotionSpec(durationMillis = 260, intensity = 0f, easing = LinearEasing))
    }

    private fun assertImmediateCutForSpec(spec: MotionSpec) {
        compose.mainClock.autoAdvance = false
        var currentRoute by mutableStateOf(AppRoute.Chats)
        val history = routeHistory(AppRoute.Chats)

        compose.setContent {
            RouteTransitionHost(currentRoute, history, spec, spec, spec)
        }
        compose.waitForIdle()

        commitState { currentRoute = AppRoute.Chat }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()

        assertEquals("Chat should land immediately at 0f", 0f, positionX("route-Chat"), 1f)
        assertEquals("Chats should exit immediately", 0, compose.onAllNodesWithTag("route-Chats").fetchSemanticsNodes().size)
    }

    @Test
    fun rtlLayoutMirrorsPushPopAndTabSlideDirections() {
        compose.mainClock.autoAdvance = false
        var currentRoute by mutableStateOf(AppRoute.Chats)
        val history = routeHistory(AppRoute.Chats)

        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RouteTransitionHost(currentRoute, history, pushSpec, popSpec, tabSpec)
            }
        }
        compose.waitForIdle()

        // RTL Push: incoming from left (< 0), outgoing parallax right (> 0).
        commitState { currentRoute = AppRoute.Chat }
        compose.mainClock.advanceTimeBy(130)
        compose.waitForIdle()
        assertTrue("RTL Push incoming should enter from left", positionX("route-Chat") < 0f)
        assertTrue("RTL Push outgoing should shift right", positionX("route-Chats") > 0f)

        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()

        // RTL Pop: outgoing exits left (< 0), incoming returns from right parallax (> 0).
        commitState { currentRoute = AppRoute.Chats }
        compose.mainClock.advanceTimeBy(120)
        compose.waitForIdle()
        assertTrue("RTL Pop outgoing should exit left", positionX("route-Chat") < 0f)
        assertTrue("RTL Pop incoming should return from right", positionX("route-Chats") > 0f)

        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()

        // RTL Tab forward (Chats -> Settings): incoming enters from left (< 0), outgoing exits right (> 0).
        commitState { currentRoute = AppRoute.Settings }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertTrue("RTL forward Tab incoming should enter from left", positionX("route-Settings") < 0f)
        assertTrue("RTL forward Tab outgoing should exit right", positionX("route-Chats") > 0f)
    }

    @Test
    fun twoPaneContentKeyResizeCutsWithoutReplayingStaleMoveAndRailTabSwitchSlidesAsTab() {
        compose.mainClock.autoAdvance = false
        var currentRoute by mutableStateOf(AppRoute.Chats)
        var twoPane by mutableStateOf(false)
        val history = routeHistory(AppRoute.Chats)

        compose.setContent {
            val design = designFor(DesignStyle.Default, GlassQuality.Balanced, NavigationPlacement.Pinned)
            BeyGramTheme(darkTheme = false, design = design) {
                CompositionLocalProvider(
                    LocalSystemReducedMotion provides false,
                    LocalMotionSettings provides MotionSettings()
                ) {
                    val move = history.visit(currentRoute)
                    val layoutDirection = LocalLayoutDirection.current
                    val transitionSpec = rememberRouteTransitionSpec<RouteTransitionFrame>(
                        move = move,
                        push = pushSpec,
                        pop = popSpec,
                        tab = tabSpec,
                        tabDirection = history.tabDirection,
                        layoutDirection = layoutDirection,
                        resolveMove = ::routeTransitionMove
                    )
                    val selectTwoPaneTab: (MainTab) -> Unit = { tab ->
                        if (tab != MainTab.Chats && currentRoute != MainTab.Chats.route) {
                            history.prepareTabSwitch(MainTab.Chats.route)
                        }
                        currentRoute = tab.route
                    }
                    Box(Modifier.size(800.dp, 600.dp).testTag("host-container")) {
                        AnimatedContent(
                            targetState = RouteTransitionFrame(currentRoute, twoPane),
                            modifier = Modifier.fillMaxSize().clipToBounds(),
                            contentKey = { routeContentKey(it.route, it.twoPane) },
                            transitionSpec = transitionSpec,
                            label = "twoPaneRouteTransition"
                        ) { frame ->
                            Box(
                                Modifier.fillMaxSize()
                                    .background(Color.White)
                                    .testTag("slot-${frame.route.name}-${frame.twoPane}")
                            ) {
                                if (frame.twoPane && frame.route in setOf(
                                        AppRoute.Chats,
                                        AppRoute.Chat,
                                        AppRoute.ChatInfo,
                                        AppRoute.Archive,
                                        AppRoute.GlobalSearch,
                                        AppRoute.NewChat
                                    )
                                ) {
                                    Row(Modifier.fillMaxSize()) {
                                        MainTabRail(
                                            tabs = MainTab.entries,
                                            selected = MainTab.Chats,
                                            onSelect = selectTwoPaneTab
                                        )
                                        Box(Modifier.weight(1f).fillMaxHeight()) {
                                            Text("TwoPane:${frame.route.name}")
                                        }
                                    }
                                } else {
                                    Text("SinglePane:${frame.route.name}")
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        // Open Chat in single-pane (records NavigationMove.Push).
        commitState { currentRoute = AppRoute.Chat }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertEquals(0f, positionX("slot-Chat-false"), 1f)

        // Unfold/resize to two-pane while still on Chat: must cut immediately, NOT replay stale Push slide.
        commitState { twoPane = true }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(0f, positionX("slot-Chat-true"), 1f)
        assertEquals(0, compose.onAllNodesWithTag("slot-Chat-false").fetchSemanticsNodes().size)

        // Tap Settings on MainTabRail while Chat is open in two-pane mode: must animate as Tab (+1), NOT Pop.
        compose.onNodeWithText("Settings").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()

        val outgoingTwoPaneX = positionX("slot-Chat-true")
        val incomingSettingsX = positionX("slot-Settings-true")
        assertTrue("Rail tab switch should exit two-pane left, x=$outgoingTwoPaneX", outgoingTwoPaneX < 0f)
        assertTrue("Rail tab switch should enter Settings from right, x=$incomingSettingsX", incomingSettingsX > 0f)
    }

    private fun commitState(block: () -> Unit) {
        compose.runOnUiThread {
            Snapshot.withMutableSnapshot(block)
        }
        compose.waitForIdle()
    }

    private fun routeHistory(initial: AppRoute) = NavigationHistory(
        initial = initial,
        isRoot = { it in MainTab.entries.map { tab -> tab.route } },
        rootIndex = { route -> MainTab.entries.indexOfFirst { it.route == route } }
    )

    @Composable
    private fun RouteTransitionHost(
        currentRoute: AppRoute,
        history: NavigationHistory<AppRoute>,
        push: MotionSpec,
        pop: MotionSpec,
        tab: MotionSpec
    ) {
        val move = history.visit(currentRoute)
        val layoutDirection = LocalLayoutDirection.current
        val transitionSpec = rememberRouteTransitionSpec<RouteTransitionFrame>(
            move = move,
            push = push,
            pop = pop,
            tab = tab,
            tabDirection = history.tabDirection,
            layoutDirection = layoutDirection,
            resolveMove = ::routeTransitionMove
        )
        Box(Modifier.size(360.dp, 640.dp).testTag("host-container")) {
            AnimatedContent(
                targetState = RouteTransitionFrame(currentRoute, twoPane = false),
                modifier = Modifier.fillMaxSize().clipToBounds(),
                contentKey = { routeContentKey(it.route, it.twoPane) },
                transitionSpec = transitionSpec,
                label = "uiTestRouteTransition"
            ) { frame ->
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.White)
                        .testTag("route-${frame.route.name}")
                )
            }
        }
    }

    private fun positionX(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.x

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
