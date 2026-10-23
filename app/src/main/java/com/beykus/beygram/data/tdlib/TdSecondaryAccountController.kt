package com.beykus.beygram.data.tdlib

import android.content.Context
import com.beykus.beygram.BeyGramApplication
import com.beykus.beygram.data.vpn.MihomoProxyService
import com.beykus.beygram.data.vpn.ProxyRuntimeStatus
import com.beykus.beygram.domain.model.TelegramRoute
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    init {
        app.applicationScope.launch {
            // Telegram connectivity metrics update this StateFlow frequently.
            // Only listener status transitions require a SOCKS ownership probe;
            // a manual visit to the account screen also forces revalidation.
            MihomoProxyService.runtimeState.map { it.status }.distinctUntilChanged().collect { status ->
                coordinator.reconcileShared { preference ->
                    status == ProxyRuntimeStatus.RUNNING &&
                        preflight.decide(primaryRoute(), preference) == TdSecondaryRouteDecision.SharedMihomo
                }
            }
        }
    }

    /** Also called on entry to the screen, to reconcile after a route policy change. */
    suspend fun checkSharedRoute() {
        coordinator.reconcileShared { preference ->
            preflight.decide(primaryRoute(), preference) == TdSecondaryRouteDecision.SharedMihomo
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
