package com.beykus.beygram.security

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Process-wide app lock. One instance is shared by every Activity and every Telegram account,
 * so switching accounts never resets or bypasses the lock.
 */
internal class AppLockManager private constructor(context: Context) {
    val store = AppLockStore(context)
    private val session = AppLockSession(store.settings.value.isActive)
    private val _locked = MutableStateFlow(session.locked)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Foreground state is tracked even while the lock is disabled, for enable-while-background. */
    @Volatile private var foreground = false
    /** Whether the Activity was genuinely stopped, and whether the lock was active at that moment. */
    private var backgrounded = false
    private var activeWhenBackgrounded = false

    init {
        // Settings can change while the process lives. Disabling must unlock immediately;
        // if the new PIN finishes while the app is already in the background, fail closed.
        scope.launch {
            store.settings.collect { current ->
                synchronized(this@AppLockManager) {
                    if (!current.isActive) session.onDisabled()
                    else if (!foreground) session.lockNow()
                    _locked.value = session.locked
                }
            }
        }
    }

    fun onActivityStarted() {
        synchronized(this) {
            foreground = true
            val settings = store.settings.value
            if (!settings.isActive) {
                session.onDisabled()
                session.clearBackground()
            } else {
                // Disabled when the app went away, active now: PIN setup finished in the
                // background, so this first return must authenticate even with a long timeout.
                if (AppLockPolicy.lockBecameActiveWhileAway(backgrounded, activeWhenBackgrounded, settings.isActive)) {
                    session.lockNow()
                }
                session.onForeground(SystemClock.elapsedRealtime(), settings.timeout)
            }
            backgrounded = false
            activeWhenBackgrounded = false
            _locked.value = session.locked
        }
    }

    /**
     * Rotation and other configuration changes must not start the background timer.
     * A genuine stop records when the app left the foreground; "immediately" locks right away.
     */
    fun onActivityStopped(changingConfigurations: Boolean) {
        if (changingConfigurations) return
        synchronized(this) {
            foreground = false
            backgrounded = true
            val settings = store.settings.value
            activeWhenBackgrounded = settings.isActive
            if (settings.isActive) {
                session.onBackground(SystemClock.elapsedRealtime(), settings.timeout)
            } else {
                // The lock may become active after a slow PIN setup finishes in the background.
                session.onDisabled()
                session.recordBackground(SystemClock.elapsedRealtime())
            }
            _locked.value = session.locked
        }
    }

    fun unlock() {
        synchronized(this) {
            session.unlock()
            _locked.value = false
        }
    }

    companion object {
        @Volatile
        private var instance: AppLockManager? = null

        fun get(context: Context): AppLockManager =
            instance ?: synchronized(this) {
                instance ?: AppLockManager(context.applicationContext).also { instance = it }
            }
    }
}
