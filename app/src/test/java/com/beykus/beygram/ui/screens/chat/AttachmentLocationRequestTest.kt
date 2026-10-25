package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentLocationRequestTest {
    @Test
    fun `one fix owns the attachment slot and a late result is ignored`() {
        val gate = LocationRequestGate()

        val first = gate.begin()
        assertNotNull(first)
        assertTrue(gate.isBusy)
        // A contact/poll/dice choice is refused while this fix is pending.
        assertNull(gate.begin())

        gate.cancel()
        assertFalse(gate.isBusy)
        assertFalse(gate.complete(first!!))

        val second = gate.begin()
        assertNotNull(second)
        assertTrue(gate.complete(second!!))
        assertFalse(gate.isBusy)
    }
}
