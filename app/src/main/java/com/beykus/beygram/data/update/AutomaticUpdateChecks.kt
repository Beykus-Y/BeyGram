package com.beykus.beygram.data.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LOG_TAG = "BeyGramUpdates"

/** Minimum gap between two resume-triggered requests, so a rotation is not a request. */
private const val RESUME_DEBOUNCE_MS = 60L * 1000

/** Jitter window added to every retry, so many installs do not wake up at the same second. */
private const val JITTER_SPAN_MS = 5L * 60 * 1000

/** What asked for an automatic check. The interval differs, the request does not. */
enum class UpdateCheckTrigger { Launch, Resume, Periodic }

/** The one request an automatic check may make. [UpdateCoordinator] is the only production implementation. */
fun interface AutomaticCheckSource {
    suspend fun checkAutomatically(): AutomaticCheckOutcome
}

/** Persisted bookkeeping of unattended checks. [UpdateNoticeStore] is the production implementation. */
internal interface UpdateNoticeStorage {
    fun attempts(): UpdateAutoCheckPolicy.Attempts
    fun recordAttempt(now: Long)

    /** Forgets an attempt that was skipped before it reached the network. */
    fun undoAttempt(previousLastAttemptAt: Long?)
    fun recordSuccess(now: Long)
    fun recordFailure(now: Long, rateLimited: Boolean)
    fun notifiedVersionCode(): Long?
    fun saveNotifiedVersionCode(code: Long?)
    fun snoozedVersionCode(): Long?
    fun snoozedUntil(): Long?
    fun snoozeBanner(code: Long, until: Long)
    fun clearSnooze()
    /** The user's switch for unattended checks. Manual checks never depend on it. */
    fun autoCheckEnabled(): Boolean
    fun setAutoCheckEnabled(enabled: Boolean)
}

/**
 * The one scheduler of unattended update checks for this process.
 *
 * It owns no update state: every request goes through [AutomaticCheckSource], which in production is the shared
 * [UpdateCoordinator] with its single in-flight slot, stored release and manual actions. This class only decides
 * *when* to ask, remembers how previous attempts went, and reflects a validated offer in the system notification.
 *
 * The listener is process-wide and installed from [com.beykus.beygram.BeyGramApplication]; screens call
 * [onForeground] when they become visible again. A periodic ticker runs while the process lives, which on Android
 * usually means while the app is in use. Background execution is best effort: Doze, force-stop and manufacturer
 * restrictions may skip ticks, and nothing here tries to defeat them.
 */
