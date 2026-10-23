package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationDirection
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
        assertEquals(0f, plan.offsetDp, 0f)
    }

    @Test fun slideMovesSentFromTheRightAndReceivedFromTheLeft() {
        val tuning = MessageAnimationTuning(intensity = 0.5f, offsetDp = 20)
        val sent = messageAnimationPlan(direction(MessageAnimationPreset.Slide, tuning), outgoing = true)!!
        val received = messageAnimationPlan(direction(MessageAnimationPreset.Slide, tuning), outgoing = false)!!
        assertEquals(10f, sent.offsetDp, 0f)
        assertEquals(-10f, received.offsetDp, 0f)
        assertEquals(1f, sent.scaleFrom, 0f)
    }

    @Test fun springHasControlledDampingAndScale() {
        val plan = messageAnimationPlan(
            direction(MessageAnimationPreset.Spring, MessageAnimationTuning(speed = 1.5f, intensity = 1f, scale = 0.9f)),
            outgoing = true
        )!!
        assertEquals(0.9f, plan.scaleFrom, 0.0001f)
        assertTrue((plan.springDampingRatio ?: 0f) > 0.7f)
        assertTrue((plan.springDampingRatio ?: 1f) < 0.9f)
        assertEquals(630f, plan.springStiffness, 0.1f)
    }

    @Test fun customCombinesScaleOffsetAndFade() {
        val plan = messageAnimationPlan(
            direction(MessageAnimationPreset.Custom, MessageAnimationTuning(intensity = 1f, offsetDp = 8, scale = 0.8f)),
            outgoing = false
        )!!
        assertEquals(0.1f, plan.alphaFrom, 0.0001f)
        assertEquals(0.8f, plan.scaleFrom, 0.0001f)
        assertEquals(-8f, plan.offsetDp, 0f)
    }

    @Test fun speedShortensTheEntranceAndIsBounded() {
        val slow = messageAnimationPlan(direction(MessageAnimationPreset.Fade, MessageAnimationTuning(speed = 1f)), outgoing = true)!!
        val fast = messageAnimationPlan(direction(MessageAnimationPreset.Fade, MessageAnimationTuning(speed = 2f)), outgoing = true)!!
        assertTrue(fast.durationMillis < slow.durationMillis)
        val extreme = messageAnimationPlan(direction(MessageAnimationPreset.Fade, MessageAnimationTuning(speed = 20f)), outgoing = true)!!
        assertEquals(110, extreme.durationMillis)
    }
}
