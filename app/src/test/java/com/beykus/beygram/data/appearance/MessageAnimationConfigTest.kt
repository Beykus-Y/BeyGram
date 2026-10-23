package com.beykus.beygram.data.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageAnimationConfigTest {
    @Test fun defaultsUseOneSharedSmoothEffect() {
        val config = MessageAnimationConfig()
        assertTrue(config.enabled)
        assertTrue(config.linkedDirections)
        assertEquals(MessageAnimationPreset.Fade, config.shared.preset)
        assertEquals(MessageAnimationPreset.Fade, config.sent.preset)
        assertEquals(MessageAnimationPreset.Fade, config.received.preset)
        assertEquals(MessageAnimationConfig(), AppearanceConfig().messageAnimations)
    }

    @Test fun sharedModeSelectsTheSharedDirectionForBothSides() {
        val config = MessageAnimationConfig(
            linkedDirections = true,
            shared = MessageAnimationDirection(MessageAnimationPreset.Drift)
        )
        assertEquals(MessageAnimationPreset.Drift, config.direction(outgoing = true).preset)
        assertEquals(MessageAnimationPreset.Drift, config.direction(outgoing = false).preset)
    }

    @Test fun independentDirectionsSurviveWhileLinkedChoicesChange() {
        val config = MessageAnimationConfig(
            linkedDirections = false,
            shared = MessageAnimationDirection(MessageAnimationPreset.Tilt),
            sent = MessageAnimationDirection(MessageAnimationPreset.Slide),
            received = MessageAnimationDirection(MessageAnimationPreset.Flow)
        )
        val editedShared = config.copy(shared = MessageAnimationDirection(MessageAnimationPreset.Pop))
        assertEquals(MessageAnimationPreset.Slide, editedShared.sent.preset)
        assertEquals(MessageAnimationPreset.Flow, editedShared.received.preset)
        assertEquals(MessageAnimationPreset.Slide, editedShared.direction(outgoing = true).preset)
        assertEquals(MessageAnimationPreset.Flow, editedShared.direction(outgoing = false).preset)
    }

    @Test fun linkingSeedsSharedFromSentAndUnlinkingKeepsBothDirections() {
        val independent = MessageAnimationConfig(
            linkedDirections = false,
            sent = MessageAnimationDirection(MessageAnimationPreset.Sway),
            received = MessageAnimationDirection(MessageAnimationPreset.Quick)
        )
        val linked = independent.withLinkedDirections(true)
        assertTrue(linked.linkedDirections)
        assertEquals(MessageAnimationPreset.Sway, linked.shared.preset)
        assertEquals(MessageAnimationPreset.Sway, linked.sent.preset)
        assertEquals(MessageAnimationPreset.Quick, linked.received.preset)

        val unlinked = linked.withLinkedDirections(false)
        assertFalse(unlinked.linkedDirections)
        assertEquals(MessageAnimationPreset.Sway, unlinked.sent.preset)
        assertEquals(MessageAnimationPreset.Quick, unlinked.received.preset)
    }

    @Test fun composerLaunchIsOutgoingOnlyAndIncomingFallsBackToFlow() {
        val linked = MessageAnimationConfig(
            linkedDirections = true,
            shared = MessageAnimationDirection(
                preset = MessageAnimationPreset.ComposerLaunch,
                tuning = MessageAnimationTuning(motionDirection = MessageAnimationMotionDirection.FromTop)
            )
        )
        assertEquals(MessageAnimationPreset.ComposerLaunch, linked.direction(outgoing = true).preset)
        assertEquals(MessageAnimationPreset.Flow, linked.direction(outgoing = false).preset)
        assertEquals(
            MessageAnimationMotionDirection.Auto,
            linked.direction(outgoing = false).tuning.motionDirection
        )
        assertEquals(MessageAnimationPreset.Fade, linked.validated().received.preset)
        assertTrue(MessageAnimationPreset.ComposerLaunch.supportsIncoming().not())

        val independent = linked.copy(
            linkedDirections = false,
            received = MessageAnimationDirection(
                preset = MessageAnimationPreset.ComposerLaunch,
                tuning = MessageAnimationTuning(motionDirection = MessageAnimationMotionDirection.FromBottom)
            )
        ).validated()
        assertEquals(MessageAnimationPreset.Flow, independent.received.preset)
        assertEquals(MessageAnimationMotionDirection.Auto, independent.received.tuning.motionDirection)
    }

    @Test fun newChoicesAndTuningRoundTripThroughVersionTwoStorage() {
        val config = MessageAnimationConfig(
            linkedDirections = false,
            shared = MessageAnimationDirection(MessageAnimationPreset.Flow),
            sent = MessageAnimationDirection(
                preset = MessageAnimationPreset.Drift,
                tuning = MessageAnimationTuning(
                    speed = 1.7f, intensity = 0.25f, offsetDp = 28, scale = 0.85f,
                    easing = MessageAnimationEasing.Expressive,
                    springiness = 0.65f,
                    motionDirection = MessageAnimationMotionDirection.FromTop
                )
            ),
            received = MessageAnimationDirection(
                preset = MessageAnimationPreset.Pop,
                tuning = MessageAnimationTuning(
                    speed = 0.8f, intensity = 1f, offsetDp = 0, scale = 0.8f,
                    easing = MessageAnimationEasing.Linear,
                    springiness = 0.15f,
                    motionDirection = MessageAnimationMotionDirection.Auto
                )
            )
        )
        val encoded = config.encode()
        assertEquals(27, encoded.split('|').size)
        assertEquals(config, messageAnimationConfigFromStored(encoded))
    }

    @Test fun versionOneStorageRemainsCompatible() {
        val legacySame = "1|Fade|1.200|0.500|10|0.950|Fade|1.200|0.500|10|0.950"
        val same = messageAnimationConfigFromStored(legacySame)
        assertTrue(same.enabled)
        assertTrue(same.linkedDirections)
        assertEquals(MessageAnimationPreset.Fade, same.shared.preset)

        val legacyDifferent = "0|Slide|1.500|0.700|20|0.900|Spring|0.700|1.000|0|0.860"
        val different = messageAnimationConfigFromStored(legacyDifferent)
        assertFalse(different.enabled)
        assertFalse(different.linkedDirections)
        assertEquals(MessageAnimationPreset.Slide, different.sent.preset)
        assertEquals(MessageAnimationPreset.Spring, different.received.preset)
        assertEquals(20, different.sent.tuning.offsetDp)
    }

    @Test fun missingOrDamagedStorageFallsBackToDefaults() {
        assertEquals(MessageAnimationConfig(), messageAnimationConfigFromStored(null))
        assertEquals(MessageAnimationConfig(), messageAnimationConfigFromStored(""))
        assertEquals(MessageAnimationConfig(), messageAnimationConfigFromStored("1|Fade|1"))
        assertEquals(MessageAnimationConfig(), messageAnimationConfigFromStored("2|1|1|Fade"))
        assertEquals(MessageAnimationConfig(), messageAnimationConfigFromStored("not a config"))
    }

    @Test fun invalidPersistedValuesAreClamped() {
        val restored = messageAnimationConfigFromStored(
            "2|1|1|Neon|99|-1|999|2|Unknown|9|Unknown|Slide|0.1|9|-7|0.1|Smooth|5|Auto|" +
                "Fade|1|0.5|0|0.9|Smooth|0.4|Auto"
        )
        assertEquals(MessageAnimationPreset.Fade, restored.shared.preset)
        assertEquals(MAX_MESSAGE_ANIMATION_SPEED, restored.shared.tuning.speed, 0f)
        assertEquals(MIN_MESSAGE_ANIMATION_INTENSITY, restored.shared.tuning.intensity, 0f)
        assertEquals(MAX_MESSAGE_ANIMATION_OFFSET_DP, restored.shared.tuning.offsetDp)
        assertEquals(MAX_MESSAGE_ANIMATION_SCALE, restored.shared.tuning.scale, 0f)
        assertEquals(MessageAnimationEasing.Smooth, restored.shared.tuning.easing)
        assertEquals(MAX_MESSAGE_ANIMATION_SPRINGINESS, restored.shared.tuning.springiness, 0f)
        assertEquals(MessageAnimationMotionDirection.Auto, restored.shared.tuning.motionDirection)
        assertEquals(MessageAnimationPreset.Slide, restored.sent.preset)
        assertEquals(MIN_MESSAGE_ANIMATION_SPEED, restored.sent.tuning.speed, 0f)
        assertEquals(MIN_MESSAGE_ANIMATION_SCALE, restored.sent.tuning.scale, 0f)
    }

    @Test fun nonFiniteValuesUseSafeDefaults() {
        assertEquals(1f, validMessageAnimationSpeed(Float.NaN), 0f)
        assertEquals(1f, validMessageAnimationSpeed(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0.55f, validMessageAnimationIntensity(Float.NaN), 0f)
        assertEquals(0.94f, validMessageAnimationScale(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(0.4f, validMessageAnimationSpringiness(Float.NaN), 0f)
    }

    @Test fun controlsMatchThePresetThatActuallyUsesThem() {
        assertEquals(emptySet<MessageAnimationControl>(), MessageAnimationPreset.None.controls())
        assertEquals(setOf(MessageAnimationControl.Speed), MessageAnimationPreset.ComposerLaunch.controls())
        assertTrue(MessageAnimationControl.Springiness in MessageAnimationPreset.Pop.controls())
        assertFalse(MessageAnimationControl.Springiness in MessageAnimationPreset.Tilt.controls())
        assertTrue(MessageAnimationControl.Direction in MessageAnimationPreset.Drift.controls())
        assertTrue(MessageAnimationControl.Easing in MessageAnimationPreset.Custom.controls())
        assertFalse(MessageAnimationControl.Easing in MessageAnimationPreset.Drift.controls())
    }

    @Test fun resetReturnsExactlyTheShippedDefaults() {
        val changed = MessageAnimationConfig(
            enabled = false,
            linkedDirections = false,
            shared = MessageAnimationDirection(MessageAnimationPreset.Drift),
            sent = MessageAnimationDirection(MessageAnimationPreset.Custom, MessageAnimationTuning(2f, 0f, 32, 0.8f)),
            received = MessageAnimationDirection(MessageAnimationPreset.None, MessageAnimationTuning(0.5f, 1f, 0, 1f))
        )
        assertFalse(changed == MessageAnimationConfig())
        assertEquals(MessageAnimationConfig(), MessageAnimationConfig())
    }
}
