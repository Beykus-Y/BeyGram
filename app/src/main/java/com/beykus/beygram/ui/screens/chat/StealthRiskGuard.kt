package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.BeyGramDialog

/** A risky action waits for the user's confirmation while hidden presence warns about them and this screen has not confirmed one. */
internal fun stealthRiskNeedsConfirmation(warns: Boolean, confirmed: Boolean): Boolean = warns && !confirmed

/**
 * Asks before a reaction, a poll vote or a bot button press while online status is hidden and warnings are on. Telegram can show
 * the account online for a short while after any of them, and stealth scheduling does not cover them. Asked once per screen: after
 * the user confirms, the risky actions that follow on that screen run without asking until the screen is opened again.
 */
internal class StealthRiskGuard(private val warns: () -> Boolean) {
    private var confirmed by mutableStateOf(false)
    private var pending by mutableStateOf<(() -> Unit)?>(null)
    private var pendingCancel: () -> Unit = {}

    /** Runs [action] at once, or after the user confirms the question. [onCancel] undoes what the caller staged for it. */
    fun run(onCancel: () -> Unit = {}, action: () -> Unit) {
        if (stealthRiskNeedsConfirmation(warns(), confirmed)) {
            pending = action
            pendingCancel = onCancel
        } else {
            action()
        }
    }

    private fun cancel() {
        pending = null
        pendingCancel().also { pendingCancel = {} }
    }

    @Composable
    fun Dialog() {
        val action = pending ?: return
        BeyGramDialog(
            onDismissRequest = ::cancel,
            title = stringResource(R.string.stealth_risky_warning_title),
            message = stringResource(R.string.stealth_risky_warning_text),
            confirmText = stringResource(R.string.stealth_risky_warning_confirm),
            onConfirm = {
                pending = null
                pendingCancel = {}
                confirmed = true
                action()
            },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = ::cancel
        )
    }
}

/**
 * One guard per [key] (a chat, a thread or a story poster). [warns] is read at each risky action, so the guard always
 * follows the current stealth settings instead of the ones present when it was created.
 */
@Composable
internal fun rememberStealthRiskGuard(key: Any, warns: () -> Boolean): StealthRiskGuard {
    val latestWarns by rememberUpdatedState(warns)
    return remember(key) { StealthRiskGuard { latestWarns() } }
}