class AutomaticUpdateChecks internal constructor(
    private val source: AutomaticCheckSource,
    private val storage: UpdateNoticeStorage,
    private val scope: CoroutineScope,
    private val announce: (StableRelease, Long) -> Unit,
    private val withdraw: () -> Unit,
    private val statuses: Flow<UpdateStatus>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = { Log.i(LOG_TAG, it) },
) {
    private var ticker: Job? = null
    private var notificationWatcher: Job? = null
    @Volatile private var lastForegroundRequest = 0L
    @Volatile private var running = false

    /** Called on the first composition after a launch. A cold start may check even when the last attempt is recent. */
    fun onLaunch() {
        start()
        request(UpdateCheckTrigger.Launch)
    }

    /** Called when the app returns to the foreground; the gate keeps this from becoming a request per rotation. */
    fun onForeground() {
        request(UpdateCheckTrigger.Resume)
    }

    /**
     * The user's switch. Turning it off stops every unattended trigger at the gate; an already visible offer and its
     * alert stay, because the user asked to stop checking, not to hide what was already found.
     */
    fun setAutoCheckEnabled(enabled: Boolean) {
        storage.setAutoCheckEnabled(enabled)
    }

    fun isAutoCheckEnabled(): Boolean = storage.autoCheckEnabled()

    /** Starts the periodic ticker and the status observer once per process. Repeated calls are cheap and safe. */
    fun start() {
        if (ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive) {
                    delay(UpdateAutoCheckPolicy.FOREGROUND_INTERVAL_MS)
                    request(UpdateCheckTrigger.Periodic)
                }
            }
        }
        if (notificationWatcher?.isActive != true) {
            notificationWatcher = scope.launch {
                statuses.collect { status ->
                    when (status) {
                        // The offer is gone, so its alert must go with it. Other states keep the tray untouched:
                        // a restart invents no state, and an alert posted by an earlier process is still valid.
                        is UpdateStatus.Installed, UpdateStatus.Current -> withdraw()
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun request(trigger: UpdateCheckTrigger) {
        if (running) return
        running = true
        scope.launch {
            try {
                gateAndAttempt(trigger)
            } finally {
                running = false
            }
        }
    }

    private suspend fun gateAndAttempt(trigger: UpdateCheckTrigger) {
        // The user can switch unattended checks off; a manual check on the Updates screen stays available.
        if (!storage.autoCheckEnabled()) return
        val now = clock()
        if (trigger == UpdateCheckTrigger.Resume) {
            if (lastForegroundRequest != 0L && now - lastForegroundRequest < RESUME_DEBOUNCE_MS) return
            lastForegroundRequest = now
        }
        val interval = when (trigger) {
            UpdateCheckTrigger.Launch -> UpdateAutoCheckPolicy.LAUNCH_INTERVAL_MS
            UpdateCheckTrigger.Resume -> UpdateAutoCheckPolicy.RESUME_INTERVAL_MS
            UpdateCheckTrigger.Periodic -> UpdateAutoCheckPolicy.FOREGROUND_INTERVAL_MS
        }
        val attempts = storage.attempts()
        val jitter = UpdateAutoCheckPolicy.jitterMs(JITTER_SPAN_MS, attempts.lastAttemptAt ?: now)
        if (!UpdateAutoCheckPolicy.isDue(attempts, now, interval, jitter)) return
        attempt(now)
    }

    /**
     * One attempt. Testable without Android: the source is a function, the storage an interface, the clock injected.
     * Returns the outcome for the tests; production callers only use [onLaunch]/[onForeground].
     */
    internal suspend fun attempt(now: Long = clock()): AutomaticCheckOutcome {
        // Recorded before the request leaves, so a crash inside the call cannot become a retry loop.
        val previousAttemptAt = storage.attempts().lastAttemptAt
        storage.recordAttempt(now)
        val outcome = source.checkAutomatically()
        when (outcome) {
            is AutomaticCheckOutcome.Available -> {
                val at = clock()
                storage.recordSuccess(at)
                announce(outcome.release, at)
            }
            AutomaticCheckOutcome.Current -> {
                storage.recordSuccess(clock())
                withdraw()
            }
            // A user was working with the updater. Nothing was asked, so the attempt is rolled back: it must not
            // push the next resume/periodic trigger out by a full interval.
            AutomaticCheckOutcome.Skipped -> storage.undoAttempt(previousAttemptAt)
            is AutomaticCheckOutcome.Failed -> {
                storage.recordFailure(clock(), rateLimited = outcome.reason == StableUpdateReason.RateLimited)
                log("Automatic update check failed: ${outcome.reason ?: "unclassified"}")
            }
        }
        return outcome
    }

    companion object {
        @Volatile
        private var instance: AutomaticUpdateChecks? = null

        fun get(context: Context, scope: CoroutineScope): AutomaticUpdateChecks =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext, scope).also { instance = it }
            }

        private fun create(context: Context, scope: CoroutineScope): AutomaticUpdateChecks {
            val coordinator = UpdateCoordinator.get(context)
            val store = UpdateNoticeStore(context)
            return AutomaticUpdateChecks(
                source = AutomaticCheckSource { coordinator.checkAutomatically() },
                storage = store,
                scope = scope,
                announce = { release, now -> UpdateNotifications.offer(context, release, store, now) },
                withdraw = { UpdateNotifications.clear(context, store) },
                statuses = coordinator.state.map { it.status },
            )
        }
    }
}

/**
 * The in-app banner state, derived from the same [UpdateUiState] and the same snooze record as the notification.
 */
data class UpdateBannerState(
    val versionName: String,
    val versionCode: Long,
    val notes: String,
    val stale: Boolean,
)

/**
 * Whether the chats screen should show "Доступно обновление", and for which build.
 *
 * [snoozedCode] is the build the user hid with "Позже"; a newer build is shown again immediately, and [snoozedUntil]
 * only limits how long that hidden build stays hidden.
 */
internal fun updateBannerState(
    state: UpdateUiState,
    installedCode: Long,
    now: Long,
    snoozedCode: Long?,
    snoozedUntil: Long?,
): UpdateBannerState? {
    // A download, install or a verified APK already owns the screen; the banner must not compete with it.
    if (state.status is UpdateStatus.Downloading || state.status is UpdateStatus.Verifying ||
        state.status is UpdateStatus.Ready || state.status is UpdateStatus.PreparingInstall ||
        state.status is UpdateStatus.PermissionRequired || state.status is UpdateStatus.AwaitingInstaller ||
        state.status is UpdateStatus.Installed
    ) {
        return null
    }
    val release = when (val status = state.status) {
        is UpdateStatus.Available -> status.release
        // A check in flight, an idle process or a failed comment may keep the last confirmed offer, marked stale.
        UpdateStatus.Checking, UpdateStatus.Idle, is UpdateStatus.Failure -> state.knownRelease
        else -> null
    } ?: return null
    if (release.versionCode <= installedCode) return null
    if (snoozedCode == release.versionCode && snoozedUntil != null && now < snoozedUntil) return null
    return UpdateBannerState(
        versionName = release.versionName,
        versionCode = release.versionCode,
        notes = release.notes,
        stale = state.checkStale,
    )
}