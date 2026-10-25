package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-owned progress of a server account deletion. The request and local cleanup run in
 * [com.beykus.beygram.BeyGramApplication.applicationScope], so Activity recreation cannot
 * cancel them. A new screen observes this state instead of starting a second request.
 */
internal data class AccountDeletionProgress(
    val accountId: String? = null,
    val running: Boolean = false,
    /** Telegram confirmed the deletion; the UI may now switch away from this account. */
    val serverDeleted: Boolean = false,
    /** A cleanup/selection failure that must outlive the Activity that started the request. */
    val error: UiText? = null
)

internal object AccountDeletionTracker {
    private val _state = MutableStateFlow(AccountDeletionProgress())
    val state: StateFlow<AccountDeletionProgress> = _state.asStateFlow()

    fun begin(accountId: String): Boolean {
        if (_state.value.running) return false
        _state.value = AccountDeletionProgress(accountId = accountId, running = true)
        return true
    }

    fun finish(accountId: String, serverDeleted: Boolean, error: UiText? = null) {
        _state.value = AccountDeletionProgress(
            accountId = accountId,
            running = false,
            serverDeleted = serverDeleted,
            error = error
        )
    }

    fun clearTerminal() {
        _state.update { it.copy(accountId = null, serverDeleted = false) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
