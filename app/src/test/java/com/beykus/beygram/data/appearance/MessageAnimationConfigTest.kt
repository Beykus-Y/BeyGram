package com.beykus.beygram.data.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageAnimationConfigTest {
    @Test fun defaultsEnableSmoothIndependentDirections() {
        val config = MessageAnimationConfig()
        assertTrue(config.enabled)
        assertEquals(MessageAnimationPreset.Fade, config.sent.preset)
        assertEquals(MessageAnimationPreset.Fade, config.received.preset)
        assertEquals(MessageAnimationConfig(), AppearanceConfig().messageAnimations)
    }

    @Test fun sentAndReceivedPresetsAndTuningAreIndependentAndSurviveStorage() {
        val config = MessageAnimationConfig(
            enabled = true,
            sent = MessageAnimationDirection(
                preset = MessageAnimationPreset.Slide,
                tuning = MessageAnimationTuning(speed = 1.4f, intensity = 0.2f, offsetDp = 18, scale = 1f)
            ),
            received = MessageAnimationDirection(
                preset = MessageAnimationPreset.Spring,
                tuning = MessageAnimationTuning(speed = 0.7f, intensity = 1f, offsetDp = 0, scale = 0.86f)
            )
        )
        val restored = messageAnimationConfigFromStored(config.encode())
        assertEquals(config, restored)
        assertEquals(MessageAnimationPreset.Slide, restored.sent.preset)
        assertEquals(MessageAnimationPreset.Spring, restored.received.preset)
        assertEquals(18, restored.sent.tuning.offsetDp)
        assertEquals(0.86f, restored.received.tuning.scale, 0.0001f)
    }

    @Test fun disabledChoiceSurvivesStorage() {
        val restored = messageAnimationConfigFromStored(MessageAnimationConfig(enabled = false).encode())
        assertFalse(restored.enabled)
    }

    @Test fun missingOrDamagedStorageFallsBackToDefaults() {
        assertEquals(MessageAnimationConfig(), messageAnimationConfigFromStored(null))
        assertEquals(MessageAnimationConfig(), messageAnimationConfigFromStored(""))
        assertEquals(MessageAnimationConfig(), messageAnimationConfigFromStored("1|Fade|1"))
        assertEquals(MessageAnimationConfig(), messageAnimationConfigFromStored("not a config"))
    }

    @Test fun invalidPersistedValuesAreClampedAndUnknownPresetFallsBackToFade() {
        val restored = messageAnimationConfigFromStored("1|Neon|99|-1|999|2|Slide|0.1|9|-7|0.1")
        assertEquals(MessageAnimationPreset.Fade, restored.sent.preset)
        assertEquals(MAX_MESSAGE_ANIMATION_SPEED, restored.sent.tuning.speed, 0f)
        assertEquals(MIN_MESSAGE_ANIMATION_INTENSITY, restored.sent.tuning.intensity, 0f)
        assertEquals(MAX_MESSAGE_ANIMATION_OFFSET_DP, restored.sent.tuning.offsetDp)
        assertEquals(MAX_MESSAGE_ANIMATION_SCALE, restored.sent.tuning.scale, 0f)
        assertEquals(MessageAnimationPreset.Slide, restored.received.preset)
        assertEquals(MIN_MESSAGE_ANIMATION_SPEED, restored.received.tuning.speed, 0f)
        assertEquals(MAX_MESSAGE_ANIMATION_INTENSITY, restored.received.tuning.intensity, 0f)
        assertEquals(MIN_MESSAGE_ANIMATION_OFFSET_DP, restored.received.tuning.offsetDp)
        assertEquals(MIN_MESSAGE_ANIMATION_SCALE, restored.received.tuning.scale, 0f)
    }

    @Test fun nonFiniteValuesUseSafeDefaults() {
        assertEquals(1f, validMessageAnimationSpeed(Float.NaN), 0f)
        assertEquals(1f, validMessageAnimationSpeed(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0.55f, validMessageAnimationIntensity(Float.NaN), 0f)
        assertEquals(0.94f, validMessageAnimationScale(Float.NEGATIVE_INFINITY), 0f)
    }

    @Test fun resetReturnsExactlyTheShippedDefaults() {
        val changed = MessageAnimationConfig(
            enabled = false,
            sent = MessageAnimationDirection(MessageAnimationPreset.Custom, MessageAnimationTuning(2f, 0f, 24, 0.8f)),
            received = MessageAnimationDirection(MessageAnimationPreset.None, MessageAnimationTuning(0.5f, 1f, 0, 1f))
        )
        assertFalse(changed == MessageAnimationConfig())
        assertEquals(MessageAnimationConfig(), MessageAnimationConfig())
    }
}
