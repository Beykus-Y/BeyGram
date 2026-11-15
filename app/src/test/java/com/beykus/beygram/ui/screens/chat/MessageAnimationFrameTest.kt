package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.data.appearance.MessageAnimationTuning
import com.beykus.beygram.ui.accessibility.LocalSystemReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

private const val ROW_TAG = "message-animation-row"
private const val CHILD_TAG = "message-animation-child"

/**
 * Verifies the Compose wiring of [MessageAnimationFrame]: layout-direction-aware [TransformOrigin],
 * horizontal [sideInset] anchoring, centered chip origin, and cancellation via [SideEffect] without replay.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class MessageAnimationFrameTest {
    @get:Rule
    val compose = createComposeRule()

    private fun scaleOnlyConfig(): MessageAnimationConfig {
        val direction = MessageAnimationDirection(
            preset = MessageAnimationPreset.Fade,
            tuning = MessageAnimationTuning(intensity = 1f, scale = 0.8f)
        )
        return MessageAnimationConfig(
            enabled = true,
            linkedDirections = false,
            sent = direction,
            received = direction
        )
    }

    private fun setAnimatedRow(
        animate: Boolean,
        outgoing: Boolean,
        layoutDirection: LayoutDirection,
        centered: Boolean = false,
        sideInset: Dp = 0.dp,
        reducedMotion: Boolean = false,
        config: MessageAnimationConfig = scaleOnlyConfig()
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides layoutDirection,
                LocalSystemReducedMotion provides reducedMotion
            ) {
                Box(Modifier.width(300.dp).testTag(ROW_TAG)) {
                    MessageAnimationFrame(
                        animate = animate,
                        outgoing = outgoing,
                        config = config,
                        centered = centered,
                        sideInset = sideInset
                    ) {
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = sideInset)
                        ) {
                            val alignment = when {
                                centered -> Alignment.Center
                                outgoing -> Alignment.BottomEnd
                                else -> Alignment.BottomStart
                            }
                            Box(
                                Modifier.align(alignment)
                                    .size(100.dp, 40.dp)
                                    .testTag(CHILD_TAG)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun outgoingAnchorsToBottomRightInLtr() {
        assertAnchoredCorner(outgoing = true, layoutDirection = LayoutDirection.Ltr, expectRightPinned = true)
    }

    @Test
    fun incomingAnchorsToBottomLeftInLtr() {
        assertAnchoredCorner(outgoing = false, layoutDirection = LayoutDirection.Ltr, expectRightPinned = false)
    }

    @Test
    fun outgoingFlipsToBottomLeftInRtl() {
        assertAnchoredCorner(outgoing = true, layoutDirection = LayoutDirection.Rtl, expectRightPinned = false)
    }

    @Test
    fun incomingFlipsToBottomRightInRtl() {
        assertAnchoredCorner(outgoing = false, layoutDirection = LayoutDirection.Rtl, expectRightPinned = true)
    }

    private fun assertAnchoredCorner(
        outgoing: Boolean,
        layoutDirection: LayoutDirection,
        expectRightPinned: Boolean
    ) {
        compose.mainClock.autoAdvance = false
        setAnimatedRow(animate = true, outgoing = outgoing, layoutDirection = layoutDirection)
        val row = bounds(ROW_TAG)
        val child = bounds(CHILD_TAG)
        val unscaledWidthPx = with(compose.density) { 100.dp.toPx() }
        assertEquals(unscaledWidthPx * 0.8f, child.width, 1.5f)
        if (expectRightPinned) {
            assertEquals(row.right, child.right, 1f)
        } else {
            assertEquals(row.left, child.left, 1f)
        }
        assertEquals(row.bottom, child.bottom, 1f)
    }

    @Test
    fun sideInsetAnchorsTransformToBubbleEdgeRatherThanOuterRowEdge() {
        compose.mainClock.autoAdvance = false
        val inset = 14.dp
        val insetPx = with(compose.density) { inset.toPx() }
        setAnimatedRow(
            animate = true,
            outgoing = true,
            layoutDirection = LayoutDirection.Ltr,
            sideInset = inset
        )
        val row = bounds(ROW_TAG)
        val child = bounds(CHILD_TAG)
        // With scale = 0.8f, the bubble's outer edge must remain pinned at row.right - insetPx.
        assertEquals(row.right - insetPx, child.right, 1f)
        assertEquals(row.bottom, child.bottom, 1f)
    }

    @Test
    fun centeredRowScalesSymmetricallyAroundCenter() {
        compose.mainClock.autoAdvance = false
        setAnimatedRow(
            animate = true,
            outgoing = true,
            layoutDirection = LayoutDirection.Ltr,
            centered = true
        )
        val row = bounds(ROW_TAG)
        val child = bounds(CHILD_TAG)
        val unscaledWidthPx = with(compose.density) { 100.dp.toPx() }
        assertEquals(unscaledWidthPx * 0.8f, child.width, 1.5f)
        assertEquals(row.center.x, child.center.x, 1f)
        assertEquals(row.center.y, child.center.y, 1f)
        assertTrue(abs(child.right - row.right) > 10f)
        assertTrue(abs(child.left - row.left) > 10f)
    }

    @Test
    fun cancellingMidFlightViaReducedMotionClearsRetainedPlanViaSideEffectWithoutReplay() {
        compose.mainClock.autoAdvance = false
        var reducedMotion by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Ltr,
                LocalSystemReducedMotion provides reducedMotion
            ) {
                Box(Modifier.width(300.dp).testTag(ROW_TAG)) {
                    MessageAnimationFrame(
                        animate = true,
                        outgoing = true,
                        config = scaleOnlyConfig()
                    ) {
                        Box(
                            Modifier.fillMaxWidth()
                        ) {
                            Box(
                                Modifier.align(Alignment.BottomEnd)
                                    .size(100.dp, 40.dp)
                                    .testTag(CHILD_TAG)
                            )
                        }
                    }
                }
            }
        }

        val unscaledWidthPx = with(compose.density) { 100.dp.toPx() }
        // Advance one frame: entrance is in-flight and scaled down.
        compose.mainClock.advanceTimeByFrame()
        assertTrue(bounds(CHILD_TAG).width < unscaledWidthPx * 0.95f)

        // Turning on reduced motion drops requestedPlan -> SideEffect clears retainedPlan.
        reducedMotion = true
        compose.mainClock.advanceTimeByFrame()
        assertEquals(unscaledWidthPx, bounds(CHILD_TAG).width, 1f)

        // Restoring motion must NOT replay the cancelled entrance.
        reducedMotion = false
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            assertEquals(unscaledWidthPx, bounds(CHILD_TAG).width, 1f)
        }
    }

    @Test
    fun withdrawingArrivalMidFlightClearsRetainedPlanWithoutReplay() {
        compose.mainClock.autoAdvance = false
        var animateArrival by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Ltr,
                LocalSystemReducedMotion provides false
            ) {
                Box(Modifier.width(300.dp).testTag(ROW_TAG)) {
                    MessageAnimationFrame(
                        animate = animateArrival,
                        outgoing = false,
                        config = scaleOnlyConfig()
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier.align(Alignment.BottomStart)
                                    .size(100.dp, 40.dp)
                                    .testTag(CHILD_TAG)
                            )
                        }
                    }
                }
            }
        }

        val unscaledWidthPx = with(compose.density) { 100.dp.toPx() }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(bounds(CHILD_TAG).width < unscaledWidthPx * 0.95f)

        animateArrival = false
        compose.mainClock.advanceTimeByFrame()
        assertEquals(unscaledWidthPx, bounds(CHILD_TAG).width, 1f)

        animateArrival = true
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            assertEquals(unscaledWidthPx, bounds(CHILD_TAG).width, 1f)
        }
    }
}
