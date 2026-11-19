package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.TelegramConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** One secondary account as the proxy logic sees it. [pendingSinceMillis] is set while it is still connecting. */
internal data class TdSecondaryLink(
    val sharesMihomo: Boolean,
    val authorization: TdAccountAuthPhase,
    val connection: TdAccountConnectionPhase,
    val pendingSinceMillis: Long? = null
)

/** Connection phases that mean "not connected yet". UNKNOWN and OTHER do not count: they are not evidence. */
internal val TD_PENDING_CONNECTION_PHASES = setOf(
    TdAccountConnectionPhase.WAIT_NETWORK,
    TdAccountConnectionPhase.CONNECTING,
    TdAccountConnectionPhase.CONNECTING_PROXY,
    TdAccountConnectionPhase.UPDATING
)

/**
 * A secondary that stays pending longer than this stops holding the legacy account's state. Otherwise one stuck
 * account would keep the whole proxy "Connecting" and make auto-selection cycle nodes for no reason.
 */
internal const val SECONDARY_PENDING_LIMIT_MILLIS = 60_000L

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
    private val pendingSince = HashMap<String, Long>()
    private val _links = MutableStateFlow<Map<String, TdSecondaryLink>>(emptyMap())
    val links: StateFlow<Map<String, TdSecondaryLink>> = _links.asStateFlow()

    /** Recorded when a secondary client is opened, from the route the preflight decided. */
    @Synchronized
    fun markRoute(accountId: String, sharesMihomo: Boolean) {
        sharedMihomo[accountId] = sharesMihomo
        publish(accountId, sessions[accountId]?.snapshot?.value, System.currentTimeMillis())
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
            pendingSince.remove(event.accountId)
            _links.value = _links.value - event.accountId
        } else {
            publish(event.accountId, snapshot, System.currentTimeMillis())
        }
    }

    private fun publish(accountId: String, snapshot: TdAccountRuntimeSnapshot?, now: Long) {
        if (snapshot == null) return
        if (snapshot.connection in TD_PENDING_CONNECTION_PHASES) {
            pendingSince.putIfAbsent(accountId, now)
        } else {
            pendingSince.remove(accountId)
        }
        _links.value = _links.value + (accountId to TdSecondaryLink(
            sharesMihomo = sharedMihomo[accountId] == true,
            authorization = snapshot.authorization,
            connection = snapshot.connection,
            pendingSinceMillis = pendingSince[accountId]
        ))
    }

    /** Test seam: forgets every account so tests do not share state. */
    @Synchronized
    internal fun resetForTest() {
        sessions.clear()
        closedGenerations.clear()
        sharedMihomo.clear()
        pendingSince.clear()
        _links.value = emptyMap()
    }
}

/**
 * The legacy account's state, corrected by the logged-in secondaries that really use the shared Mihomo listener.
 * Such a secondary still pending after [SECONDARY_PENDING_LIMIT_MILLIS] no longer counts. Accounts on another route
 * and logged-out accounts never change the result.
 */
internal fun tdSharedMihomoConnection(
    legacy: TelegramConnectionState,
    links: Map<String, TdSecondaryLink>,
    nowMillis: Long
): TelegramConnectionState {
    val pending = links.values.filter {
        it.sharesMihomo &&
            it.authorization == TdAccountAuthPhase.READY &&
            it.connection in TD_PENDING_CONNECTION_PHASES &&
            it.pendingSinceMillis != null &&
            nowMillis - it.pendingSinceMillis < SECONDARY_PENDING_LIMIT_MILLIS
    }
    if (pending.isEmpty()) return legacy
    val derived = if (pending.any { it.connection == TdAccountConnectionPhase.WAIT_NETWORK }) {
        TelegramConnectionState.WaitingForNetwork
    } else {
        TelegramConnectionState.Connecting
    }
    return if (legacy == TelegramConnectionState.Connected) derived else legacy
}

/**
 * Connection state for the proxy logic: the legacy state, corrected by secondaries that share the listener.
 * A timer re-evaluates it, so a secondary whose pending time has run out releases the state without a new update.
 */
internal fun StateFlow<TelegramConnectionState>.withSharedMihomoLinks(scope: CoroutineScope): StateFlow<TelegramConnectionState> {
    val recheck = flow {
        while (true) {
            emit(Unit)
            delay(SHARED_RECHECK_MILLIS)
        }
    }
    return combine(this, TdSecondaryConnectionStates.links, recheck) { legacy, links, _ ->
        tdSharedMihomoConnection(legacy, links, System.currentTimeMillis())
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        tdSharedMihomoConnection(value, TdSecondaryConnectionStates.links.value, System.currentTimeMillis())
    )
}

private const val SHARED_RECHECK_MILLIS = 15_000L
