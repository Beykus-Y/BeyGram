package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.media.ComposerRecordingMode
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.design.rememberGlassBackdrop
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xhdpi")
class GlassControlGeometryTest {
    @get:Rule
    val rule = createComposeRule()

    private fun assertCenteredInside(outerTag: String, glyphTag: String, contextLabel: String) {
        val outerBounds: Rect = rule.onNodeWithTag(outerTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val glyphBounds: Rect = rule.onNodeWithTag(glyphTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        assertTrue("$contextLabel: outer width must be > 0 (was ${outerBounds.width})", outerBounds.width > 0f)
        assertTrue("$contextLabel: outer height must be > 0 (was ${outerBounds.height})", outerBounds.height > 0f)
        assertTrue("$contextLabel: glyph width must be > 0 (was ${glyphBounds.width})", glyphBounds.width > 0f)
        assertTrue("$contextLabel: glyph height must be > 0 (was ${glyphBounds.height})", glyphBounds.height > 0f)

        assertTrue(
            "$contextLabel: glyph ($glyphBounds) must not clip outside outer ($outerBounds)",
            glyphBounds.left >= outerBounds.left - 0.5f &&
                glyphBounds.right <= outerBounds.right + 0.5f &&
                glyphBounds.top >= outerBounds.top - 0.5f &&
                glyphBounds.bottom <= outerBounds.bottom + 0.5f
        )

        val outerCenterX = (outerBounds.left + outerBounds.right) / 2f
        val outerCenterY = (outerBounds.top + outerBounds.bottom) / 2f
        val glyphCenterX = (glyphBounds.left + glyphBounds.right) / 2f
        val glyphCenterY = (glyphBounds.top + glyphBounds.bottom) / 2f

        assertTrue(
            "$contextLabel: glyph centerX ($glyphCenterX) must match outer centerX ($outerCenterX)",
            abs(glyphCenterX - outerCenterX) <= 1.5f
        )
        assertTrue(
            "$contextLabel: glyph centerY ($glyphCenterY) must match outer centerY ($outerCenterY)",
            abs(glyphCenterY - outerCenterY) <= 1.5f
        )
    }

    @Test
    fun jumpToLatestArrowIsCenteredAcrossAllStylesAndBadges() {
        var style by mutableStateOf(DesignStyle.LiquidGlass)
        var v2 by mutableStateOf(true)
        var unread by mutableStateOf(0)
        var rtl by mutableStateOf(false)
        var fontScale by mutableStateOf(1f)

        rule.setContent {
            val baseDensity = LocalDensity.current
            val backdrop = rememberGlassBackdrop()
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(baseDensity.density, fontScale = fontScale),
                LocalGlassBackdrop provides backdrop
            ) {
                BeyGramTheme(design = designFor(style, GlassQuality.High, liquidGlassV2 = v2)) {
                    JumpToLatestButton(unread = unread, onClick = {})
                }
            }
        }

        for (s in DesignStyle.entries) {
            style = s
            for (u in listOf(0, 5)) {
                unread = u
                rule.waitForIdle()
                assertCenteredInside("jump_to_latest_button", "jump_to_latest_glyph", "JumpToLatest $s unread=$u")
            }
        }
        style = DesignStyle.LiquidGlass
        v2 = false
        unread = 5
        rtl = true
        fontScale = 1.5f
        rule.waitForIdle()
        assertCenteredInside("jump_to_latest_button", "jump_to_latest_glyph", "JumpToLatest LiquidGlass v2=false RTL 1.5x")
    }

    @Test
    fun floatingBadgeButtonsCenterIconsAcrossStylesAndCounts() {
        var style by mutableStateOf(DesignStyle.LiquidGlass)
        var v2 by mutableStateOf(true)
        var count by mutableStateOf(0)
        var useFavorite by mutableStateOf(false)
        var rtl by mutableStateOf(false)

        rule.setContent {
            val backdrop = rememberGlassBackdrop()
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalGlassBackdrop provides backdrop
            ) {
                BeyGramTheme(design = designFor(style, GlassQuality.High, liquidGlassV2 = v2)) {
                    ChatFloatingBadgeButton(
                        icon = if (useFavorite) Icons.Rounded.Favorite else Icons.Rounded.AlternateEmail,
                        count = count,
                        label = "Badge",
                        onClick = {}
                    )
                }
            }
        }

        for (s in DesignStyle.entries) {
            style = s
            for (fav in listOf(false, true)) {
                useFavorite = fav
                for (c in listOf(0, 3)) {
                    count = c
                    rule.waitForIdle()
                    assertCenteredInside("chat_floating_badge_button", "chat_floating_badge_glyph", "Badge $s fav=$fav count=$c")
                }
            }
        }
        style = DesignStyle.LiquidGlass
        v2 = false
        useFavorite = true
        count = 3
        rtl = true
        rule.waitForIdle()
        assertCenteredInside("chat_floating_badge_button", "chat_floating_badge_glyph", "Badge LiquidGlass v2=false RTL")
    }

