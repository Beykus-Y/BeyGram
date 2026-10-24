package com.beykus.beygram.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePinPolicyTest {
    @Test fun `pin and unpin both need the canPin capability`() {
        assertFalse("A user without pin-management rights must not see the action", canTogglePin(canPin = false))
        assertTrue(canTogglePin(canPin = true))
    }

    @Test fun `an existing pin is not standing permission to unpin`() {
        // Message.isPinned is presentation state, not authorization. TDLib checks the same canBePinned for both calls.
        assertFalse(canTogglePin(canPin = false))
    }
}
