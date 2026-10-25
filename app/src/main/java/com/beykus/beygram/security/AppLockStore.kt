package com.beykus.beygram.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Base64

/** Result of checking a PIN, including the brute-force delay that now applies. */
internal sealed interface AppLockPinCheck {
    data object Success : AppLockPinCheck
    data class Failure(val attemptsLeft: Int, val lockoutMillis: Long) : AppLockPinCheck
    data class LockedOut(val remainingMillis: Long) : AppLockPinCheck
}

/**
 * Local, app-wide storage of the optional lock. The file is intentionally not part of the
 * backup allowlist: a restored backup must never carry a PIN hash to another installation.
 */
internal class AppLockStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppLockSettings> = _settings.asStateFlow()

    init {
        // A half-written preference file must not lock the owner out of their own chats.
        // Drop an enabled flag without a usable hash; the user simply configures the lock again.
        if (preferences.getBoolean(KEY_ENABLED, false) && !hasPinHash()) {
            preferences.edit().putBoolean(KEY_ENABLED, false).apply()
            _settings.value = readSettings()
        }
    }

    /** Stores a new salted hash and enables the lock. Runs the slow KDF off the main thread. */
    suspend fun setPin(pin: String): Result<Unit> = withContext(Dispatchers.Default) {
        if (!AppLockPolicy.isValidPin(pin)) {
            return@withContext Result.failure(IllegalArgumentException("PIN must contain 4 to 8 digits"))
        }
        runCatching {
            val salt = AppLockPinHasher.newSalt()
            val hash = AppLockPinHasher.derive(pin, salt, AppLockPinHasher.DEFAULT_ITERATIONS)
            // commit() is a deliberate barrier: the settings screen must not report success
            // before the hash is durable.
            val saved = preferences.edit()
                .putString(KEY_SALT, encode(salt))
                .putString(KEY_HASH, encode(hash))
                .putInt(KEY_ITERATIONS, AppLockPinHasher.DEFAULT_ITERATIONS)
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .commit()
            check(saved) { "Unable to persist the app-lock PIN hash" }
            publish()
        }
    }

    /**
     * Hashes [pin] and updates the failure counter. A lockout is persisted with wall-clock time
     * so it survives an Activity or process restart.
     */
    suspend fun verifyPin(pin: String, nowMillis: Long = System.currentTimeMillis()): AppLockPinCheck {
        val remaining = lockoutRemainingMillis(nowMillis)
        if (remaining > 0L) return AppLockPinCheck.LockedOut(remaining)

        val salt = readSalt()
        val expected = readHash()
        if (!AppLockPolicy.isValidPin(pin) || salt == null || expected == null) {
            return AppLockPinCheck.Failure(AppLockPolicy.attemptsLeft(readFailedAttempts()), 0L)
        }

        val matches = withContext(Dispatchers.Default) {
            // A broken crypto provider must never turn into a successful unlock.
            runCatching { AppLockPinHasher.verify(pin, salt, readIterations(), expected) }
                .getOrDefault(false)
        }
        return withContext(Dispatchers.IO) {
            synchronized(this@AppLockStore) {
                if (matches) {
                    preferences.edit()
                        .putInt(KEY_FAILED_ATTEMPTS, 0)
                        .putLong(KEY_LOCKOUT_UNTIL, 0L)
                        .commit()
                    publish()
                    AppLockPinCheck.Success
                } else {
                    val failures = readFailedAttempts() + 1
                    val lockout = AppLockPolicy.lockoutMillisForFailures(failures)
                    // commit(): a force-stop between attempts must not reset the throttle.
                    preferences.edit()
                        .putInt(KEY_FAILED_ATTEMPTS, failures)
                        .putLong(KEY_LOCKOUT_UNTIL, if (lockout > 0L) nowMillis + lockout else 0L)
                        .commit()
                    publish()
                    AppLockPinCheck.Failure(AppLockPolicy.attemptsLeft(failures), lockout)
                }
            }
        }
    }

    fun lockoutRemainingMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        (preferences.getLong(KEY_LOCKOUT_UNTIL, 0L) - nowMillis).coerceAtLeast(0L)

    fun updateTimeout(timeout: AppLockTimeout) {
        preferences.edit().putInt(KEY_TIMEOUT_SECONDS, timeout.seconds).apply()
        publish()
    }

    fun updateHideRecents(hidden: Boolean) {
        preferences.edit().putBoolean(KEY_HIDE_RECENTS, hidden).apply()
        publish()
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
        publish()
    }

    /** A successful biometric is a completed local authentication, so the PIN delay starts over. */
    fun clearFailedAttempts() {
        synchronized(this) {
            preferences.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
            publish()
        }
    }

    /** Removes the PIN hash as well as the enabled flag, so turning the lock back on requires a new PIN. */
    fun disable() {
        synchronized(this) {
            preferences.edit()
                .putBoolean(KEY_ENABLED, false)
                .remove(KEY_SALT)
                .remove(KEY_HASH)
                .remove(KEY_ITERATIONS)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
            publish()
        }
    }

    private fun publish() {
        _settings.value = readSettings()
    }

    private fun readSettings(): AppLockSettings {
        val hasPin = hasPinHash()
        return AppLockSettings(
            // The settings screen reads the effective state: enabled without a hash is not a lock.
            enabled = preferences.getBoolean(KEY_ENABLED, false) && hasPin,
            hasPin = hasPin,
            timeout = AppLockTimeout.fromStored(
                preferences.getInt(KEY_TIMEOUT_SECONDS, AppLockTimeout.Immediately.seconds)
            ),
            hideRecents = preferences.getBoolean(KEY_HIDE_RECENTS, false),
            biometricEnabled = preferences.getBoolean(KEY_BIOMETRIC, true),
        )
    }

    private fun hasPinHash(): Boolean = readSalt() != null && readHash()?.isNotEmpty() == true

    private fun readFailedAttempts(): Int = preferences.getInt(KEY_FAILED_ATTEMPTS, 0).coerceAtLeast(0)

    private fun readIterations(): Int = preferences.getInt(
        KEY_ITERATIONS, AppLockPinHasher.DEFAULT_ITERATIONS
    ).coerceIn(AppLockPinHasher.MIN_ITERATIONS, AppLockPinHasher.MAX_ITERATIONS)

    private fun readSalt(): ByteArray? = decode(preferences.getString(KEY_SALT, null))

    private fun readHash(): ByteArray? = decode(preferences.getString(KEY_HASH, null))

    private fun encode(value: ByteArray): String = Base64.getEncoder().encodeToString(value)

    private fun decode(value: String?): ByteArray? {
        if (value.isNullOrBlank()) return null
        return runCatching { Base64.getDecoder().decode(value) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val PREFERENCES = "beygram-app-lock"
        const val KEY_ENABLED = "enabled"
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_ITERATIONS = "pin_iterations"
        const val KEY_TIMEOUT_SECONDS = "timeout_seconds"
        const val KEY_HIDE_RECENTS = "hide_recents"
        const val KEY_BIOMETRIC = "biometric_enabled"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_LOCKOUT_UNTIL = "lockout_until_millis"
    }
}
