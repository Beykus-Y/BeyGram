package com.beykus.beygram.ui.screens.theme

import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.BubbleStyle
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.ListDensity
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationDirection
import com.beykus.beygram.data.appearance.MessageAnimationEasing
import com.beykus.beygram.data.appearance.MessageAnimationMotionDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.data.appearance.MessageAnimationTuning
import com.beykus.beygram.data.appearance.MessageLineSpacing
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceDraftLogicTest {
    private fun changedAppearance() = AppearanceConfig(
        preset = ThemePreset.Graphite,
        mode = ThemeMode.Amoled,
        bubbleStyle = BubbleStyle.Square,
        density = ListDensity.Spacious,
        accentColor = 0xFF3B5BDB.toInt(),
        outgoingBubbleColor = 0xFFDCE4F8.toInt(),
        wallpaper = ChatWallpaper.Preset("mint-air", 35),
        messageTextSizeSp = 21,
        bubbleCornerRadiusDp = 7,
        messageLineSpacing = MessageLineSpacing.Relaxed,
        messageTextColor = 0xFF1A1714.toInt(),
        messageTimeColor = 0xFF4B33B5.toInt(),
        messageAnimations = MessageAnimationConfig(
            enabled = false,
            linkedDirections = false,
            sent = MessageAnimationDirection(MessageAnimationPreset.Drift),
            received = MessageAnimationDirection(MessageAnimationPreset.Sway)
        )
    )

    @Test fun applyIsEnabledOnlyForRealChangesAndNoContrastBlocker() {
        val saved = AppearanceConfig()
        assertFalse(appearanceApplyState(saved, saved, blockedByContrast = false).canApply)
        assertTrue(appearanceApplyState(changedAppearance(), saved, blockedByContrast = false).canApply)
        assertFalse(appearanceApplyState(changedAppearance(), saved, blockedByContrast = true).canApply)
        assertTrue(appearanceApplyState(changedAppearance(), saved, blockedByContrast = true).blockedByContrast)
    }

    @Test fun messagesResetKeepsPaletteWallpaperAnimationsAndCustomColours() {
        val changed = changedAppearance()
        val reset = changed.resetMessages()
        assertEquals(ThemePreset.Graphite, reset.preset)
        assertEquals(ThemeMode.Amoled, reset.mode)
        assertEquals(changed.wallpaper, reset.wallpaper)
        assertEquals(changed.accentColor, reset.accentColor)
        assertEquals(changed.messageAnimations, reset.messageAnimations)
        assertEquals(BubbleStyle.Rounded, reset.bubbleStyle)
        assertEquals(ListDensity.Comfortable, reset.density)
        assertEquals(16, reset.messageTextSizeSp)
        assertNull(reset.bubbleCornerRadiusDp)
        assertEquals(MessageLineSpacing.Normal, reset.messageLineSpacing)
        assertEquals(changed.messageTextColor, reset.messageTextColor)
        assertEquals(changed.messageTimeColor, reset.messageTimeColor)
    }

    @Test fun customColoursResetDoesNotTouchGeometryOrAnimations() {
        val changed = changedAppearance()
        val reset = changed.resetCustomColors()
        assertNull(reset.accentColor)
        assertNull(reset.outgoingBubbleColor)
        assertNull(reset.messageTextColor)
        assertNull(reset.messageTimeColor)
        assertEquals(changed.bubbleStyle, reset.bubbleStyle)
        assertEquals(changed.messageTextSizeSp, reset.messageTextSizeSp)
        assertEquals(changed.messageAnimations, reset.messageAnimations)
    }

    @Test fun animationsResetKeepsTheRestOfTheAppearance() {
        val changed = changedAppearance()
        val reset = changed.resetAnimations()
        assertEquals(MessageAnimationConfig(), reset.messageAnimations)
        assertEquals(changed.preset, reset.preset)
        assertEquals(changed.wallpaper, reset.wallpaper)
        assertEquals(changed.messageTextSizeSp, reset.messageTextSizeSp)
    }

    @Test fun resetAllReturnsTheShippedDefaults() {
        assertEquals(AppearanceConfig(), changedAppearance().resetAllAppearance())
    }

    @Test fun undoCoalescerSeparatesEveryDiscreteEditAndEverySliderGesture() {
        val coalescer = AppearanceDraftUndoCoalescer()
        assertTrue(coalescer.shouldCheckpoint(continuous = false))
        assertTrue(coalescer.shouldCheckpoint(continuous = false))
        assertTrue(coalescer.shouldCheckpoint(continuous = true))
        assertFalse(coalescer.shouldCheckpoint(continuous = true))
        coalescer.endGesture()
        assertTrue(coalescer.shouldCheckpoint(continuous = true))
        coalescer.endGesture()
        assertTrue(coalescer.shouldCheckpoint(continuous = false))
        assertTrue(coalescer.shouldCheckpoint(continuous = true))
        assertFalse(coalescer.shouldCheckpoint(continuous = true))
        coalescer.reset()
        assertTrue(coalescer.shouldCheckpoint(continuous = true))
    }

    @Test fun backFromSubpageReturnsToOverviewAndPreviewReturnsToItsOrigin() {
        assertEquals(AppearancePage.Overview, appearanceBackTarget(AppearancePage.Messages, AppearancePage.Overview))
        assertEquals(AppearancePage.Overview, appearanceBackTarget(AppearancePage.CustomColors, AppearancePage.Overview))
        assertEquals(AppearancePage.Animations, appearanceBackTarget(AppearancePage.Preview, AppearancePage.Animations))
        assertNull(appearanceBackTarget(AppearancePage.Overview, AppearancePage.Animations))
    }

    @Test fun everyPresetCanBeSelectedForBothSupportedDirections() {
        var config = MessageAnimationConfig(linkedDirections = false)
        MessageAnimationPreset.entries.forEach { preset ->
            config = config.withDirection(
                outgoing = true,
                config.direction(outgoing = true).copy(
                    preset = preset,
                    tuning = MessageAnimationTuning(speed = 1.3f)
                )
            )
            assertEquals(preset, config.direction(outgoing = true).preset)
            assertEquals(1.3f, config.direction(outgoing = true).tuning.speed, 0f)
            if (preset != MessageAnimationPreset.ComposerLaunch) {
                config = config.withDirection(
                    outgoing = false,
                    config.direction(outgoing = false).copy(
                        preset = preset,
                        tuning = MessageAnimationTuning(easing = MessageAnimationEasing.Expressive)
                    )
                )
                assertEquals(preset, config.direction(outgoing = false).preset)
                assertEquals(MessageAnimationEasing.Expressive, config.direction(outgoing = false).tuning.easing)
            }
        }
        assertEquals(MessageAnimationConfig().enabled, config.enabled)
    }

    @Test fun selectingAComposerLaunchForReceivedFallsBackToFlowWithoutBreakingSent() {
        val sent = MessageAnimationConfig(linkedDirections = false).withDirection(
            outgoing = true,
            MessageAnimationDirection(MessageAnimationPreset.ComposerLaunch)
        )
        val config = sent.withDirection(
            outgoing = false,
            MessageAnimationDirection(
                preset = MessageAnimationPreset.ComposerLaunch,
                tuning = MessageAnimationTuning(motionDirection = MessageAnimationMotionDirection.FromTop)
            )
        )
        assertEquals(MessageAnimationPreset.ComposerLaunch, config.direction(outgoing = true).preset)
        assertEquals(MessageAnimationPreset.Flow, config.direction(outgoing = false).preset)
        assertEquals(MessageAnimationMotionDirection.Auto, config.direction(outgoing = false).tuning.motionDirection)
    }
}
