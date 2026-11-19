package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connection phase of every live secondary account, fed from the same native updates the account already
 * receives. It only observes: nothing here changes routing, the listener or what the account does.
 *
 * An update from an older generation of an account is ignored, so a late event from a closed client cannot
 * overwrite the phase of its replacement. A closed account is removed from [phases].
 */
internal object TdSecondaryConnectionStates {
    private val sessions = HashMap<String, TdAccountSessionState>()
    private val _phases = MutableStateFlow<Map<String, TdAccountConnectionPhase>>(emptyMap())
    val phases: StateFlow<Map<String, TdAccountConnectionPhase>> = _phases.asStateFlow()

    @Synchronized
    fun receive(event: TdAccountUpdateRouter.TdAccountRoutedUpdate) {
        val current = sessions[event.accountId]
        if (current != null && event.generation < current.generation) return
        val session = if (current != null && current.generation == event.generation) {
            current
        } else {
            TdAccountSessionState(event.accountId, event.generation).also { sessions[event.accountId] = it }
        }
        session.receive(event)
        val snapshot = session.snapshot.value
        _phases.value = if (snapshot.authorization == TdAccountAuthPhase.CLOSED) {
            sessions.remove(event.accountId)
            _phases.value - event.accountId
        } else {
            _phases.value + (event.accountId to snapshot.connection)
        }
    }

    /** Test seam: forgets every account so tests do not share state. */
    @Synchronized
    internal fun resetForTest() {
        sessions.clear()
        _phases.value = emptyMap()
    }
}
