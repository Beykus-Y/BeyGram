package com.beykus.beygram.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted slow hash for the local PIN. The PIN itself is never stored: only the salt, the
 * PBKDF2-HMAC-SHA256 output and the iteration count live in private SharedPreferences.
 */
internal object AppLockPinHasher {
    const val DEFAULT_ITERATIONS = 210_000
    const val MIN_ITERATIONS = 10_000
    const val MAX_ITERATIONS = 600_000
    const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    /**
     * Derives [KEY_BITS] bits. The iteration count is clamped before use so a corrupted or
     * tampered preference file cannot turn an unlock into an unbounded computation.
     */
    fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val effectiveIterations = iterations.coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)
        val characters = pin.toCharArray()
        val spec = PBEKeySpec(characters, salt, effectiveIterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
            characters.fill('\u0000')
        }
    }

    /** Constant-time comparison of the derived key with the stored one. */
    fun verify(pin: String, salt: ByteArray, iterations: Int, expected: ByteArray): Boolean {
        if (expected.isEmpty()) return false
        return MessageDigest.isEqual(derive(pin, salt, iterations), expected)
    }
}
