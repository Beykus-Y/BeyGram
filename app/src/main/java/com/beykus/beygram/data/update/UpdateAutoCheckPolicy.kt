package com.beykus.beygram.data.update

/**
 * Scheduling rules of the automatic update check. Pure: no Android, no clock, no network, so every rule is
 * tested with a fixed "now" and an explicit jitter.
 *
 * The automatic check never replaces the manual one. It only decides *when* an unattended attempt may start;
 * what the attempt may change in the user-visible state is decided by [UpdateCoordinator.checkAutomatically].
 */
object UpdateAutoCheckPolicy {
    /** Cadence while the app is open. The issue asks for "about 30 minutes", not an exact alarm. */
    const val FOREGROUND_INTERVAL_MS = 30L * 60 * 1000

    /** A resume sooner than this reuses the state from the previous attempt instead of asking GitHub again. */
    const val RESUME_INTERVAL_MS = 10L * 60 * 1000

    /** A cold start is the user's own launch: it may check even though the previous attempt is recent. */
    const val LAUNCH_INTERVAL_MS = 0L

    private const val BACKOFF_BASE_MS = 5L * 60 * 1000
    private const val BACKOFF_MAX_MS = 6L * 60 * 60 * 1000

    /** GitHub reports 403/429 for a rate limit; nothing may be retried before this window ends. */
    const val RATE_LIMITED_INTERVAL_MS = 60L * 60 * 1000

    /** A timestamp further in the future than this is a clock change, not a real attempt. */
    private const val MAX_CLOCK_SKEW_MS = 5L * 60 * 1000

    /** Bookkeeping of previous automatic attempts. */
    data class Attempts(
        val lastAttemptAt: Long? = null,
        val lastSuccessAt: Long? = null,
        val consecutiveFailures: Int = 0,
        /** Set when GitHub answered with a rate limit; no attempt is allowed before it. */
        val rateLimitedUntil: Long? = null,
    ) {
        val hasSucceeded: Boolean get() = lastSuccessAt != null
    }

    /**
     * When the next automatic attempt may start. [intervalMs] is the cadence of the trigger that asks; failures
     * stretch it with an exponential backoff so a broken server is not hammered from every resume.
     */
    fun nextAttemptAt(attempts: Attempts, intervalMs: Long, jitterMs: Long = 0L): Long? {
        val rateLimited = attempts.rateLimitedUntil
        val last = attempts.lastAttemptAt ?: return rateLimited
        val wait = maxOf(intervalMs, backoffMillis(attempts.consecutiveFailures) + jitterMs.coerceAtLeast(0L))
        val scheduled = last + wait
        return if (rateLimited != null && rateLimited > scheduled) rateLimited else scheduled
    }

    /** True when an attempt may start at [now]. A clock that moved backwards pauses the loop instead of spinning. */
    fun isDue(attempts: Attempts, now: Long, intervalMs: Long, jitterMs: Long = 0L): Boolean {
        attempts.lastAttemptAt?.let { last ->
            if (last > now + MAX_CLOCK_SKEW_MS) return false
        }
        val next = nextAttemptAt(attempts, intervalMs, jitterMs) ?: return true
        return now >= next
    }

    /** 0, then 5 min, 10, 20, 40, 80, 160, capped at 6 h. */
    fun backoffMillis(consecutiveFailures: Int): Long {
        if (consecutiveFailures <= 0) return 0L
        val exponent = (consecutiveFailures - 1).coerceAtMost(7)
        val delay = BACKOFF_BASE_MS shl exponent
        return delay.coerceAtMost(BACKOFF_MAX_MS)
    }

    /** After a rate limit nothing is retried for an hour, regardless of the cadence that asks. */
    fun afterRateLimit(now: Long): Long = now + RATE_LIMITED_INTERVAL_MS

    /**
     * Deterministic pseudo-jitter in `0 until spanMs`, derived from the version code and the attempt time, so a
     * retry storm from many devices spreads out while a single device stays reproducible for tests.
     */
    fun jitterMs(spanMs: Long, seed: Long): Long {
        if (spanMs <= 0L) return 0L
        // Knuth's MMIX linear congruential step: cheap, deterministic, and good enough to spread retries out.
        val mixed = seed * 6364136223846793005L + 1442695040888963407L
        val positive = mixed xor (mixed ushr 33)
        return (positive and Long.MAX_VALUE) % spanMs
    }

    /**
     * Whether a validated offer should reach the user as a system notification. A verified release that is not
     * newer than the installed build is never announced, and a build that is already installed is never announced.
     */
    fun shouldAnnounce(releaseVersionCode: Long, installedCode: Long): Boolean = releaseVersionCode > installedCode
}