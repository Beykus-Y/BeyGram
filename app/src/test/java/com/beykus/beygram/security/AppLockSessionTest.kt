package com.beykus.beygram.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionTest {
    @Test
    fun coldStartIsLockedUntilUnlocked() {
        val session = AppLockSession(initiallyLocked = true)
        assertTrue(session.locked)
        session.unlock()
        assertFalse(session.locked)
    }

    @Test
    fun returningWithinTheTimeoutKeepsTheSessionOpen() {
        val session = AppLockSession(initiallyLocked = false)
        session.onBackground(nowMillis = 1_000L, timeout = AppLockTimeout.OneMinute)
        session.onForeground(nowMillis = 60_999L, timeout = AppLockTimeout.OneMinute)
        assertFalse(session.locked)
    }

    @Test
    fun returningAtTheTimeoutLocksTheSession() {
        val session = AppLockSession(initiallyLocked = false)
        session.onBackground(nowMillis = 1_000L, timeout = AppLockTimeout.OneMinute)
        session.onForeground(nowMillis = 61_000L, timeout = AppLockTimeout.OneMinute)
        assertTrue(session.locked)
    }

    @Test
    fun immediateTimeoutLocksWhenGoingToBackground() {
        val session = AppLockSession(initiallyLocked = false)
        session.onBackground(nowMillis = 5_000L, timeout = AppLockTimeout.Immediately)
        assertTrue(session.locked)
    }

    @Test
    fun aForegroundEventWithoutBackgroundingDoesNotLock() {
        val session = AppLockSession(initiallyLocked = false)
        session.onForeground(nowMillis = 999_999L, timeout = AppLockTimeout.Immediately)
        assertFalse(session.locked)
    }

    @Test
    fun unlockingClearsThePendingBackgroundTimestamp() {
        val session = AppLockSession(initiallyLocked = false)
        session.onBackground(nowMillis = 1_000L, timeout = AppLockTimeout.Immediately)
        session.unlock()
        session.onForeground(nowMillis = 2_000L, timeout = AppLockTimeout.Immediately)
        assertFalse(session.locked)
    }

    @Test
    fun disablingAndEnablingInTheCurrentSessionUnlock() {
        val session = AppLockSession(initiallyLocked = true)
        session.onDisabled()
        assertFalse(session.locked)

        session.onBackground(nowMillis = 1_000L, timeout = AppLockTimeout.Immediately)
        session.onEnabledInCurrentSession()
        assertFalse(session.locked)
        session.onForeground(nowMillis = 2_000L, timeout = AppLockTimeout.Immediately)
        assertFalse(session.locked)
    }
}
