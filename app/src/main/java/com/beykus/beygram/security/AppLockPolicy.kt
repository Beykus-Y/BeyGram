package com.beykus.beygram.security

/**
 * How long BeyGram may stay in the background before the local lock is shown again.
 * The stored value is the number of seconds, so a renamed enum constant never loses the user's choice.
 */
internal enum class AppLockTimeout(val seconds: Int) {
    Immediately(0),
    OneMinute(60),
    FiveMinutes(300);

    companion object {
        fun fromStored(seconds: Int): AppLockTimeout =
            entries.firstOrNull { it.seconds == seconds } ?: Immediately
    }
}

/** Settings of the app-wide local lock. The lock is shared by every Telegram account in this installation. */
internal data class AppLockSettings(
    val enabled: Boolean = false,
    val hasPin: Boolean = false,
    val timeout: AppLockTimeout = AppLockTimeout.Immediately,
    val hideRecents: Boolean = false,
    val biometricEnabled: Boolean = true,
) {
    /** A lock is active only when it was enabled and its PIN hash is intact. */
    val isActive: Boolean get() = enabled && hasPin
}

/**
 * Pure rules behind the lock: PIN shape, background timeout and brute-force throttling.
 * Android storage and UI stay out of this object so the rules can be tested on the JVM.
 */
internal object AppLockPolicy {
    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8

    /** Wrong attempts allowed before the first delay. */
    const val FREE_ATTEMPTS = 5
    const val BASE_LOCKOUT_MILLIS = 30_000L
    const val MAX_LOCKOUT_MILLIS = 300_000L

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    fun shouldLockAfterBackground(timeout: AppLockTimeout, elapsedMillis: Long): Boolean =
        timeout == AppLockTimeout.Immediately || elapsedMillis >= timeout.seconds * 1_000L

    fun attemptsLeft(failedAttempts: Int): Int =
        (FREE_ATTEMPTS - failedAttempts).coerceAtLeast(0)

    /** Zero until the free attempts are used, then a doubling delay capped at five minutes. */
    fun lockoutMillisForFailures(failedAttempts: Int): Long {
        if (failedAttempts < FREE_ATTEMPTS) return 0L
        val step = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(4)
        return (BASE_LOCKOUT_MILLIS shl step).coerceAtMost(MAX_LOCKOUT_MILLIS)
    }
}
