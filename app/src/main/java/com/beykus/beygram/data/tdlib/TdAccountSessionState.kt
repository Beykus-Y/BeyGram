package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/**
 * Minimal isolated auth/connectivity projection for a future account-scoped repository.
 * Each native TDLib update is tagged at its SOURCE by TdAccountUpdateRouter.
 * Do not populate global/singleton legacy auth, chats or notifications from here.
 */
internal enum class TdAccountAuthPhase {
    STARTING, WAIT_PARAMETERS, WAIT_PHONE, WAIT_CODE, WAIT_PASSWORD, READY, LOGGING_OUT, CLOSED, OTHER
}

internal enum class TdAccountConnectionPhase {
    UNKNOWN, WAIT_NETWORK, CONNECTING, CONNECTING_PROXY, UPDATING, READY, OTHER
}

internal data class TdAccountRuntimeSnapshot(
    val accountId: String,
    val generation: Long,
    val authorization: TdAccountAuthPhase = TdAccountAuthPhase.STARTING,
    val connection: TdAccountConnectionPhase = TdAccountConnectionPhase.UNKNOWN
)

internal class TdAccountSessionState(val accountId: String, val generation: Long) {
    private val _snapshot = MutableStateFlow(TdAccountRuntimeSnapshot(accountId, generation))
    val snapshot: StateFlow<TdAccountRuntimeSnapshot> = _snapshot.asStateFlow()

    /** Fail closed if an update from another client/generation reaches this reducer. */
    fun receive(event: TdAccountUpdateRouter.TdAccountRoutedUpdate) {
        require(event.accountId == accountId && event.generation == generation) {
            "Cross-account or stale TDLib update refused"
        }
        val current = _snapshot.value
        // CLOSED is terminal: even a delayed connection/auth update cannot revive a dead session.
        if (current.authorization == TdAccountAuthPhase.CLOSED) return
        when (val update = event.update) {
            is TdApi.UpdateAuthorizationState -> _snapshot.value = current.copy(
                authorization = when (update.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> TdAccountAuthPhase.WAIT_PARAMETERS
                    is TdApi.AuthorizationStateWaitPhoneNumber -> TdAccountAuthPhase.WAIT_PHONE
                    is TdApi.AuthorizationStateWaitCode -> TdAccountAuthPhase.WAIT_CODE
                    is TdApi.AuthorizationStateWaitPassword -> TdAccountAuthPhase.WAIT_PASSWORD
                    is TdApi.AuthorizationStateReady -> TdAccountAuthPhase.READY
                    is TdApi.AuthorizationStateLoggingOut -> TdAccountAuthPhase.LOGGING_OUT
                    is TdApi.AuthorizationStateClosed -> TdAccountAuthPhase.CLOSED
                    else -> TdAccountAuthPhase.OTHER
                }
            )
            is TdApi.UpdateConnectionState -> _snapshot.value = current.copy(
                connection = when (update.state) {
                    is TdApi.ConnectionStateWaitingForNetwork -> TdAccountConnectionPhase.WAIT_NETWORK
                    is TdApi.ConnectionStateConnecting -> TdAccountConnectionPhase.CONNECTING
                    is TdApi.ConnectionStateConnectingToProxy -> TdAccountConnectionPhase.CONNECTING_PROXY
                    is TdApi.ConnectionStateUpdating -> TdAccountConnectionPhase.UPDATING
                    is TdApi.ConnectionStateReady -> TdAccountConnectionPhase.READY
                    else -> TdAccountConnectionPhase.OTHER
                }
            )
        }
    }
}
