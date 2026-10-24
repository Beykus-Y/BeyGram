package com.beykus.beygram.security

/**
 * Foreground/background state machine of the process-wide lock. It is deliberately free of
 * Android APIs and of a clock: callers pass monotonic elapsed-realtime values.
 */
internal class AppLockSession(initiallyLocked: Boolean) {
    var locked: Boolean = initiallyLocked
        private set

    private var backgroundedAtMillis: Long? = null

    fun onForeground(nowMillis: Long, timeout: AppLockTimeout) {
        val since = backgroundedAtMillis ?: return
        if (AppLockPolicy.shouldLockAfterBackground(timeout, nowMillis - since)) locked = true
        backgroundedAtMillis = null
    }

    fun onBackground(nowMillis: Long, timeout: AppLockTimeout) {
        backgroundedAtMillis = nowMillis
        if (timeout == AppLockTimeout.Immediately) locked = true
    }

    /** Records that the app left the foreground without deciding whether a lock is configured yet. */
    fun recordBackground(nowMillis: Long) {
        backgroundedAtMillis = nowMillis
    }

    fun lockNow() {
        locked = true
    }

    fun clearBackground() {
        backgroundedAtMillis = null
    }

    fun unlock() {
        locked = false
        backgroundedAtMillis = null
    }

    fun onDisabled() {
        // Keep the background timestamp: the lock may be enabled while this process is still
        // in the background, and that transition must not lose the elapsed time.
        locked = false
    }

    /** Enabling the lock from Settings keeps the current, already authenticated session open. */
    fun onEnabledInCurrentSession() {
        locked = false
        backgroundedAtMillis = null
    }
}
