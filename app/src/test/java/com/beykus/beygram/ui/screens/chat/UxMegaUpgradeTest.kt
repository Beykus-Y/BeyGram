package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.ThemePreset
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.ui.screens.theme.AppearanceUserPresetStore
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UxMegaUpgradeTest {
    @Test
    fun messageSendOptionsCarriesReplyQuote() {
        val opts = MessageSendOptions(replyQuote = "exact phrase", replyQuotePosition = 12)
        assertEquals("exact phrase", opts.replyQuote)
        assertEquals(12, opts.replyQuotePosition)
    }

    @Test
    fun outgoingMediaDefaultsSpoilerOffAndSupportsToggle() {
        val photo = OutgoingMedia(path = "/tmp/a.jpg", fileName = "a.jpg", mimeType = "image/jpeg", kind = MediaKind.Photo)
        assertFalse(photo.hasSpoiler)
        assertTrue(photo.copy(hasSpoiler = true).hasSpoiler)
    }

    @Test
    fun appearanceUserPresetRoundTripsThroughJson() {
        val original = AppearanceConfig(
            preset = ThemePreset.Warm,
            mode = ThemeMode.Dark,
            designStyle = DesignStyle.Signature,
            messageTextSizeSp = 17
        )
        val encoded = AppearanceUserPresetStore.encode(original)
        val decoded = AppearanceUserPresetStore.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun appearanceUserPresetKeepsTheLiquidGlassV2Switch() {
        val original = AppearanceConfig(designStyle = DesignStyle.LiquidGlass, liquidGlassV2 = true)
        val decoded = AppearanceUserPresetStore.decode(AppearanceUserPresetStore.encode(original))
        assertEquals(true, decoded?.liquidGlassV2)
        assertEquals(original, decoded)
    }

    @Test
    fun appearanceUserPresetKeepsTheLensPreset() {
        val original = AppearanceConfig(
            designStyle = DesignStyle.LiquidGlass,
            liquidGlassV2 = true,
            liquidGlassPreset = com.beykus.beygram.data.appearance.LiquidGlassLensPreset.Clear
        )
        val decoded = AppearanceUserPresetStore.decode(AppearanceUserPresetStore.encode(original))
        assertEquals(com.beykus.beygram.data.appearance.LiquidGlassLensPreset.Clear, decoded?.liquidGlassPreset)
    }

    @Test
    fun appearanceUserPresetWithoutTheLensPresetReadsAsClear() {
        val legacy = AppearanceUserPresetStore.encode(AppearanceConfig(liquidGlassV2 = true))
            .replace(Regex(",\"liquidGlassPreset\":\"[A-Za-z]+\"|\"liquidGlassPreset\":\"[A-Za-z]+\","), "")
        assertEquals(com.beykus.beygram.data.appearance.LiquidGlassLensPreset.Clear, AppearanceUserPresetStore.decode(legacy)?.liquidGlassPreset)
    }

    @Test
    fun appearanceUserPresetWithoutTheSwitchReadsAsOn() {
        // A preset saved before the switch was stored has no "liquidGlassV2" key.
        val legacy = AppearanceUserPresetStore.encode(AppearanceConfig(designStyle = DesignStyle.LiquidGlass))
            .replace(Regex(",\"liquidGlassV2\":(true|false)|\"liquidGlassV2\":(true|false),"), "")
        assertEquals(true, AppearanceUserPresetStore.decode(legacy)?.liquidGlassV2)
    }

    @Test
    fun appearanceUserPresetPreservesExplicitOffAndStandard() {
        val saved = AppearanceConfig(
            designStyle = DesignStyle.LiquidGlass,
            liquidGlassV2 = false,
            liquidGlassPreset = com.beykus.beygram.data.appearance.LiquidGlassLensPreset.Standard
        )
        val decoded = AppearanceUserPresetStore.decode(AppearanceUserPresetStore.encode(saved))
        assertEquals(saved, decoded)
    }
}
