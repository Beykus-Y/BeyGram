package com.beykus.beygram.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePinPolicyTest {
    @Test fun `an unpinned message needs the canPin capability`() {
        assertFalse("An unpinned message must not offer pinning without rights", canTogglePin(isPinned = false, canPin = false))
        assertTrue(canTogglePin(isPinned = false, canPin = true))
    }

    @Test fun `an existing pin can always be removed`() {
        assertTrue("A conservative canBePinned must not trap an existing pin", canTogglePin(isPinned = true, canPin = false))
        assertTrue(canTogglePin(isPinned = true, canPin = true))
    }
}
