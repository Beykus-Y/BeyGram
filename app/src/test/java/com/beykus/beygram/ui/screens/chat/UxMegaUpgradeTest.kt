package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.ThemePreset
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.ui.screens.theme.AppearanceUserPresetStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UxMegaUpgradeTest {
    @Test
    fun quoteCandidatesSplitSentencesAndComputeOffset() {
        val text = "First sentence here. Second sentence! Third line\nFourth line"
        val parts = extractQuoteCandidates(text)
        assertEquals(listOf("First sentence here.", "Second sentence!", "Third line", "Fourth line"), parts)
        assertEquals(21, quoteOffsetIn(text, "Second sentence!"))
        assertEquals(0, quoteOffsetIn(text, "Custom edited phrase"))
    }

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
}
