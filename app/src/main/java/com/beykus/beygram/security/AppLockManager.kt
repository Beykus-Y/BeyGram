package com.beykus.beygram.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
    /** When BeyGram last started a foreign Activity itself. Consumed by the next stop, or cleared on return. */
    private var externalLaunchRequestedAt: Long? = null
    /** The current background period is such a trip, so the timeout does not apply to it on return. */
    private var externalBackground = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onScreenOff()
    }
    /** Without the screen-off receiver a screen-off during a trip cannot be seen, so trips then lock as before. */
    private val screenOffWatched: Boolean = registerScreenOffReceiver(context)

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
                    if (AppLockPolicy.isExternalHandoffReturn(externalBackground, backgroundAge)) {
                        // Back from a picker, camera, permission prompt or share sheet that BeyGram opened itself.
                        session.clearBackground()
                    } else {
                        session.onForeground(now, settings.timeout)
                    }
                }
                backgrounded = false
                activeWhenBackgrounded = false
            }
            externalBackground = false
            externalLaunchRequestedAt = null
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
            // A stop right after BeyGram started a foreign Activity is that trip, not the user leaving.
            val externalTrip = !unlockInProgress && AppLockPolicy.isExternalHandoff(externalLaunchRequestedAt, now)
            externalLaunchRequestedAt = null
            backgrounded = true
            activeWhenBackgrounded = settings.isActive
            backgroundedAtElapsed = now
            externalBackground = externalTrip
            if (unlockInProgress) {
                // Authentication just succeeded. Keep a timestamp: if MainActivity starts
                // immediately this is a hand-over; if the user actually left, the next start
                // must apply the timeout and the immediate setting must lock again.
                session.recordBackground(now)
            } else if (settings.isActive) {
                // A trip is judged on return: a short one keeps the session, a long one follows the timeout.
                if (externalTrip) session.recordBackground(now) else session.onBackground(now, settings.timeout)
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

    /**
     * Called right before BeyGram starts a foreign Activity itself (picker, camera, share sheet) or
     * asks for a runtime permission. The stop that follows is that trip, not the user leaving.
     * Permission requests never reach the Activity overrides in MainActivity, so their call sites
     * call this directly.
     */
    fun expectExternalActivity() {
        synchronized(this) {
            if (screenOffWatched) externalLaunchRequestedAt = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Called from a permission result. A denied or already-answered request may never stop the
     * Activity, so the armed exemption must not carry over to the user's next ordinary departure.
     * After a real trip the stop has already consumed it, so clearing is a no-op then.
     */
    fun clearExpectedExternalActivity() {
        synchronized(this) { externalLaunchRequestedAt = null }
    }

    /**
     * A screen-off is the user putting the phone away, not a return from the trip. It turns a trip
     * into an ordinary background period from this moment, so the timeout applies again.
     */
    private fun onScreenOff() {
        synchronized(this) {
            externalLaunchRequestedAt = null
            if (!externalBackground) return
            externalBackground = false
            val now = SystemClock.elapsedRealtime()
            val settings = store.settings.value
            backgroundedAtElapsed = now
            if (settings.isActive) session.onBackground(now, settings.timeout)
            else session.recordBackground(now)
            _locked.value = session.locked
        }
    }

    private fun registerScreenOffReceiver(context: Context): Boolean = runCatching {
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.applicationContext.registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.applicationContext.registerReceiver(screenOffReceiver, filter)
        }
    }.isSuccess

    companion object {
        @Volatile
        private var instance: AppLockManager? = null

        fun get(context: Context): AppLockManager =
            instance ?: synchronized(this) {
                instance ?: AppLockManager(context.applicationContext).also { instance = it }
            }
    }
}
