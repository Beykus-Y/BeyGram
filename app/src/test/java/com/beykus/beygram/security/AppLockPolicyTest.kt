package com.beykus.beygram.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    @Test
    fun pinMustBeFourToEightDigits() {
        listOf("1234", "0000", "12345678", "987654").forEach {
            assertTrue(it, AppLockPolicy.isValidPin(it))
        }
        listOf("", "123", "123456789", "12a4", "12 4", "-1234", "１２３４").forEach {
            assertFalse(it, AppLockPolicy.isValidPin(it))
        }
    }

    @Test
    fun immediatelyLocksRegardlessOfElapsedTime() {
        assertTrue(AppLockPolicy.shouldLockAfterBackground(AppLockTimeout.Immediately, 0L))
        assertTrue(AppLockPolicy.shouldLockAfterBackground(AppLockTimeout.Immediately, -1L))
    }

    @Test
    fun timedLocksUseTheConfiguredThreshold() {
        assertFalse(AppLockPolicy.shouldLockAfterBackground(AppLockTimeout.OneMinute, 59_999L))
        assertTrue(AppLockPolicy.shouldLockAfterBackground(AppLockTimeout.OneMinute, 60_000L))
        assertFalse(AppLockPolicy.shouldLockAfterBackground(AppLockTimeout.FiveMinutes, 299_999L))
        assertTrue(AppLockPolicy.shouldLockAfterBackground(AppLockTimeout.FiveMinutes, 300_000L))
    }

    @Test
    fun storedTimeoutFallsBackToImmediately() {
        assertEquals(AppLockTimeout.Immediately, AppLockTimeout.fromStored(0))
        assertEquals(AppLockTimeout.OneMinute, AppLockTimeout.fromStored(60))
        assertEquals(AppLockTimeout.FiveMinutes, AppLockTimeout.fromStored(300))
        assertEquals(AppLockTimeout.Immediately, AppLockTimeout.fromStored(42))
    }

    @Test
    fun firstFiveWrongAttemptsHaveNoDelay() {
        (0..4).forEach { assertEquals(0L, AppLockPolicy.lockoutMillisForFailures(it)) }
        assertEquals(4, AppLockPolicy.attemptsLeft(1))
        assertEquals(0, AppLockPolicy.attemptsLeft(5))
        assertEquals(0, AppLockPolicy.attemptsLeft(9))
    }

    @Test
    fun repeatedFailuresBackOffExponentiallyAndCapAtFiveMinutes() {
        assertEquals(30_000L, AppLockPolicy.lockoutMillisForFailures(5))
        assertEquals(60_000L, AppLockPolicy.lockoutMillisForFailures(6))
        assertEquals(120_000L, AppLockPolicy.lockoutMillisForFailures(7))
        assertEquals(240_000L, AppLockPolicy.lockoutMillisForFailures(8))
        assertEquals(300_000L, AppLockPolicy.lockoutMillisForFailures(9))
        assertEquals(300_000L, AppLockPolicy.lockoutMillisForFailures(100))
    }
}
