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
 *
 * MainActivity stays stopped behind AppLockActivity. Stopping a composition does not cancel
 * `rememberCoroutineScope` jobs, so in-flight sends finish and clear their drafts; at the same
 * time a stopped Activity cannot add new dialog windows above the lock screen.
 */
internal class AppLockManager private constructor(context: Context) {
    val store = AppLockStore(context)
    private val session = AppLockSession(store.settings.value.isActive)
    private val _locked = MutableStateFlow(session.locked)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startedActivities = mutableSetOf<Any>()
    @Volatile private var foreground = false
    private var backgrounded = false
    private var activeWhenBackgrounded = false
    private var backgroundedAtElapsed: Long? = null
    /** Set by a successful unlock; the gap before MainActivity starts must not re-lock. */
    private var unlockInProgress = false

    init {
        scope.launch {
            store.settings.collect { current ->
                synchronized(this@AppLockManager) {
                    if (!current.isActive) session.onDisabled()
                    else if (!foreground && !unlockInProgress) session.lockNow()
                    _locked.value = session.locked
                }
            }
        }
    }

    fun onActivityStarted(activity: Any) {
        synchronized(this) {
            val wasForeground = foreground
            val wasUnlocking = unlockInProgress
            val now = SystemClock.elapsedRealtime()
            val backgroundAge = backgroundedAtElapsed?.let { now - it }
            startedActivities += activity
            foreground = true
            if (!wasForeground) {
                val settings = store.settings.value
                if (!settings.isActive) {
                    session.onDisabled()
                    session.clearBackground()
                } else if (backgrounded && AppLockPolicy.isUnlockHandover(wasUnlocking, backgroundAge)) {
                    // LockActivity finished after a successful unlock and MainActivity
                    // started immediately: this is a hand-over, not a new background period.
                    session.clearBackground()
                } else {
                    // The lock may have become active while this process was in the background
                    // (PIN setup finishing after Home). That first return must fail closed.
                    if (AppLockPolicy.lockBecameActiveWhileAway(
                            backgrounded, activeWhenBackgrounded, settings.isActive
                        )
                    ) {
                        session.lockNow()
                    }
                    session.onForeground(now, settings.timeout)
                }
                backgrounded = false
                activeWhenBackgrounded = false
            }
            backgroundedAtElapsed = null
            unlockInProgress = false
            _locked.value = session.locked
        }
    }

    fun onActivityStopped(activity: Any, changingConfigurations: Boolean) {
        if (changingConfigurations) {
            synchronized(this) { startedActivities -= activity }
            return
        }
        synchronized(this) {
            startedActivities -= activity
            if (startedActivities.isNotEmpty()) return
            foreground = false
            val now = SystemClock.elapsedRealtime()
            val settings = store.settings.value
            backgrounded = true
            activeWhenBackgrounded = settings.isActive
            backgroundedAtElapsed = now
            if (unlockInProgress) {
                // Authentication just succeeded. Keep a timestamp: if MainActivity starts
                // immediately this is a hand-over; if the user actually left, the next start
                // must apply the timeout and the immediate setting must lock again.
                session.recordBackground(now)
            } else if (settings.isActive) {
                session.onBackground(now, settings.timeout)
            } else {
                session.onDisabled()
                session.recordBackground(now)
            }
            _locked.value = session.locked
        }
    }

    /**
     * Called by the lock screen only after local authentication. A completion arriving after
     * the Activity was paused is ignored: the next foreground must authenticate again.
     */
    fun unlock() {
        synchronized(this) {
            if (!foreground) return
            session.unlock()
            unlockInProgress = true
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
