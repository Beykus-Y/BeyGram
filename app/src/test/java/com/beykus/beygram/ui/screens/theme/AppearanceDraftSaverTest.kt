package com.beykus.beygram.ui.screens.theme

import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.BubbleStyle
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.appearance.ListDensity
import com.beykus.beygram.data.appearance.encode
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationDirection
import com.beykus.beygram.data.appearance.MessageAnimationEasing
import com.beykus.beygram.data.appearance.MessageAnimationMotionDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.data.appearance.MessageAnimationTuning
import com.beykus.beygram.data.appearance.MessageLineSpacing
import com.beykus.beygram.data.appearance.NavigationPlacement
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceDraftSaverTest {
    private fun completeDraft() = AppearanceConfig(
        preset = ThemePreset.MaterialYou,
        mode = ThemeMode.Dark,
        bubbleStyle = BubbleStyle.Soft,
        density = ListDensity.Spacious,
        accentColor = 0xFF0F9D8A.toInt(),
        outgoingBubbleColor = 0xFFD5F2E6.toInt(),
        wallpaper = ChatWallpaper.Photo("trial-photo.jpg", 47, blurred = true),
        messageTextSizeSp = 19,
        bubbleCornerRadiusDp = 18,
        messageLineSpacing = MessageLineSpacing.Relaxed,
        messageTextColor = 0xFFFFFFFF.toInt(),
        messageTimeColor = 0xFFC9C2BB.toInt(),
        messageAnimations = MessageAnimationConfig(
            enabled = false,
            linkedDirections = false,
            shared = MessageAnimationDirection(MessageAnimationPreset.Flow),
            sent = MessageAnimationDirection(
                MessageAnimationPreset.ComposerLaunch,
                MessageAnimationTuning(speed = 1.7f)
            ),
            received = MessageAnimationDirection(
                MessageAnimationPreset.Drift,
                MessageAnimationTuning(
                    speed = 0.7f,
                    intensity = 0.8f,
                    offsetDp = 29,
                    scale = 0.84f,
                    easing = MessageAnimationEasing.Expressive,
                    springiness = 0.75f,
                    motionDirection = MessageAnimationMotionDirection.FromEnd
                )
            )
        )
    )

    @Test fun noDraftIsStoredAsAnEmptyListAndRestoresAsNull() {
        assertTrue(encodeAppearanceDraft(null).isEmpty())
        assertNull(decodeAppearanceDraft(emptyList()))
    }

    @Test fun completeDraftSurvivesActivityRecreationExactly() {
        val draft = completeDraft()
        val restored = decodeAppearanceDraft(encodeAppearanceDraft(draft))
        assertEquals(draft, restored)
        assertEquals(17, encodeAppearanceDraft(draft).size)
    }

    @Test fun designStyleAndGlassQualitySurviveActivityRecreation() {
        val draft = completeDraft().copy(
            designStyle = DesignStyle.LiquidGlass,
            glassQuality = GlassQuality.Balanced
        )
        val restored = decodeAppearanceDraft(encodeAppearanceDraft(draft))
        assertEquals(DesignStyle.LiquidGlass, restored?.designStyle)
        assertEquals(GlassQuality.Balanced, restored?.glassQuality)
    }

    @Test fun navigationPlacementSurvivesActivityRecreation() {
        val draft = completeDraft().copy(navigationPlacement = NavigationPlacement.Pinned)
        val restored = decodeAppearanceDraft(encodeAppearanceDraft(draft))
        assertEquals(NavigationPlacement.Pinned, restored?.navigationPlacement)

        // A forced floating bar is a real choice too and must not collapse back to Auto.
        val floating = decodeAppearanceDraft(
            encodeAppearanceDraft(completeDraft().copy(navigationPlacement = NavigationPlacement.Floating))
        )
        assertEquals(NavigationPlacement.Floating, floating?.navigationPlacement)
    }

    @Test fun draftSavedBeforeThePlacementExistedKeepsTheStylePlacement() {
        // A version-3 draft: the design style and the glass quality, but no bar placement.
        val legacy = encodeAppearanceDraft(
            completeDraft().copy(designStyle = DesignStyle.Signature, glassQuality = GlassQuality.High)
        ).toMutableList()
        legacy.removeAt(15)
        val restored = decodeAppearanceDraft(legacy)
        assertEquals(DesignStyle.Signature, restored?.designStyle)
        assertEquals(GlassQuality.High, restored?.glassQuality)
        assertEquals(NavigationPlacement.Auto, restored?.navigationPlacement)
    }

    @Test fun unknownPlacementFallsBackToFollowTheStyle() {
        val parts = encodeAppearanceDraft(completeDraft()).toMutableList()
        parts[15] = "DockFromTheFuture"
        assertEquals(NavigationPlacement.Auto, decodeAppearanceDraft(parts)?.navigationPlacement)
    }

    @Test fun draftSavedBeforeTheDesignStyleExistedStillRestores() {
        // A version-2 draft: thirteen values plus the trailing version, without the design style and glass quality.
        val legacy = listOf(
            "Warm", "Dark", "Rounded", "Comfortable", "", "", "preset:lavender-flow:40",
            "16", "", "Normal", "", "", MessageAnimationConfig().encode(), "2"
        )
        val restored = decodeAppearanceDraft(legacy)
        assertEquals(DesignStyle.Default, restored?.designStyle)
        assertEquals(GlassQuality.Auto, restored?.glassQuality)
        assertEquals(NavigationPlacement.Auto, restored?.navigationPlacement)
    }

    @Test fun minimalLegacyDraftStillRestoresWithCurrentDefaults() {
        val legacy = listOf("Graphite", "Amoled", "Square", "Compact", "", "", "solid:ff112233")
        val restored = decodeAppearanceDraft(legacy)
        assertEquals(ThemePreset.Graphite, restored?.preset)
        assertEquals(ThemeMode.Amoled, restored?.mode)
        assertEquals(BubbleStyle.Square, restored?.bubbleStyle)
        assertEquals(ListDensity.Compact, restored?.density)
        assertEquals(ChatWallpaper.Solid(0xFF112233.toInt()), restored?.wallpaper)
        assertEquals(16, restored?.messageTextSizeSp)
        assertNull(restored?.bubbleCornerRadiusDp)
        assertEquals(MessageAnimationConfig(), restored?.messageAnimations)
    }

    @Test fun damagedDraftNeverLeaksInvalidGeometryIntoCompose() {
        val restored = decodeAppearanceDraft(
            listOf(
                "Unknown", "Unknown", "Unknown", "Unknown", "not-a-colour", "not-a-colour",
                "preset:unknown:99", "999", "-100", "Unknown", "x", "y", "broken", "99"
            )
        )
        assertEquals(ThemePreset.Warm, restored?.preset)
        assertEquals(ThemeMode.FollowSystem, restored?.mode)
        assertEquals(BubbleStyle.Rounded, restored?.bubbleStyle)
        assertEquals(ListDensity.Comfortable, restored?.density)
        assertNull(restored?.accentColor)
        assertNull(restored?.outgoingBubbleColor)
        assertEquals(ChatWallpaper.None, restored?.wallpaper)
        assertEquals(22, restored?.messageTextSizeSp)
        assertEquals(4, restored?.bubbleCornerRadiusDp)
        assertEquals(MessageLineSpacing.Normal, restored?.messageLineSpacing)
        assertEquals(MessageAnimationConfig(), restored?.messageAnimations)
    }

    @Test fun tooShortOrAbsurdlyLongListsAreIgnored() {
        assertNull(decodeAppearanceDraft(listOf("Warm", "Dark")))
        assertNull(decodeAppearanceDraft(List(20) { "Warm" }))
    }
}
