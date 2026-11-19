package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.TelegramConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One secondary account as the proxy logic sees it: whether it joins Mihomo, and its phases. */
internal data class TdSecondaryLink(
    val sharesMihomo: Boolean,
    val authorization: TdAccountAuthPhase,
    val connection: TdAccountConnectionPhase
)

/**
 * Connection phase of every open secondary account, fed from the same native updates the account already
 * receives. It only observes: nothing here changes routing, the listener or what the account does.
 *
 * An update from an older generation of an account is ignored, so a late event from a closed client cannot
 * overwrite its replacement. A closed account is removed from [links].
 */
internal object TdSecondaryConnectionStates {
    private val sessions = HashMap<String, TdAccountSessionState>()
    /** Generation of the last closed client per account. Its late updates must not bring the account back. */
    private val closedGenerations = HashMap<String, Long>()
    private val sharedMihomo = HashMap<String, Boolean>()
    private val _links = MutableStateFlow<Map<String, TdSecondaryLink>>(emptyMap())
    val links: StateFlow<Map<String, TdSecondaryLink>> = _links.asStateFlow()

    /** Recorded when a secondary client is opened, from the route the preflight decided. */
    @Synchronized
    fun markRoute(accountId: String, sharesMihomo: Boolean) {
        sharedMihomo[accountId] = sharesMihomo
        publish(accountId, sessions[accountId]?.snapshot?.value)
    }

    @Synchronized
    fun receive(event: TdAccountUpdateRouter.TdAccountRoutedUpdate) {
        if (event.generation <= (closedGenerations[event.accountId] ?: Long.MIN_VALUE)) return
        val current = sessions[event.accountId]
        if (current != null && event.generation < current.generation) return
        val session = if (current != null && current.generation == event.generation) {
            current
        } else {
            TdAccountSessionState(event.accountId, event.generation).also { sessions[event.accountId] = it }
        }
        session.receive(event)
        val snapshot = session.snapshot.value
        if (snapshot.authorization == TdAccountAuthPhase.CLOSED) {
            closedGenerations[event.accountId] = event.generation
            sessions.remove(event.accountId)
            sharedMihomo.remove(event.accountId)
            _links.value = _links.value - event.accountId
        } else {
            publish(event.accountId, snapshot)
        }
    }

    private fun publish(accountId: String, snapshot: TdAccountRuntimeSnapshot?) {
        if (snapshot == null) return
        _links.value = _links.value + (accountId to TdSecondaryLink(
            sharesMihomo = sharedMihomo[accountId] == true,
            authorization = snapshot.authorization,
            connection = snapshot.connection
        ))
    }

    /** Test seam: forgets every account so tests do not share state. */
    @Synchronized
    internal fun resetForTest() {
        sessions.clear()
        closedGenerations.clear()
        sharedMihomo.clear()
        _links.value = emptyMap()
    }
}

/**
 * The legacy account's state, corrected by the secondary accounts that really use the shared Mihomo listener.
 * Only logged-in secondaries that share Mihomo count. Their state can only lower the result: a legacy Ready is
 * not a connection for them. Accounts on another route never change the result.
 */
internal fun tdSharedMihomoConnection(
    legacy: TelegramConnectionState,
    links: Map<String, TdSecondaryLink>
): TelegramConnectionState {
    val pending = links.values.filter {
        it.sharesMihomo &&
            it.authorization == TdAccountAuthPhase.READY &&
            it.connection != TdAccountConnectionPhase.READY
    }
    if (pending.isEmpty()) return legacy
    val derived = if (pending.any { it.connection == TdAccountConnectionPhase.WAIT_NETWORK }) {
        TelegramConnectionState.WaitingForNetwork
    } else {
        TelegramConnectionState.Connecting
    }
    return if (legacy == TelegramConnectionState.Connected) derived else legacy
}

/** Connection state for the proxy logic: the legacy state, corrected by secondaries that share the listener. */
internal fun StateFlow<TelegramConnectionState>.withSharedMihomoLinks(scope: CoroutineScope): StateFlow<TelegramConnectionState> =
    combine(this, TdSecondaryConnectionStates.links) { legacy, links -> tdSharedMihomoConnection(legacy, links) }
        .stateIn(scope, SharingStarted.Eagerly, tdSharedMihomoConnection(value, TdSecondaryConnectionStates.links.value))
