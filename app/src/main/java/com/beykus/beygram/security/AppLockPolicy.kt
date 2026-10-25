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

    /**
     * A foreign Activity that BeyGram starts itself (photo picker, camera, permission dialog,
     * share sheet) stops MainActivity right after the launch request. Only a stop this close to
     * such a request is that trip; any later stop is a real background period.
     */
    const val EXTERNAL_LAUNCH_WINDOW_MILLIS = 10_000L

    /** A trip BeyGram started itself is not a background period, unless it lasts longer than this. */
    const val EXTERNAL_HANDOFF_MAX_MILLIS = 600_000L

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    fun shouldLockAfterBackground(timeout: AppLockTimeout, elapsedMillis: Long): Boolean =
        timeout == AppLockTimeout.Immediately || elapsedMillis >= timeout.seconds * 1_000L

    fun attemptsLeft(failedAttempts: Int): Int =
        (FREE_ATTEMPTS - failedAttempts).coerceAtLeast(0)

    /**
     * True when the app was stopped without an active lock and the lock is active now.
     * A slow PIN setup may finish after the Activity stopped; that first return must fail closed.
     */
    fun lockBecameActiveWhileAway(
        backgrounded: Boolean,
        activeWhenBackgrounded: Boolean,
        activeNow: Boolean,
    ): Boolean = backgrounded && !activeWhenBackgrounded && activeNow

    /**
     * AppLockActivity finishing after authentication normally hands over to MainActivity in a
     * few hundred milliseconds. Only that short gap may skip the background timeout; pressing
     * Home and returning later must still be treated as a real background period.
     */
    fun isUnlockHandover(
        wasUnlocking: Boolean,
        backgroundAgeMillis: Long?,
        graceMillis: Long = 2_000L,
    ): Boolean = wasUnlocking && backgroundAgeMillis != null &&
        backgroundAgeMillis in 0L..graceMillis

    fun isExternalHandoff(launchRequestedAtMillis: Long?, stoppedAtMillis: Long): Boolean =
        launchRequestedAtMillis != null && stoppedAtMillis - launchRequestedAtMillis in 0L..EXTERNAL_LAUNCH_WINDOW_MILLIS

    /**
     * Returning from a short trip that BeyGram started keeps the session open, whatever the timeout.
     * A long trip is an ordinary background period; a screen-off during the trip is handled separately.
     */
    fun isExternalHandoffReturn(wasExternalHandoff: Boolean, backgroundAgeMillis: Long?): Boolean =
        wasExternalHandoff && backgroundAgeMillis != null && backgroundAgeMillis in 0L..EXTERNAL_HANDOFF_MAX_MILLIS

    /** Zero until the free attempts are used, then a doubling delay capped at five minutes. */
    fun lockoutMillisForFailures(failedAttempts: Int): Long {
        if (failedAttempts < FREE_ATTEMPTS) return 0L
        val step = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(4)
        return (BASE_LOCKOUT_MILLIS shl step).coerceAtMost(MAX_LOCKOUT_MILLIS)
    }
}