    @Test
    fun sendControlFaceShowsAndCentersGlyphAcrossStatesStylesAndGlassModes() {
        var style by mutableStateOf(DesignStyle.LiquidGlass)
        var v2 by mutableStateOf(true)
        var busy by mutableStateOf(false)
        var canSend by mutableStateOf(true)
        var editing by mutableStateOf(false)
        var hasBackdrop by mutableStateOf(true)
        var onGlassPlane by mutableStateOf(false)
        var rtl by mutableStateOf(false)
        var fontScale by mutableStateOf(1f)

        rule.setContent {
            val baseDensity = LocalDensity.current
            val backdrop = rememberGlassBackdrop()
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(baseDensity.density, fontScale = fontScale),
                LocalGlassBackdrop provides (if (hasBackdrop) backdrop else null),
                LocalComposerOnGlassPlane provides onGlassPlane
            ) {
                BeyGramTheme(design = designFor(style, GlassQuality.High, liquidGlassV2 = v2)) {
                    SendControlFace(busy = busy, canSend = canSend, editing = editing)
                }
            }
        }

        for (s in DesignStyle.entries) {
            style = s
            for ((b, cs, ed) in listOf(
                Triple(false, true, false),
                Triple(false, false, false),
                Triple(false, true, true),
                Triple(true, true, false)
            )) {
                busy = b
                canSend = cs
                editing = ed
                rule.waitForIdle()
                assertCenteredInside("send_control_face", "send_control_glyph", "SendControl $s busy=$b canSend=$cs editing=$ed")
            }
        }

        style = DesignStyle.LiquidGlass
        for (backdropOn in listOf(true, false)) {
            for (shared in listOf(false, true)) {
                for (v2On in listOf(true, false)) {
                    hasBackdrop = backdropOn
                    onGlassPlane = shared
                    v2 = v2On
                    busy = false
                    canSend = true
                    editing = false
                    rtl = true
                    fontScale = 1.5f
                    rule.waitForIdle()
                    assertCenteredInside(
                        "send_control_face",
                        "send_control_glyph",
                        "SendControl LiquidGlass backdrop=$backdropOn shared=$shared v2=$v2On RTL 1.5x"
                    )
                }
            }
        }
    }

    @Test
    fun composerAttachAndRecordButtonsCenterIconsInGlassAndRtl() {
        var mode by mutableStateOf(ComposerRecordingMode.Voice)
        var shared by mutableStateOf(false)
        var rtl by mutableStateOf(false)

        rule.setContent {
            val backdrop = rememberGlassBackdrop()
            val attachmentState = rememberAttachmentState(
                chatId = 1L,
                sendMedia = { _, _, _, _ -> Result.success(Unit) },
                sendSticker = { _, _ -> Result.success(Unit) },
                sendExtra = { _, _ -> Result.success(Unit) }
            )
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalGlassBackdrop provides backdrop,
                LocalComposerOnGlassPlane provides shared
            ) {
                BeyGramTheme(design = designFor(DesignStyle.LiquidGlass, GlassQuality.High, liquidGlassV2 = true)) {
                    Box {
                        AttachmentButton(state = attachmentState, enabled = true, embedded = false)
                        RecordingControlButton(
                            mode = mode,
                            active = false,
                            canceling = false,
                            enabled = true,
                            onTap = {},
                            onStart = {},
                            onAccessibleStart = {},
                            onCanceling = {},
                            onLock = {},
                            onRelease = {}
                        )
                    }
                }
            }
        }

        for (m in ComposerRecordingMode.entries) {
            for (sh in listOf(false, true)) {
                for (isRtl in listOf(false, true)) {
                    mode = m
                    shared = sh
                    rtl = isRtl
                    rule.waitForIdle()
                    assertCenteredInside("attachment_composer_button", "attachment_composer_glyph", "Attach shared=$sh rtl=$isRtl")
                    assertCenteredInside("recording_control_button", "recording_control_glyph", "Record $m shared=$sh rtl=$isRtl")
                }
            }
        }
    }
}
