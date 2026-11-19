package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.TelegramConnectionState
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
        val links = TdSecondaryConnectionStates.links.value
        assertEquals(TdAccountConnectionPhase.CONNECTING, links["a"]?.connection)
        assertEquals(TdAccountConnectionPhase.READY, links["b"]?.connection)
    }

    @Test fun staleGenerationCannotOverwriteNewerPhase() {
        TdSecondaryConnectionStates.receive(connection("a", 2, TdApi.ConnectionStateReady()))
        TdSecondaryConnectionStates.receive(connection("a", 1, TdApi.ConnectionStateConnecting()))
        assertEquals(TdAccountConnectionPhase.READY, TdSecondaryConnectionStates.links.value["a"]?.connection)
    }

    @Test fun closedAccountIsRemoved() {
        TdSecondaryConnectionStates.receive(connection("a", 1, TdApi.ConnectionStateReady()))
        TdSecondaryConnectionStates.receive(authorization("a", 1, TdApi.AuthorizationStateClosed()))
        assertFalse(TdSecondaryConnectionStates.links.value.containsKey("a"))
    }

    @Test fun legacyReadyIsNotConnectedWhileSharedSecondaryConnects() {
        val links = mapOf("a" to TdSecondaryLink(true, TdAccountAuthPhase.READY, TdAccountConnectionPhase.CONNECTING))
        assertEquals(TelegramConnectionState.Connecting, tdSharedMihomoConnection(TelegramConnectionState.Connected, links))
    }

    @Test fun waitingForNetworkOfSharedSecondaryIsReported() {
        val links = mapOf("a" to TdSecondaryLink(true, TdAccountAuthPhase.READY, TdAccountConnectionPhase.WAIT_NETWORK))
        assertEquals(TelegramConnectionState.WaitingForNetwork, tdSharedMihomoConnection(TelegramConnectionState.Connected, links))
    }

    @Test fun secondaryOnAnotherRouteDoesNotChangeLegacyState() {
        val links = mapOf("a" to TdSecondaryLink(false, TdAccountAuthPhase.READY, TdAccountConnectionPhase.CONNECTING))
        assertEquals(TelegramConnectionState.Connected, tdSharedMihomoConnection(TelegramConnectionState.Connected, links))
    }

    @Test fun loggedOutSharedSecondaryDoesNotBlockLegacyState() {
        val links = mapOf("a" to TdSecondaryLink(true, TdAccountAuthPhase.WAIT_PHONE, TdAccountConnectionPhase.UNKNOWN))
        assertEquals(TelegramConnectionState.Connected, tdSharedMihomoConnection(TelegramConnectionState.Connected, links))
    }

    @Test fun legacyConnectingIsKeptWhenSharedSecondaryConnects() {
        val links = mapOf("a" to TdSecondaryLink(true, TdAccountAuthPhase.READY, TdAccountConnectionPhase.CONNECTING))
        assertEquals(TelegramConnectionState.Connecting, tdSharedMihomoConnection(TelegramConnectionState.Connecting, links))
    }

    @Test fun lateUpdateOfClosedGenerationDoesNotBringAccountBack() {
        TdSecondaryConnectionStates.receive(authorization("a", 1, TdApi.AuthorizationStateClosed()))
        TdSecondaryConnectionStates.receive(connection("a", 1, TdApi.ConnectionStateReady()))
        assertFalse(TdSecondaryConnectionStates.phases.value.containsKey("a"))
    }
}
