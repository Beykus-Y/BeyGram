package com.beykus.beygram.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPinHasherTest {
    private val iterations = 10_000

    @Test
    fun samePinAndSaltProduceTheSameKey() {
        val salt = AppLockPinHasher.newSalt()
        assertArrayEquals(
            AppLockPinHasher.derive("1234", salt, iterations),
            AppLockPinHasher.derive("1234", salt, iterations),
        )
    }

    @Test
    fun differentSaltsProduceDifferentKeysForTheSamePin() {
        val first = AppLockPinHasher.derive("1234", AppLockPinHasher.newSalt(), iterations)
        val second = AppLockPinHasher.derive("1234", AppLockPinHasher.newSalt(), iterations)
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun verifyAcceptsTheOriginalPinOnly() {
        val salt = AppLockPinHasher.newSalt()
        val hash = AppLockPinHasher.derive("482916", salt, iterations)
        assertTrue(AppLockPinHasher.verify("482916", salt, iterations, hash))
        assertFalse(AppLockPinHasher.verify("482917", salt, iterations, hash))
        assertFalse(AppLockPinHasher.verify("", salt, iterations, hash))
        assertFalse(AppLockPinHasher.verify("482916", salt, iterations, ByteArray(0)))
    }

    @Test
    fun saltIsRandomAndFullLength() {
        val first = AppLockPinHasher.newSalt()
        val second = AppLockPinHasher.newSalt()
        assertTrue(first.size == AppLockPinHasher.SALT_BYTES)
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun iterationCountIsClampedToTheSupportedRange() {
        val salt = AppLockPinHasher.newSalt()
        assertArrayEquals(
            AppLockPinHasher.derive("1234", salt, AppLockPinHasher.MIN_ITERATIONS),
            AppLockPinHasher.derive("1234", salt, 0),
        )
        assertArrayEquals(
            AppLockPinHasher.derive("1234", salt, AppLockPinHasher.MAX_ITERATIONS),
            AppLockPinHasher.derive("1234", salt, Int.MAX_VALUE),
        )
    }
}
