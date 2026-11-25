package com.beykus.beygram.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dedupe rules for the update notification tap (Codex review of #1166).
 */
class UpdateIntentGateTest {
    private val action = UpdateNotifications.ACTION_OPEN_UPDATES

    private fun uri(versionCode: Long, postedAt: Long) =
        "beygram://com.beykus.beygram.dev/updates/$versionCode/$postedAt"

    @Test
    fun `a fresh tap is accepted`() {
        val gate = UpdateIntentGate()
        assertTrue(gate.accept(action, uri(64, 1_000)))
    }

    @Test
    fun `a re-delivered intent of the same notification is ignored`() {
        val gate = UpdateIntentGate()
        assertTrue(gate.accept(action, uri(64, 1_000)))
        // Recreation re-delivers the very same Intent: it must not reopen the screen.
        assertFalse(gate.accept(action, uri(64, 1_000)))
    }

    @Test
    fun `a later post of the same version is a new tap`() {
        val gate = UpdateIntentGate()
        assertTrue(gate.accept(action, uri(64, 1_000)))
        assertTrue(gate.accept(action, uri(64, 2_000)))
    }

    @Test
    fun `another version is always a new tap`() {
        val gate = UpdateIntentGate()
        assertTrue(gate.accept(action, uri(64, 1_000)))
        assertTrue(gate.accept(action, uri(65, 1_000)))
    }

    @Test
    fun `the processed tap survives recreation through saved state`() {
        val gate = UpdateIntentGate()
        assertTrue(gate.accept(action, uri(64, 1_000)))

        val restored = UpdateIntentGate().apply { restore(gate.handledData) }
        assertFalse(restored.accept(action, uri(64, 1_000)))
        assertTrue(restored.accept(action, uri(64, 9_000)))
    }

    @Test
    fun `an unrelated intent never opens the screen`() {
        val gate = UpdateIntentGate()
        assertFalse(gate.accept("android.intent.action.VIEW", "https://example.com/x"))
        assertFalse(gate.accept(null, uri(64, 1_000)))
    }

    @Test
    fun `a tap without data is still accepted once`() {
        val gate = UpdateIntentGate()
        assertTrue(gate.accept(action, null))
        // Nothing identifies it, so it cannot be deduplicated; a repeat is a separate tap.
        assertTrue(gate.accept(action, null))
    }
}