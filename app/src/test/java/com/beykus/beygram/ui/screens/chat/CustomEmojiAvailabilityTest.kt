package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDLib 1.8.65 has no per-custom-emoji free flag, so the picker follows the account/Saved Messages policy of the
 * official clients. A server refusal remains authoritative and is surfaced by the normal send error path.
 */
class CustomEmojiAvailabilityTest {
    @Test
    fun onlyPremiumOrSavedMessagesMayInsertCustomEmoji() {
        assertFalse(customEmojiAllowed(premium = null, savedMessages = false))
        assertFalse(customEmojiAllowed(premium = false, savedMessages = false))
        assertTrue(customEmojiAllowed(premium = true, savedMessages = false))
        assertTrue(customEmojiAllowed(premium = false, savedMessages = true))
        assertTrue(customEmojiAllowed(premium = true, savedMessages = true))
    }
}