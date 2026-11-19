package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class TdSecondaryConnectionStatesTest {
    @Before fun reset() = TdSecondaryConnectionStates.resetForTest()

    private fun connection(accountId: String, generation: Long, state: TdApi.ConnectionState) =
        TdAccountUpdateRouter.TdAccountRoutedUpdate(accountId, generation, TdApi.UpdateConnectionState(state))

    private fun authorization(accountId: String, generation: Long, state: TdApi.AuthorizationState) =
        TdAccountUpdateRouter.TdAccountRoutedUpdate(accountId, generation, TdApi.UpdateAuthorizationState(state))

    @Test fun phasesAreTrackedPerAccount() {
        TdSecondaryConnectionStates.receive(connection("a", 1, TdApi.ConnectionStateConnecting()))
        TdSecondaryConnectionStates.receive(connection("b", 1, TdApi.ConnectionStateReady()))
        val phases = TdSecondaryConnectionStates.phases.value
        assertEquals(TdAccountConnectionPhase.CONNECTING, phases["a"])
        assertEquals(TdAccountConnectionPhase.READY, phases["b"])
    }

    @Test fun staleGenerationCannotOverwriteNewerPhase() {
        TdSecondaryConnectionStates.receive(connection("a", 2, TdApi.ConnectionStateReady()))
        TdSecondaryConnectionStates.receive(connection("a", 1, TdApi.ConnectionStateConnecting()))
        assertEquals(TdAccountConnectionPhase.READY, TdSecondaryConnectionStates.phases.value["a"])
    }

    @Test fun closedAccountIsRemoved() {
        TdSecondaryConnectionStates.receive(connection("a", 1, TdApi.ConnectionStateReady()))
        TdSecondaryConnectionStates.receive(authorization("a", 1, TdApi.AuthorizationStateClosed()))
        assertFalse(TdSecondaryConnectionStates.phases.value.containsKey("a"))
    }

    @Test fun lateUpdateOfClosedGenerationDoesNotBringAccountBack() {
        TdSecondaryConnectionStates.receive(authorization("a", 1, TdApi.AuthorizationStateClosed()))
        TdSecondaryConnectionStates.receive(connection("a", 1, TdApi.ConnectionStateReady()))
        assertFalse(TdSecondaryConnectionStates.phases.value.containsKey("a"))
    }
}
