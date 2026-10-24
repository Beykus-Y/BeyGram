package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.TelegramProxyKind
import com.beykus.beygram.domain.model.TelegramRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class TdSecondaryAccountRoutingTest {
    private val inherit = TdSecondaryRoutePreference.FOLLOW_PRIMARY

    @Test fun inheritingPrimaryDirectIsExplicitlySafeWithoutAListener() {
        assertEquals(
            TdSecondaryRouteDecision.Direct,
            tdSecondaryRouteDecision(TelegramRoute.Direct, inherit, TdSharedListenerEvidence.UNAVAILABLE)
        )
    }

    @Test fun subscriptionWithoutOwnListenerNeverFallsBackToDirect() {
        assertEquals(
            TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.LISTENER_NOT_READY),
            tdSecondaryRouteDecision(TelegramRoute.Subscription, inherit, TdSharedListenerEvidence.UNAVAILABLE)
        )
        assertEquals(
            TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.LISTENER_NOT_OWNED),
            tdSecondaryRouteDecision(TelegramRoute.Subscription, inherit, TdSharedListenerEvidence.FOREIGN)
        )
    }

    @Test fun onlyCredentialVerifiedListenerEnablesSharedMihomo() {
        assertEquals(
            TdSecondaryRouteDecision.SharedMihomo,
            tdSecondaryRouteDecision(TelegramRoute.Subscription, inherit, TdSharedListenerEvidence.VERIFIED_OWN)
        )
        assertEquals(
            TdSecondaryRouteDecision.SharedMihomo,
            tdSecondaryRouteDecision(TelegramRoute.Direct, TdSecondaryRoutePreference.SHARED_MIHOMO,
                TdSharedListenerEvidence.VERIFIED_OWN)
        )
        assertEquals(
            TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.LISTENER_NOT_OWNED),
            tdSecondaryRouteDecision(TelegramRoute.Direct, TdSecondaryRoutePreference.SHARED_MIHOMO,
                TdSharedListenerEvidence.FOREIGN)
        )
    }

    @Test fun unknownOrManualPrimaryRouteRequiresExplicitSelection() {
        assertEquals(
            TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.PRIMARY_ROUTE_UNKNOWN),
            tdSecondaryRouteDecision(null, inherit, TdSharedListenerEvidence.VERIFIED_OWN)
        )
        assertEquals(
            TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.MANUAL_PROXY_NEEDS_ACCOUNT_CONFIGURATION),
            tdSecondaryRouteDecision(TelegramRoute.Proxy(TelegramProxyKind.SOCKS5, "proxy.invalid", 1080),
                inherit, TdSharedListenerEvidence.VERIFIED_OWN)
        )
    }

    @Test fun explicitDirectIsNotAnImplicitFallbackFromAFailedProxy() {
        for (route in listOf<TelegramRoute?>(null, TelegramRoute.Subscription, TelegramRoute.Direct)) {
            for (evidence in TdSharedListenerEvidence.entries) {
                assertEquals(TdSecondaryRouteDecision.Direct, tdSecondaryRouteDecision(
                    route, TdSecondaryRoutePreference.EXPLICIT_DIRECT, evidence
                ))
            }
        }
    }

    @Test fun doesNotProbeSocketForDirectUnknownOrManualRoute() = runBlocking {
        var probes = 0
        val gate = TdSecondaryAccountRoutePreflight(
            listenerRunning = { true },
            verifyOwnListener = { probes++; throw IllegalStateException("must not probe") }
        )
        assertEquals(TdSecondaryRouteDecision.Direct,
            gate.decide(TelegramRoute.Direct, inherit))
        assertEquals(
            TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.PRIMARY_ROUTE_UNKNOWN),
            gate.decide(null, inherit)
        )
        assertEquals(
            TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.MANUAL_PROXY_NEEDS_ACCOUNT_CONFIGURATION),
            gate.decide(TelegramRoute.Proxy(TelegramProxyKind.SOCKS5, "proxy.invalid", 1080), inherit)
        )
        assertEquals(0, probes)
    }

    @Test fun unavailableOrMisownedListenerBlocksSecondarySession() = runBlocking {
        var probes = 0
        val stopped = TdSecondaryAccountRoutePreflight(
            listenerRunning = { false },
            verifyOwnListener = { probes++; TdSharedListenerEvidence.VERIFIED_OWN }
        )
        assertEquals(
            TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.LISTENER_NOT_READY),
            stopped.decide(TelegramRoute.Subscription, inherit)
        )
        assertEquals(0, probes)

        val foreign = TdSecondaryAccountRoutePreflight(
            listenerRunning = { true },
            verifyOwnListener = { TdSharedListenerEvidence.FOREIGN }
        )
        assertEquals(
            TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.LISTENER_NOT_OWNED),
            foreign.decide(TelegramRoute.Subscription, inherit)
        )
    }

    @Test fun probingFailureDoesNotSilentlyUseDirect() = runBlocking {
        val gate = TdSecondaryAccountRoutePreflight(
            listenerRunning = { true },
            verifyOwnListener = { throw java.io.IOException("port unavailable") }
        )
        assertEquals(
            TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.LISTENER_NOT_READY),
            gate.decide(TelegramRoute.Subscription, inherit)
        )
    }

    @Test fun oneVerifiedProcessListenerCanBeSharedWithoutStartingAnother() = runBlocking {
        var checks = 0
        val gate = TdSecondaryAccountRoutePreflight(
            listenerRunning = { true },
            verifyOwnListener = { checks++; TdSharedListenerEvidence.VERIFIED_OWN }
        )
        repeat(3) {
            assertEquals(TdSecondaryRouteDecision.SharedMihomo,
                gate.decide(TelegramRoute.Subscription, inherit))
        }
        assertEquals(3, checks)
    }
}
