package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationDirection
import com.beykus.beygram.data.appearance.MessageAnimationEasing
import com.beykus.beygram.data.appearance.MessageAnimationMotionDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.data.appearance.MessageAnimationTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageAnimationPlanTest {
    private fun direction(preset: MessageAnimationPreset, tuning: MessageAnimationTuning = MessageAnimationTuning()) =
        MessageAnimationDirection(preset, tuning)

    @Test fun noneHasNoPlan() {
        assertNull(messageAnimationPlan(direction(MessageAnimationPreset.None), outgoing = true))
    }

    @Test fun disabledOrAlreadyShownMessagesHaveNoPlan() {
        val config = MessageAnimationConfig(sent = direction(MessageAnimationPreset.Slide))
        assertNull(messageAnimationPlan(config.copy(enabled = false), animate = true, outgoing = true))
        assertNull(messageAnimationPlan(config, animate = false, outgoing = true))
        assertNotNull(messageAnimationPlan(config, animate = true, outgoing = true))
    }

    @Test fun fadeStartsTransparentAndSlightlySmaller() {
        val plan = messageAnimationPlan(
            direction(MessageAnimationPreset.Fade, MessageAnimationTuning(intensity = 0.5f, scale = 0.9f)),
            outgoing = false
        )!!
        assertEquals(0.55f, plan.alphaFrom, 0.0001f)
        assertEquals(0.95f, plan.scaleFrom, 0.0001f)
        assertEquals(0f, plan.offsetXDp, 0f)
    }

    @Test fun slideMovesSentFromTheRightAndReceivedFromTheLeft() {
        val tuning = MessageAnimationTuning(intensity = 0.5f, offsetDp = 20)
        val sent = messageAnimationPlan(direction(MessageAnimationPreset.Slide, tuning), outgoing = true)!!
        val received = messageAnimationPlan(direction(MessageAnimationPreset.Slide, tuning), outgoing = false)!!
        assertEquals(10f, sent.offsetXDp, 0f)
        assertEquals(-10f, received.offsetXDp, 0f)
        assertEquals(1f, sent.scaleFrom, 0f)
    }

    @Test fun directionOverrideReplacesTheMirroredSide() {
        val tuning = MessageAnimationTuning(intensity = 1f, offsetDp = 20, motionDirection = MessageAnimationMotionDirection.FromBottom)
        val plan = messageAnimationPlan(direction(MessageAnimationPreset.Slide, tuning), outgoing = true)!!
        assertEquals(0f, plan.offsetXDp, 0f)
        assertEquals(20f, plan.offsetYDp, 0f)
    }

    @Test fun springUsesTheConfiguredSpringinessAndKeepsOldDefaultDamping() {
        val defaultPlan = messageAnimationPlan(direction(MessageAnimationPreset.Spring), outgoing = true)!!
        assertEquals(0.82f, defaultPlan.springDampingRatio ?: 0f, 0.0001f)

        val bouncy = messageAnimationPlan(
            direction(MessageAnimationPreset.Spring, MessageAnimationTuning(springiness = 1f)), outgoing = true
        )!!
        val firm = messageAnimationPlan(
            direction(MessageAnimationPreset.Spring, MessageAnimationTuning(springiness = 0f)), outgoing = true
        )!!
        assertTrue((bouncy.springDampingRatio ?: 0f) < (firm.springDampingRatio ?: 0f))
    }

    @Test fun popStartsSmallerThanSpringAndOvershootsGently() {
        val tuning = MessageAnimationTuning(scale = 1f, intensity = 1f)
        val pop = messageAnimationPlan(direction(MessageAnimationPreset.Pop, tuning), outgoing = true)!!
        val spring = messageAnimationPlan(direction(MessageAnimationPreset.Spring, tuning), outgoing = true)!!
        assertTrue(pop.scaleFrom < spring.scaleFrom)
        assertTrue((pop.springDampingRatio ?: 0f) > 0.55f)
        assertTrue((pop.springDampingRatio ?: 1f) < 0.9f)
    }

    @Test fun flowCombinesMirroredHorizontalAndUpwardMotion() {
        val tuning = MessageAnimationTuning(intensity = 1f, offsetDp = 20)
        val sent = messageAnimationPlan(direction(MessageAnimationPreset.Flow, tuning), outgoing = true)!!
        val received = messageAnimationPlan(direction(MessageAnimationPreset.Flow, tuning), outgoing = false)!!
        assertEquals(20f, sent.offsetXDp, 0f)
        assertEquals(-20f, received.offsetXDp, 0f)
        assertTrue(sent.offsetYDp < 0f)
        assertEquals(sent.offsetYDp, received.offsetYDp, 0f)
    }

    @Test fun driftMovesDiagonallyWithStrongDeceleration() {
        val plan = messageAnimationPlan(
            direction(MessageAnimationPreset.Drift, MessageAnimationTuning(intensity = 1f, offsetDp = 20)),
            outgoing = true
        )!!
        assertEquals(26f, plan.offsetXDp, 0f)
        assertEquals(-17f, plan.offsetYDp, 0f)
        assertEquals(MessageAnimationEasing.EaseOut, plan.easing)
    }

    @Test fun quickIsShorterAndSubtlerThanFade() {
        val quick = messageAnimationPlan(direction(MessageAnimationPreset.Quick), outgoing = true)!!
        val fade = messageAnimationPlan(direction(MessageAnimationPreset.Fade), outgoing = true)!!
        assertTrue(quick.durationMillis < fade.durationMillis)
        assertTrue(quick.alphaFrom > fade.alphaFrom)
    }

    @Test fun tiltAndSwayMirrorTheirRotations() {
        val tiltOut = messageAnimationPlan(direction(MessageAnimationPreset.Tilt), outgoing = true)!!
        val tiltIn = messageAnimationPlan(direction(MessageAnimationPreset.Tilt), outgoing = false)!!
        val swayOut = messageAnimationPlan(direction(MessageAnimationPreset.Sway), outgoing = true)!!
        val swayIn = messageAnimationPlan(direction(MessageAnimationPreset.Sway), outgoing = false)!!
        assertTrue(tiltOut.rotationYDegrees > 0f)
        assertTrue(tiltIn.rotationYDegrees < 0f)
        assertTrue(swayOut.rotationZDegrees > 0f)
        assertTrue(swayIn.rotationZDegrees < 0f)
    }

    @Test fun composerLaunchFallsBackToFlowForTheRow() {
        val config = MessageAnimationConfig(
            linkedDirections = false,
            sent = direction(MessageAnimationPreset.ComposerLaunch),
            received = direction(MessageAnimationPreset.Slide)
        )
        val plan = messageAnimationPlan(config, animate = true, outgoing = true)!!
        assertEquals(-2.31f, plan.offsetYDp, 0.0001f)
        // Incoming is not touched by the outgoing-only preset.
        assertEquals(-6.6f, messageAnimationPlan(config, animate = true, outgoing = false)!!.offsetXDp, 0.0001f)
    }

    @Test fun customCombinesScaleOffsetAndFade() {
        val plan = messageAnimationPlan(
            direction(MessageAnimationPreset.Custom, MessageAnimationTuning(intensity = 1f, offsetDp = 8, scale = 0.8f)),
            outgoing = false
        )!!
        assertEquals(0.1f, plan.alphaFrom, 0.0001f)
        assertEquals(0.8f, plan.scaleFrom, 0.0001f)
        assertEquals(-8f, plan.offsetXDp, 0f)
    }

    @Test fun speedShortensTheEntranceAndIsBounded() {
        val slow = messageAnimationPlan(direction(MessageAnimationPreset.Fade, MessageAnimationTuning(speed = 1f)), outgoing = true)!!
        val fast = messageAnimationPlan(direction(MessageAnimationPreset.Fade, MessageAnimationTuning(speed = 2f)), outgoing = true)!!
        assertTrue(fast.durationMillis < slow.durationMillis)
        val extreme = messageAnimationPlan(direction(MessageAnimationPreset.Fade, MessageAnimationTuning(speed = 20f)), outgoing = true)!!
        assertEquals(110, extreme.durationMillis)
    }
}
