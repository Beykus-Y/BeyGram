package com.beykus.beygram.data.tdlib

import android.content.Context
import com.beykus.beygram.BeyGramApplication
import com.beykus.beygram.data.vpn.MihomoProxyService
import com.beykus.beygram.domain.model.TelegramRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-owned and intentionally lazy. Opening Settings does NOT open a
 * second TDLib client; only an explicit user action may do so.
 *
 * Do not put this into an Activity-scoped remember/state holder: losing the
 * handle on screen navigation would orphan a live TDLib client and its lease.
 */
internal class TdSecondaryAccountController private constructor(
    context: Context,
    private val primaryRoute: suspend () -> TelegramRoute?
) {
    private val app = context.applicationContext as BeyGramApplication
    private val preflight = TdSecondaryAccountRoutePreflight.forApplication(app)
    private val launcher = TdSecondaryNativeLoginLauncher.forApplication(app, primaryRoute)
    val coordinator = TdSecondarySessionCoordinator(
        registry = TdAccountRegistry(app.filesDir),
        scope = app.applicationScope,
        openNative = { accountId, preference ->
            TdNativeSecondaryAuthSession(launcher.openRegisteredSecondary(accountId, preference))
        }
    )

    private val reconcileMutex = Mutex()

    init {
        app.applicationScope.launch {
            // React immediately to every listener status transition, including
            // STARTING/STOPPED. Initial DIRECT and shared SOCKS both need to be
            // checked against the current primary account route.
            MihomoProxyService.runtimeState.map { it.status }.distinctUntilChanged().collect {
                checkRoutesNow()
            }
        }
        app.applicationScope.launch {
            // The legacy TDLib repository doesn't currently expose a StateFlow
            // for manual proxy changes. Poll ONLY while a secondary is active:
            // a FOLLOW_PRIMARY client originally DIRECT must not keep sending
            // direct traffic indefinitely after the primary route changes.
            coordinator.snapshot
                .map { state -> state.accounts.any { it.connectedInThisProcess } }
                .distinctUntilChanged()
                .collectLatest { active ->
                    while (active) {
                        delay(15_000)
                        checkRoutesNow()
                    }
                }
        }
    }

    /** Also called when entering the account screen; serializes all route probes. */
    suspend fun checkRoutesNow() = reconcileMutex.withLock {
        coordinator.reconcileRoutes { preference ->
            preflight.decide(primaryRoute(), preference)
        }
    }

    companion object {
        @Volatile private var instance: TdSecondaryAccountController? = null

        fun get(context: Context, primaryRoute: suspend () -> TelegramRoute?): TdSecondaryAccountController =
            instance ?: synchronized(this) {
                instance ?: TdSecondaryAccountController(context.applicationContext, primaryRoute)
                    .also { instance = it }
            }
    }
}
