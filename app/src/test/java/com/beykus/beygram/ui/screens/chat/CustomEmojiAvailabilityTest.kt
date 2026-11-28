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
        assertTrue(customEmojiAllowed(premium = null, savedMessages = false))
        assertFalse(customEmojiAllowed(premium = false, savedMessages = false))
        assertTrue(customEmojiAllowed(premium = true, savedMessages = false))
        assertTrue(customEmojiAllowed(premium = false, savedMessages = true))
        assertTrue(customEmojiAllowed(premium = true, savedMessages = true))
    }

    @Test
    fun nothingIsMarkedLockedUntilThePremiumStatusIsKnown() {
        assertFalse(customEmojiLocked(premium = null, savedMessages = false))
        assertTrue(customEmojiLocked(premium = false, savedMessages = false))
        assertFalse(customEmojiLocked(premium = true, savedMessages = false))
        assertFalse(customEmojiLocked(premium = null, savedMessages = true))
        assertFalse(customEmojiLocked(premium = false, savedMessages = true))
    }
}