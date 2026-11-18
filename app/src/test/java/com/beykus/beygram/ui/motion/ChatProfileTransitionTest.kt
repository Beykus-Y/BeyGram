package com.beykus.beygram.ui.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import com.beykus.beygram.chatProfilePairRoutes
import com.beykus.beygram.navigation.AppRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the continuous chat ↔ profile transition (#999): the flight windows,
 * the no-duplication visibility contract, the scroll-driven collapse and the controller's
 * lifecycle — engagement, target attach, geometry fallback, reversals and the predictive
 * back gesture.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatProfileTransitionTest {

    // ---- Flight windows -------------------------------------------------------

    @Test fun windowProgressClampsIntoItsWindow() {
        assertEquals(0f, windowProgress(0f, 0.125f, 0.875f), 0f)
        assertEquals(0.5f, windowProgress(0.5f, 0.125f, 0.875f), 0.0001f)
        assertEquals(1f, windowProgress(1f, 0.125f, 0.875f), 0f)
        assertEquals(0f, windowProgress(-1f, 0.125f, 0.875f), 0f)
        assertEquals(1f, windowProgress(2f, 0.125f, 0.875f), 0f)
    }

    @Test fun realHeadersCarryTheEndpointsAndTheOverlayOnlyTheMiddle() {
        // The push starts with the chat header fully visible and nothing flying yet.
        assertEquals(1f, chatSideVisibility(0f), 0f)
        assertEquals(0f, profileSideVisibility(0f), 0f)
        assertEquals(0f, overlayVisibility(0f), 0f)
        // The flight ends with the profile header carrying the identity.
        assertEquals(0f, chatSideVisibility(1f), 0f)
        assertEquals(1f, profileSideVisibility(1f), 0f)
        assertEquals(0f, overlayVisibility(1f), 0f)
    }

    @Test fun midFlightNeitherRealHeaderIsVisibleWhileTheOverlayCarriesTheElements() {
        // No duplication anywhere in the flight: for every sample the shared elements exist in
        // exactly one place — a real header at the ends, the overlay in the middle.
        var p = 0f
        while (p <= 1f) {
            val realVisible = chatSideVisibility(p) + profileSideVisibility(p)
            if (overlayVisibility(p) > 0.99f) {
                assertEquals("real headers must be hidden at p=$p", 0f, realVisible, 0.0001f)
            }
            if (realVisible > 0.99f) {
                assertEquals("overlay must be gone when a real header shows at p=$p", 0f, overlayVisibility(p), 0.0001f)
            }
            p += 0.01f
        }
    }

    @Test fun visibilityRampsAreMonotonic() {
        var p = 0f
        var previousChat = chatSideVisibility(0f)
        var previousProfile = profileSideVisibility(0f)
        while (p <= 1f) {
            // The chat header only hides as the flight leaves it; the profile only appears at the landing.
            assertTrue(previousChat + 0.0001f >= chatSideVisibility(p))
            assertTrue(previousProfile <= profileSideVisibility(p) + 0.0001f)
            previousChat = chatSideVisibility(p)
            previousProfile = profileSideVisibility(p)
            p += 0.01f
        }
    }

    @Test fun lerpRectHitsBothEndpoints() {
        val from = Rect(10f, 20f, 50f, 60f)
        val to = Rect(100f, 200f, 300f, 400f)
        assertEquals(from, lerpRect(from, to, 0f))
        assertEquals(to, lerpRect(from, to, 1f))
        val mid = lerpRect(from, to, 0.5f)
        assertEquals((from.left + to.left) / 2f, mid.left, 0.001f)
    }

    // ---- Collapsible header ---------------------------------------------------

    @Test fun personProfileCollapseFollowsTheRealScrollOffset() {
        assertEquals(0f, collapseFractionForScroll(scrollPx = 0, headerHeightPx = 400), 0f)
        assertEquals(0.5f, collapseFractionForScroll(scrollPx = 200, headerHeightPx = 400), 0.0001f)
        assertEquals(1f, collapseFractionForScroll(scrollPx = 800, headerHeightPx = 400), 0f)
        // The header is unmeasured before its first layout: nothing collapses.
        assertEquals(0f, collapseFractionForScroll(scrollPx = 100, headerHeightPx = 0), 0f)
    }

    @Test fun avatarReportsMatchTheVisualCollapseScaleAroundTheCenter() {
        val layout = Rect(100f, 200f, 212f, 312f)
        // Expanded: the visual equals the layout bounds.
        assertEquals(layout, layout.shrunkAroundCenter(0f, 0.5f))
        // Fully collapsed: the avatar shrank to half its size, centered where it was.
        val collapsed = layout.shrunkAroundCenter(1f, 0.5f)
        assertEquals(layout.center.x, collapsed.center.x, 0.001f)
        assertEquals(56f, collapsed.width, 0.001f)
        assertEquals(56f, collapsed.height, 0.001f)
        // Half-collapsed: the shrink is proportional, never inverted and never larger.
        val half = layout.shrunkAroundCenter(0.5f, 0.5f)
        assertEquals(84f, half.width, 0.001f)
        assertEquals(layout.center.y, half.center.y, 0.001f)
        assertEquals(layout, layout.shrunkAroundCenter(-1f, 0.5f))
        assertEquals(layout, layout.shrunkAroundCenter(2f, 0f))
    }

    // ---- Geometry fingerprint -------------------------------------------------

    @Test fun staleFingerprintsNeverDriveAFlight() {
        val base = ChatProfileWindowFingerprint(1080, 2400, 2.75f)
        assertTrue(fingerprintMatches(base, ChatProfileWindowFingerprint(1080, 2400, 2.75f)))
        // Rotation or a fold resize invalidates the captured bounds.
        assertFalse(fingerprintMatches(base, ChatProfileWindowFingerprint(2400, 1080, 2.75f)))
        // A font-scale change invalidates them too.
        assertFalse(fingerprintMatches(base, ChatProfileWindowFingerprint(1080, 2400, 3.3f)))
    }

    // ---- Route pair -----------------------------------------------------------

    @Test fun onlyTheChatSlotPairsWithChatInfo() {
        assertTrue(chatProfilePairRoutes(AppRoute.Chat, AppRoute.ChatInfo))
        assertTrue(chatProfilePairRoutes(AppRoute.ChatInfo, AppRoute.Chat))
        assertTrue(chatProfilePairRoutes(AppRoute.MessageThread, AppRoute.ChatInfo))
        assertTrue(chatProfilePairRoutes(AppRoute.ChatInfo, AppRoute.MessageThread))
        assertFalse(chatProfilePairRoutes(AppRoute.Chats, AppRoute.ChatInfo))
        assertFalse(chatProfilePairRoutes(AppRoute.ChatInfo, AppRoute.ChatMembers))
        assertFalse(chatProfilePairRoutes(AppRoute.Chat, AppRoute.Settings))
    }

    // ---- Pair background transform --------------------------------------------

    @Test fun instantMotionCutsThePairImmediately() {
        val instant = MotionSpec(durationMillis = 0, intensity = 0f, easing = FastOutSlowInEasing)
        val transform = chatProfilePairContentTransform(instant, instant, pushwards = true)
        assertEquals(0f, transform.targetContentZIndex, 0f)
    }

    @Test fun pushDrawsTheProfileOverTheChatAndPopTheReverse() {
        val spec = MotionSpec(durationMillis = 260, intensity = 1f, easing = FastOutSlowInEasing)
        assertEquals(1f, chatProfilePairContentTransform(spec, spec, pushwards = true).targetContentZIndex, 0f)
        assertEquals(-1f, chatProfilePairContentTransform(spec, spec, pushwards = false).targetContentZIndex, 0f)
    }

    // ---- Controller lifecycle -------------------------------------------------

    private val fingerprint = ChatProfileWindowFingerprint(1080, 2400, 2.75f)
    private val chatGeometry = ChatProfileElementGeometry(
        avatar = Rect(40f, 120f, 120f, 200f),
        title = Rect(140f, 130f, 400f, 160f),
        status = Rect(140f, 164f, 400f, 190f)
    )
    private val profileGeometry = ChatProfileElementGeometry(
        avatar = Rect(480f, 300f, 960f, 780f),
        title = Rect(100f, 800f, 980f, 860f),
        status = Rect(100f, 866f, 980f, 900f)
    )
    private val toolbarGeometry = ChatProfileElementGeometry(
        avatar = Rect(140f, 120f, 204f, 184f),
        title = Rect(220f, 130f, 700f, 170f)
    )
    private val animated = MotionSpec(durationMillis = 260, intensity = 1f, easing = FastOutSlowInEasing)
    private val instant = MotionSpec(durationMillis = 0, intensity = 0f, easing = FastOutSlowInEasing)

    private fun visuals(chatId: Long) = ChatProfileFlightVisuals(
        avatar = { },
        chatTitle = "Chat $chatId",
        chatStatus = "online",
        avatarSize = Dp(112f)
    )

    @Test fun pushEngagesOnlyWithFreshGeometryAndMotion() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val controller = ChatProfileTransitionController(scope)
        try {
            val chatId = 7L
            // Motion off means no flight at all, whatever the geometry.
            assertFalse(controller.beginPush(chatId, visuals(chatId), fingerprint, instant, animated))
            assertNull(controller.flight)
            // A stale fingerprint (rotation, fold) must not fly either.
            assertFalse(controller.beginPush(chatId, visuals(chatId),
                ChatProfileWindowFingerprint(2400, 1080, 2.75f), animated, animated))
            assertNull(controller.flight)
            // Nothing reported yet: the caller keeps the standard transition.
            assertFalse(controller.beginPush(chatId, visuals(chatId), fingerprint, animated, animated))
            assertNull(controller.flight)

            val source = controller.chatHeaderSource(chatId)
            source.reportAvatar(chatGeometry.avatar, fingerprint)
            source.reportTitle(chatGeometry.title, fingerprint)
            source.reportStatus(chatGeometry.status, fingerprint)
            assertTrue(controller.beginPush(chatId, visuals(chatId), fingerprint, animated, animated))
            val flight = controller.flight
            assertTrue(flight != null)
            assertEquals(0f, flight!!.progress, 0f)
            // The profile side composes only after navigation; the flight waits for its report.
            assertFalse(flight.ready)

            controller.reportProfileHeader(chatId, fingerprint, profileGeometry)
            assertTrue(flight.ready)
            assertTrue(flight.targetAttached)
            // A later report never moves a flight that already has a target.
            val moved = profileGeometry.copy(avatar = Rect(0f, 0f, 10f, 10f))
            controller.reportProfileHeader(chatId, fingerprint, moved)
            assertEquals(profileGeometry.avatar, flight.profileGeometry?.avatar)
        } finally {
            scope.cancel()
        }
    }

    @Test fun pushFallsBackWhenTheProfileGeometryNeverArrives() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val controller = ChatProfileTransitionController(scope)
        try {
            val chatId = 7L
            val source = controller.chatHeaderSource(chatId)
            source.reportAvatar(chatGeometry.avatar, fingerprint)
            assertTrue(controller.beginPush(chatId, visuals(chatId), fingerprint, animated, animated))
            assertTrue(controller.flight != null)
            // The profile never placed its header: after the wait the standard transition takes over.
            advanceTimeBy(GeometryWaitMillis + 50)
            assertNull(controller.flight)
        } finally {
            scope.cancel()
        }
    }

    @Test fun popFallsBackWithoutUsableChatGeometry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val controller = ChatProfileTransitionController(scope)
        try {
            val chatId = 9L
            val target = controller.profileTarget(chatId)
            target.reportAvatar(profileGeometry.avatar, fingerprint)
            target.reportTitle(profileGeometry.title, fingerprint)
            // The profile reports itself, but the chat header never did (a deep link opened the
            // profile directly): back must not jump to invented coordinates.
            assertFalse(controller.beginPop(chatId, visuals(chatId), fingerprint, animated))
            assertNull(controller.flight)
        } finally {
            scope.cancel()
        }
    }

    @Test fun popStartsAtTheProfileStateAndUsesTheToolbarWhenCollapsed() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val controller = ChatProfileTransitionController(scope)
        try {
            val chatId = 7L
            val source = controller.chatHeaderSource(chatId)
            source.reportAvatar(chatGeometry.avatar, fingerprint)
            val target = controller.profileTarget(chatId)
            target.reportAvatar(profileGeometry.avatar, fingerprint)
            target.reportTitle(profileGeometry.title, fingerprint)
            target.reportStatus(profileGeometry.status, fingerprint)

            assertTrue(controller.beginPop(chatId, visuals(chatId), fingerprint, animated))
            val expanded = controller.flight
            assertTrue(expanded != null)
            assertEquals(1f, expanded!!.progress, 0f)
            assertEquals(profileGeometry.avatar, expanded.profileGeometry?.avatar)

            // From a collapsed profile the flight starts at the compact toolbar avatar.
            controller.dispose()
            target.reportCollapsed(true)
            target.reportToolbarAvatar(toolbarGeometry.avatar, fingerprint)
            target.reportToolbarTitle(toolbarGeometry.title, fingerprint)
            assertTrue(controller.beginPop(chatId, visuals(chatId), fingerprint, animated))
            val collapsed = controller.flight
            assertTrue(collapsed != null)
            assertEquals(toolbarGeometry.avatar, collapsed!!.profileGeometry?.avatar)
        } finally {
            scope.cancel()
        }
    }

    @Test fun midFlightReversalInterruptsInPlaceInsteadOfRestarting() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val controller = ChatProfileTransitionController(scope)
        try {
            val chatId = 7L
            val source = controller.chatHeaderSource(chatId)
            source.reportAvatar(chatGeometry.avatar, fingerprint)
            assertTrue(controller.beginPush(chatId, visuals(chatId), fingerprint, animated, animated))
            val flight = controller.flight!!
            controller.reportProfileHeader(chatId, fingerprint, profileGeometry)
            assertTrue(flight.ready)

            // A back while the push is still flying reverses the same flight from its position.
            flight.progress = 0.4f
            assertTrue(controller.beginPop(chatId, visuals(chatId), fingerprint, animated))
            assertEquals(0.4f, controller.flight!!.progress, 0.0001f)

            // And opening the profile again flies back to it from wherever the pop reached.
            controller.flight!!.progress = 0.3f
            assertTrue(controller.beginPush(chatId, visuals(chatId), fingerprint, animated, animated))
            assertEquals(0.3f, controller.flight!!.progress, 0.0001f)
        } finally {
            scope.cancel()
        }
    }

    @Test fun aChatSwitchMidFlightAbortsIt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val controller = ChatProfileTransitionController(scope)
        try {
            val chatId = 7L
            val source = controller.chatHeaderSource(chatId)
            source.reportAvatar(chatGeometry.avatar, fingerprint)
            assertTrue(controller.beginPush(chatId, visuals(chatId), fingerprint, animated, animated))
            assertTrue(controller.flight != null)
            // The chat switched under the flight: the landing spot no longer exists.
            controller.chatHeaderSource(8L).reportAvatar(Rect(0f, 0f, 10f, 10f), fingerprint)
            assertNull(controller.flight)
        } finally {
            scope.cancel()
        }
    }

    @Test fun predictiveGestureDrivesTheSameFlightAndNavigatesOnce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val controller = ChatProfileTransitionController(scope)
        try {
            val chatId = 7L
            val source = controller.chatHeaderSource(chatId)
            source.reportAvatar(chatGeometry.avatar, fingerprint)
            val target = controller.profileTarget(chatId)
            target.reportAvatar(profileGeometry.avatar, fingerprint)
            target.reportTitle(profileGeometry.title, fingerprint)

            assertTrue(controller.beginGesturePop(chatId, visuals(chatId), fingerprint, animated))
            val flight = controller.flight!!
            assertEquals(1f, flight.progress, 0f)
            // The gesture writes the progress directly: no timed animation runs behind it.
            controller.updateGesturePop(0.3f)
            assertEquals(0.7f, flight.progress, 0.001f)

            var navigations = 0
            controller.commitGesturePop { navigations++ }
            // Exactly one navigation on commit; the flight keeps its position for the landing.
            assertEquals(1, navigations)
            assertEquals(0.7f, flight.progress, 0.01f)
        } finally {
            scope.cancel()
        }
    }

    @Test fun gesturePopRefusesWithoutGeometrySoPlainBackRuns() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val controller = ChatProfileTransitionController(scope)
        try {
            val chatId = 11L
            val target = controller.profileTarget(chatId)
            target.reportAvatar(profileGeometry.avatar, fingerprint)
            // No chat header geometry: the gesture must not engage a shared flight.
            assertFalse(controller.beginGesturePop(chatId, visuals(chatId), fingerprint, animated))
            assertNull(controller.flight)
        } finally {
            scope.cancel()
        }
    }

    @Test fun targetBackNavigatesExactlyOnceWhetherOrNotTheFlightEngages() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val controller = ChatProfileTransitionController(scope)
        try {
            var navigations = 0
            val orchestration = ChatProfileBackOrchestration(
                visuals = { visuals(7L) },
                fingerprint = { fingerprint },
                popSpec = { animated },
                navigate = { navigations++ }
            )
            val chatId = 7L
            val source = controller.chatHeaderSource(chatId)
            source.reportAvatar(chatGeometry.avatar, fingerprint)
            val target = controller.profileTarget(chatId, orchestration)
            target.reportAvatar(profileGeometry.avatar, fingerprint)

            // The flight engages and navigation still happens exactly once.
            target.back()
            assertEquals(1, navigations)
            assertTrue(controller.flight != null)

            // Without chat geometry the flight refuses and navigation still happens exactly once.
            controller.dispose()
            val bareTarget = controller.profileTarget(9L, orchestration)
            bareTarget.reportAvatar(profileGeometry.avatar, fingerprint)
            bareTarget.back()
            assertEquals(2, navigations)
            assertNull(controller.flight)
        } finally {
            scope.cancel()
        }
    }

    @Test fun sourceOpenNavigatesExactlyOnceWhetherOrNotTheFlightEngages() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val controller = ChatProfileTransitionController(scope)
        try {
            var navigations = 0
            val chatId = 7L
            val orchestration = ChatProfilePushOrchestration(
                visuals = { visuals(chatId) },
                fingerprint = { fingerprint },
                pushSpec = { animated },
                popSpec = { animated },
                navigate = { navigations++ }
            )
            val source = controller.chatHeaderSource(chatId, orchestration)

            // No geometry reported yet: no flight, but navigation happens exactly once.
            source.open()
            assertEquals(1, navigations)
            assertNull(controller.flight)

            // With geometry the flight engages and navigation still happens exactly once.
            source.reportAvatar(chatGeometry.avatar, fingerprint)
            source.open()
            assertEquals(2, navigations)
            assertTrue(controller.flight != null)

            // A topic view never flies, and navigation still happens exactly once.
            controller.dispose()
            val topicView = ChatProfilePushOrchestration(
                visuals = { null },
                fingerprint = { fingerprint },
                pushSpec = { animated },
                popSpec = { animated },
                navigate = { navigations++ }
            )
            controller.chatHeaderSource(chatId, topicView).open()
            assertEquals(3, navigations)
            assertNull(controller.flight)
        } finally {
            scope.cancel()
        }
    }
}
