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

    @Test
    fun enablingTheLockWhileBackgroundedRequiresAuthenticationOnReturn() {
        // PIN setup can finish after the Activity has already stopped. That transition must
        // fail closed even when the user selected a five-minute timeout.
        val session = AppLockSession(initiallyLocked = false)
        session.onDisabled()
        session.recordBackground(nowMillis = 1_000L)
        session.lockNow()

        session.onForeground(nowMillis = 2_000L, timeout = AppLockTimeout.FiveMinutes)
        assertTrue(session.locked)
    }

    @Test
    fun disabledSessionKeepsTheBackgroundTimestampUntilItIsCleared() {
        val session = AppLockSession(initiallyLocked = false)
        session.onBackground(nowMillis = 1_000L, timeout = AppLockTimeout.OneMinute)
        session.onDisabled()
        assertFalse(session.locked)

        // The timestamp survived onDisabled: returning after the timeout still locks.
        session.onForeground(nowMillis = 62_000L, timeout = AppLockTimeout.OneMinute)
        assertTrue(session.locked)

        session.unlock()
        session.onBackground(nowMillis = 100_000L, timeout = AppLockTimeout.OneMinute)
        session.clearBackground()
        session.onForeground(nowMillis = 400_000L, timeout = AppLockTimeout.OneMinute)
        assertFalse(session.locked)
    }
}
