package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

class TdAccountSessionStateTest {
    @Test fun identicalTelegramEventsUpdateOnlyTheirOwnAccounts() {
        val temp = Files.createTempDirectory("beygram-account-state-").toFile()
        try {
            val registry = TdAccountRegistry(temp)
            val (_, secondId) = registry.prepareSecondaryAccount()
            val router = TdAccountUpdateRouter(registry)
            var a: TdAccountSessionState? = null
            var b: TdAccountSessionState? = null
            val updatesA = mutableListOf<TdAccountUpdateRouter.TdAccountRoutedUpdate>()
            val updatesB = mutableListOf<TdAccountUpdateRouter.TdAccountRoutedUpdate>()
            val regA = router.register("legacy") { event -> updatesA.add(event); a!!.receive(event) }
            val regB = router.register(secondId) { event -> updatesB.add(event); b!!.receive(event) }
            a = TdAccountSessionState(regA.accountId, regA.generation)
            b = TdAccountSessionState(regB.accountId, regB.generation)
            regA.updateHandler.onResult(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
            regB.updateHandler.onResult(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))
            assertEquals(TdAccountAuthPhase.READY, a.snapshot.value.authorization)
            assertEquals(TdAccountAuthPhase.WAIT_PHONE, b.snapshot.value.authorization)
            regA.updateHandler.onResult(TdApi.UpdateConnectionState(TdApi.ConnectionStateReady()))
            assertEquals(TdAccountConnectionPhase.READY, a.snapshot.value.connection)
            assertEquals(TdAccountConnectionPhase.UNKNOWN, b.snapshot.value.connection)
            assertEquals("legacy", updatesA.single { it.update is TdApi.UpdateConnectionState }.accountId)
            assertEquals(secondId, updatesB.single().accountId)
            regA.onNativeClosed()
            regB.onNativeClosed()
        } finally { temp.deleteRecursively() }
    }

    @Test fun stateRejectsWrongAccountAndGenerationEvenWithSameMessageIdentifiers() {
        val state = TdAccountSessionState("legacy", 12)
        for (bad in listOf(
            TdAccountUpdateRouter.TdAccountRoutedUpdate("a-another", 12, TdApi.Ok()),
            TdAccountUpdateRouter.TdAccountRoutedUpdate("legacy", 11, TdApi.Ok())
        )) {
            try {
                state.receive(bad)
                fail("Cross-account event was admitted")
            } catch (_: IllegalArgumentException) { }
        }
        assertEquals(TdAccountAuthPhase.STARTING, state.snapshot.value.authorization)
    }

    @Test fun terminalStateCannotBeRevivedByLateReadyAndNetworkEvents() {
        val state = TdAccountSessionState("legacy", 7)
        fun update(value: TdApi.Object) {
            state.receive(TdAccountUpdateRouter.TdAccountRoutedUpdate("legacy", 7, value))
        }
        update(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
        update(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
        update(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
        update(TdApi.UpdateConnectionState(TdApi.ConnectionStateReady()))
        assertEquals(TdAccountAuthPhase.CLOSED, state.snapshot.value.authorization)
        assertEquals(TdAccountConnectionPhase.UNKNOWN, state.snapshot.value.connection)
    }
}
