package com.beykus.beygram.data.tdlib

import android.content.Context
import com.beykus.beygram.data.vpn.LoopbackProxyCredentials
import com.beykus.beygram.data.vpn.LoopbackSocksProbe
import com.beykus.beygram.data.vpn.MihomoProxyClient
import com.beykus.beygram.domain.model.TelegramRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One Mihomo listener belongs to the APPLICATION PROCESS, not to every account.
 * This is a policy/preflight for future secondary sessions, never a command to
 * start, stop, or reconfigure the currently authorized legacy TDLib session.
 */
internal enum class TdSecondaryRoutePreference {
    /** On a missing per-account setting, use the primary account's policy. */
    FOLLOW_PRIMARY,
    /** Explicit per-account direct mode: never a silent fallback from a failed proxy. */
    EXPLICIT_DIRECT,
    /** Explicit per-account choice to join BeyGram's authenticated local SOCKS. */
    SHARED_MIHOMO
}

internal enum class TdSharedListenerEvidence { VERIFIED_OWN, UNAVAILABLE, FOREIGN }

internal enum class TdSecondaryRouteBlock {
    PRIMARY_ROUTE_UNKNOWN,
    MANUAL_PROXY_NEEDS_ACCOUNT_CONFIGURATION,
    LISTENER_NOT_READY,
    LISTENER_NOT_OWNED
}

internal sealed interface TdSecondaryRouteDecision {
    data object Direct : TdSecondaryRouteDecision
    /** Credentials come from the installation-local store at activation time, never from this object. */
    data object SharedMihomo : TdSecondaryRouteDecision
    data class Blocked(val reason: TdSecondaryRouteBlock) : TdSecondaryRouteDecision
}

/**
 * No unknown or failed local proxy state is silently converted to direct TDLib.
 * For a primary manual proxy, the secondary account needs its own explicit
 * configuration: copying only a host/port would lose its secrets and privacy rules.
 */
internal fun tdSecondaryRouteDecision(
    primary: TelegramRoute?,
    preference: TdSecondaryRoutePreference,
    listener: TdSharedListenerEvidence
): TdSecondaryRouteDecision {
    val effective = when (preference) {
        TdSecondaryRoutePreference.EXPLICIT_DIRECT -> return TdSecondaryRouteDecision.Direct
        TdSecondaryRoutePreference.SHARED_MIHOMO -> TelegramRoute.Subscription
        TdSecondaryRoutePreference.FOLLOW_PRIMARY -> primary
            ?: return TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.PRIMARY_ROUTE_UNKNOWN)
    }
    return when (effective) {
        TelegramRoute.Direct -> TdSecondaryRouteDecision.Direct
        is TelegramRoute.Proxy -> TdSecondaryRouteDecision.Blocked(
            TdSecondaryRouteBlock.MANUAL_PROXY_NEEDS_ACCOUNT_CONFIGURATION
        )
        TelegramRoute.Subscription -> when (listener) {
            TdSharedListenerEvidence.VERIFIED_OWN -> TdSecondaryRouteDecision.SharedMihomo
            TdSharedListenerEvidence.FOREIGN ->
                TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.LISTENER_NOT_OWNED)
            TdSharedListenerEvidence.UNAVAILABLE ->
                TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.LISTENER_NOT_READY)
        }
    }
}

/**
 * Makes the proxy evidence real: an "open port" or service RUNNING alone is
 * insufficient. A different BeyGram variant or another app can own that port.
 * Use only from a coroutine/background thread; it performs a short socket probe.
 *
 * Safe order for a future account manager: query the primary TDLib route,
 * evaluate this preflight, verify credentials when sharing, THEN create/configure
 * the secondary client. The owner must recheck when the shared listener restarts.
 */
internal class TdSecondaryAccountRoutePreflight(
    private val listenerRunning: () -> Boolean,
    private val verifyOwnListener: () -> TdSharedListenerEvidence
) {
    suspend fun decide(
        primaryRoute: TelegramRoute?,
        preference: TdSecondaryRoutePreference
    ): TdSecondaryRouteDecision = withContext(Dispatchers.IO) {
        if (preference == TdSecondaryRoutePreference.EXPLICIT_DIRECT ||
            (preference == TdSecondaryRoutePreference.FOLLOW_PRIMARY && primaryRoute == TelegramRoute.Direct) ||
            (preference == TdSecondaryRoutePreference.FOLLOW_PRIMARY && primaryRoute is TelegramRoute.Proxy) ||
            (preference == TdSecondaryRoutePreference.FOLLOW_PRIMARY && primaryRoute == null)
        ) return@withContext tdSecondaryRouteDecision(primaryRoute, preference, TdSharedListenerEvidence.UNAVAILABLE)

        val evidence = if (!listenerRunning()) TdSharedListenerEvidence.UNAVAILABLE
            else try { verifyOwnListener() } catch (_: Exception) { TdSharedListenerEvidence.UNAVAILABLE }
        tdSecondaryRouteDecision(primaryRoute, preference, evidence)
    }

    companion object {
        /** Uses the one existing Mihomo port and installation credentials; creates no listener. */
        fun forApplication(context: Context): TdSecondaryAccountRoutePreflight {
            val appContext = context.applicationContext
            return TdSecondaryAccountRoutePreflight(
                listenerRunning = { MihomoProxyClient.isRunning() },
                verifyOwnListener = {
                    val credentials = LoopbackProxyCredentials.get(appContext)
                    when (LoopbackSocksProbe.check(
                        MihomoProxyClient.HOST, MihomoProxyClient.PORT,
                        credentials.username, credentials.password, 350, 500
                    )) {
                        LoopbackSocksProbe.Result.OWN_LISTENER -> TdSharedListenerEvidence.VERIFIED_OWN
                        LoopbackSocksProbe.Result.FOREIGN_LISTENER -> TdSharedListenerEvidence.FOREIGN
                    }
                }
            )
        }
    }
}
